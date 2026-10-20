package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.ExpressionTypes.type
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.ast.parser.position
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.std.lib.ArrayLib
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigInteger

class StatementParser(
    val parser: Stage1Parser,
    val ast: ASTFile
) {
    val exprParser = parser.expressionParser
    fun parseStatement(ctx: ScratcherLangParser.StatementContext): Statement {
        return when (val child = ctx.getChild(0)) {
            is ScratcherLangParser.VarDeclContext -> {
                val name = child.IDENTIFIER().text
                val type = child.type()?.let {
                    figureOutType(this.parser.reporter, parser.ctx, ast, it, localTypeBindings = parser.currentTypeBindings)
                }
                val value = exprParser.parseExpression(child.expression(), type)
                val resolvedType = type ?: ExpressionTypes.getExpressionType(value)

                if (resolvedType == PrimitiveType.Null) {
                    return error("Expected any type, found null", ctx.position!!)
                }
                val variable = LocalVariable(name, resolvedType)
                if (variable.type == PrimitiveType.Void || variable.type == PrimitiveType.StaticList) {
                    return error("Variable has type void or static list", ctx.position!!)
                }
                if (parser.localVariables.find { it.name == variable.name } != null) {
                    return error("Variable already exists!", child.IDENTIFIER().position())
                }
                parser.localVariables.add(variable)
                VariableStatement(value, variable, child.position)
            }
            is ScratcherLangParser.ExprStmtContext -> ExpressionStatement(exprParser.parseExpression(child.expression()), child.position)
            is ScratcherLangParser.AssignIndexStmtContext -> {
                val list = exprParser.parseExpression(child.expression(0)!!)
                val index = exprParser.parseExpression(child.expression(1)!!)
                val item = exprParser.parseExpression(child.expression(2)!!)
                if (list is StaticListExpression) {
                    return ExpressionStatement(
                        parser.functionResolver.resolveStaticListFunction(list, "set", listOf(index, item), child.position),
                        child.position
                    )
                }

                val type = ExpressionTypes.getExpressionType(list)
                val call = parser.functionResolver.resolveReceiverFunction(list, "set", listOf(index, item), filter = { it.operator }, position = child.position)
                    ?: return error("Cannot resolve $type.set(${index.type}, ${item.type})", child.position!!)

                ExpressionStatement(call, child.position)
            }
            is ScratcherLangParser.AssignStmtContext -> {
                parseAssignStatement(child)
            }
            is ScratcherLangParser.IfStmtContext -> {
                parseIfStatement(child)
            }
            is ScratcherLangParser.WhileStmtContext -> {
                val cond = exprParser.parseExpression(child.expression())
                val whileBlock = CodeBlock().also { parseBlock(it, child.block()) }
                WhileStatement(cond, whileBlock, child.position)
            }
            is ScratcherLangParser.RepeatStmtContext -> {
                val amount = exprParser.parseExpression(child.expression())
                if (child.indexedBlock().block() != null) {
                    val repeatBlock = CodeBlock().also { parseBlock(it, child.indexedBlock().block()!!) }
                    RepeatStatement(amount, repeatBlock, child.position)
                } else {
                    val block = child.indexedBlock()
                    val indexVar = LocalVariable(block.IDENTIFIER()!!.text, PrimitiveType.Integer)
                    if (parser.localVariables.find { it.name == indexVar.name } != null) {
                        this.error("Variable already exists!", block.IDENTIFIER()!!.position())
                    }
                    val amountVar = LocalVariable("repeat_stmt_amount_${getUniqueName()}", PrimitiveType.Integer)
                    CompositeStatement(listOf(
                        VariableStatement(IntLiteral((-1).toBigInteger(), child.position), indexVar, child.position),
                        VariableStatement(amount, amountVar, child.position),
                        WhileStatement(
                            condition = BinaryExpression(
                                left = BinaryExpression(
                                    left = LocalVariableExpression(indexVar, child.position),
                                    right = IntLiteral(BigInteger.ONE, child.position),
                                    operator = BinaryOperator.ADD,
                                    position = child.position
                                ),
                                right = LocalVariableExpression(amountVar, child.position),
                                operator = BinaryOperator.LESS_THAN,
                                position = child.position
                            ),
                            block = CodeBlock().also { blc ->
                                blc.code.add(LocalVariableAssignmentStatement(indexVar, BinaryExpression(
                                    left = LocalVariableExpression(indexVar, child.position),
                                    right = IntLiteral(BigInteger.ONE, child.position),
                                    operator = BinaryOperator.ADD,
                                    position = child.position
                                ), child.position))

                                parseIndexedBlock(block, indexVar, blc)
                            },
                            position = child.position
                        )
                    ), child.position)
                }
            }
            is ScratcherLangParser.ReturnIfStmtContext -> {
                val returnExpr = if (child.expression().size == 2) exprParser.parseExpression(child.expression(0)!!) else null
                val cond = exprParser.parseExpression(child.expression().last())

                IfStatement(cond, CodeBlock().also {
                    it.code.add(ReturnStatement(returnExpr?.let { StringBoxing.autoConvert(it, parser.currentFunction?.returnType) }, child.position))
                }, child.position)
            }
            is ScratcherLangParser.ForStmtContext -> {
                parseForStatement(child)
            }
            is ScratcherLangParser.PostIncStmtContext -> {
                parsePostIncStmt(child)
            }
            is ScratcherLangParser.WhenStmtContext -> {
                ExpressionStatement(exprParser.parseWhenExpr(child.whenExpression()), child.position)
            }
            is ScratcherLangParser.LoopControlStmtContext -> {
                when(child) {
                    is ScratcherLangParser.ContinueStmtContext -> {
                        ContinueStatement(child.position)
                    }
                    is ScratcherLangParser.BreakStmtContext -> {
                        BreakStatement(child.position)
                    }
                    is ScratcherLangParser.BreakIfStmtContext -> {
                        IfStatement(
                            condition = exprParser.parseExpression(child.expression()!!),
                            thenBlock = CodeBlock().also { it.code.add(BreakStatement(child.position)) },
                            position = child.position
                        )
                    }
                    is ScratcherLangParser.ContinueIfStmtContext -> {
                        IfStatement(
                            condition = exprParser.parseExpression(child.expression()!!),
                            thenBlock = CodeBlock().also { it.code.add(ContinueStatement(child.position)) },
                            position = child.position
                        )
                    }
                    else -> throw NotImplementedException("Unknown loop control statement ${child.text}")
                }
            }
            else -> throw NotImplementedException("Unknown statement type: ${child?.text}")
        }
    }

    private fun parseIndexedBlock(
        block: ScratcherLangParser.IndexedBlockContext,
        indexVar: LocalVariable,
        blc: CodeBlock,
        injectVariables: List<LocalVariable> = listOf()
    ) {
        val prevLocalVariables = parser.localVariables.toList()
        parser.localVariables.add(indexVar)
        parser.localVariables.addAll(injectVariables)
        val startIndex = parser.localVariables.size

        block.statement().map { parseStatement(it) }.forEach {
            blc.code.add(it)
        }
        block.returnStmt()?.let {
            blc.code.add(ReturnStatement(it.expression()?.let { expr ->
                val parsed = exprParser.parseExpression(expr, parser.currentFunction?.returnType)
                StringBoxing.autoConvert(parsed, parser.currentFunction?.returnType)
            }, it.position))
        }

        blc.localVariables.add(indexVar)
        blc.localVariables.addAll(injectVariables)
        blc.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))

        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)
    }

    private fun parseAssignStatement(child: ScratcherLangParser.AssignStmtContext): Statement {
        val variableExpr = exprParser.parseExpression(child.expression(0)!!)
        val assignmentExpr = exprParser.parseExpression(child.expression(1)!!)
        val assignmentType = child.assignOp()

        val op = when {
            assignmentType.ASSIGN() != null -> null
            assignmentType.ADD_ASSIGN() != null -> BinaryOperator.ADD
            assignmentType.DIV_ASSIGN() != null -> BinaryOperator.DIVIDE
            assignmentType.MUL_ASSIGN() != null -> BinaryOperator.MULTIPLY
            assignmentType.SUB_ASSIGN() != null -> BinaryOperator.SUBTRACT
            else -> throw GeneralCompilerException("Unknown assignment type $assignmentType")
        }

        return buildAssignment(variableExpr, op, assignmentExpr, child.position)
    }

    private fun parseStmtBlock(statementBlockContext: ScratcherLangParser.StmtBlockContext, code: CodeBlock) {
        if(statementBlockContext.block() != null) {
            parseBlock(code, statementBlockContext.block()!!)
        } else {
            code.code.add(parseStatement(statementBlockContext.statement()!!))
        }
    }

    private fun parseIfStatement(child: ScratcherLangParser.IfStmtContext): Statement {
        val cond = exprParser.parseExpression(child.expression())
        val thenBlock = CodeBlock().also { parseStmtBlock(child.stmtBlock(0)!!, it) }

        return if (child.ELSE() != null) {
            val elseBlock = if (child.ifStmt() != null) {
                //else if!!!
                val nestedIf = parseIfStatement(child.ifStmt()!!)

                CodeBlock().also {
                    it.code.add(nestedIf)
                }
            } else {
                CodeBlock().also { parseStmtBlock(child.stmtBlock(1)!!, it) }
            }
            IfElseStatement(cond, thenBlock, elseBlock, child.position)
        } else {
            IfStatement(cond, thenBlock, child.position)
        }
    }

    private fun parseForStatement(child: ScratcherLangParser.ForStmtContext): IfStatement {
        val list = exprParser.parseExpression(child.expression())
        val type = ExpressionTypes.getExpressionType(list)
        if (type is NullableType) {
            this.parser.reporter.logError(ast, child.expression().position!!, "List is nullable")
        }
        if (type is SimpleType && type.sourceAST.simplePath == "list" && type.name.startsWith("List")) {
            return parseListForStatement(child, list, type)
        }

        if (type !is ArrayType) {
            this.parser.reporter.logError(ast, child.expression().position!!, "For statement doesn't have an array, found $type")
            return IfStatement(ErrorExpression, CodeBlock(), child.position)
        }

        val listVar = LocalVariable(
            obfuscate("compiler@forStmtList${getUniqueName()}"),
            type
        )
        val variable = LocalVariable(
            name = child.IDENTIFIER().text,
            child.type()?.let {
                figureOutType(this.parser.reporter, parser.ctx, ast, it, localTypeBindings = parser.currentTypeBindings)
            }?: type.elementType
        )
        val indexVariable = LocalVariable(
            obfuscate("compiler@forStmtIndex${getUniqueName()}"),
            PrimitiveType.Integer
        )

        //is there a way to unsee code that you wrote?
        //this is a hack for both: hiding these variables from user code and bypassing the single statement limit
        return IfStatement(
            condition = BooleanLiteral(true, child.position),
            thenBlock = CodeBlock().also {
                val prevLocalVariables = parser.localVariables.map { variable -> variable }
                parser.localVariables.add(listVar)
                parser.localVariables.add(indexVariable)
                it.code.add(VariableStatement(list, listVar, child.position))
                it.code.add(VariableStatement(IntLiteral((-1).toBigInteger(), child.position), indexVariable, child.position))
                it.code.add(VariableStatement(null, variable, child.position))
                it.code.add(
                    RepeatStatement(
                        amount = CallExpression(
                            func = ArrayLib.length,
                            arguments = listOf(LocalVariableExpression(listVar, child.position)),
                            position = child.position
                        ),
                        block = CodeBlock().also { inner ->
                            inner.code.add(
                                LocalVariableAssignmentStatement(
                                    indexVariable, BinaryExpression(
                                        left = LocalVariableExpression(indexVariable, child.position),
                                        right = IntLiteral(1.toBigInteger(), child.position),
                                        operator = BinaryOperator.ADD,
                                        position = child.position
                                    ),
                                    child.position
                                )
                            )

                            inner.code.add(
                                LocalVariableAssignmentStatement(
                                    variable, CallExpression(
                                        func = ArrayLib.itemAt,
                                        listOf(LocalVariableExpression(listVar, child.position), LocalVariableExpression(indexVariable, child.position)),
                                        position = child.position
                                    ),
                                    child.position
                                )
                            )

                            if (child.indexedBlock().block() != null) {
                                parseBlock(inner, child.indexedBlock().block()!!, injectVariables = listOf(variable))
                            } else {
                                val indexVar0 = LocalVariable(child.indexedBlock().IDENTIFIER()!!.text, PrimitiveType.Integer)
                                inner.code.add(
                                    VariableStatement(LocalVariableExpression(indexVariable, child.position), indexVar0, child.position)
                                )

                                parseIndexedBlock(child.indexedBlock(), indexVar0, inner, injectVariables = listOf(variable))
                            }
                        },
                        position = child.position
                    ))
                it.localVariables.addAll(parser.localVariables)
                parser.localVariables.clear()
                parser.localVariables.addAll(prevLocalVariables)
            },
            position = child.position
        )
    }

    private fun parseListForStatement(
        child: ScratcherLangParser.ForStmtContext,
        list: Expression,
        type: SimpleType
    ): IfStatement {
        val listStruct = (ast.structs + ast.structTemplates + ast.imports.values.flatMap { it.structs + it.structTemplates }).find { it.type == type }
            ?: (CompilationContext.std.list.value.structs + CompilationContext.std.list.value.structTemplates).find { it.type == type }!!
        val ptrMember = listStruct.parameters.find { it.name == "ptr" || it.type is ArrayType }!!

        val listType = when {
            listStruct.typeBindings.isNotEmpty() -> listStruct.typeBindings.values.first()
            listStruct.typeParameters.isNotEmpty() -> PlaceholderType(listStruct.typeParameters.first())
            else -> {
                val raw = (ptrMember.type as ArrayType).elementType
                if (raw is NullableType && raw.inner is PlaceholderType) raw.inner else raw
            }
        }
        val indexVar = LocalVariable("list@for@index", PrimitiveType.Integer)
        val currentObjVar = LocalVariable(child.IDENTIFIER().text, listType)
        val listVar = LocalVariable("list@for@list", listStruct.type)

        return IfStatement(BooleanLiteral(true, child.position), CodeBlock().also {
            it.code.add(VariableStatement(IntLiteral((-1).toBigInteger(), child.position), indexVar, child.position))
            it.code.add(VariableStatement(null, currentObjVar, child.position))
            it.code.add(VariableStatement(list, listVar, child.position))
            it.code.add(RepeatStatement(
                amount = MemberExpression(
                    expression = LocalVariableExpression(listVar, child.position),
                    member = listStruct.parameters.find { it.type == PrimitiveType.Integer }!!, //length
                    struct = listStruct,
                    position = child.position
                ),
                block = CodeBlock().also { block ->
                    block.code.add(LocalVariableAssignmentStatement(
                        variable = indexVar,
                        assignment = BinaryExpression(
                            left = LocalVariableExpression(indexVar, child.position),
                            right = IntLiteral(BigInteger.ONE, child.position),
                            operator = BinaryOperator.ADD,
                            position = child.position
                        ),
                        position = child.position
                    ))

                    block.code.add(LocalVariableAssignmentStatement(
                        variable = currentObjVar,
                        assignment = CallExpression(
                            func = ArrayLib.itemAt,
                            arguments = listOf(
                                MemberExpression(
                                    expression = LocalVariableExpression(listVar, child.position),
                                    member = ptrMember,
                                    struct = listStruct,
                                    position = child.position
                                ),
                                LocalVariableExpression(indexVar, child.position)
                            ),
                            position = child.position
                        ).let { itemAt ->
                            if (listType is NullableType) itemAt else NonNullAssertExpression(itemAt, child.position)
                        },
                        position = child.position
                    ))

                    if (child.indexedBlock().block() != null) {
                        parseBlock(block, child.indexedBlock().block()!!, injectVariables = listOf(currentObjVar))
                    } else {
                        val variable = LocalVariable(child.indexedBlock().IDENTIFIER()!!.text, PrimitiveType.Integer)
                        block.code.add(
                            VariableStatement(LocalVariableExpression(indexVar, child.position), variable, child.position)
                        )

                        parseIndexedBlock(child.indexedBlock(), variable, block, injectVariables = listOf(currentObjVar))
                    }
                },
                position = child.position
            ))
        }, child.position)
    }

    private fun parsePostIncStmt(ctx: ScratcherLangParser.PostIncStmtContext): Statement {
        val targetExpr = when (ctx) {
            is ScratcherLangParser.PlusPlusContext -> exprParser.parseExpression(ctx.expression())
            is ScratcherLangParser.MinusMinusContext -> exprParser.parseExpression(ctx.expression())
            else -> throw UnsupportedOperationException(ctx.text)
        }

        val amount = if (ctx is ScratcherLangParser.PlusPlusContext) 1 else -1
        val amountExpr = IntLiteral(amount.toBigInteger(), ctx.position)

        return buildAssignment(targetExpr, BinaryOperator.ADD, amountExpr, ctx.position)
    }

    private fun buildAssignment(
        targetExpr: Expression,
        op: BinaryOperator?,
        valueExpr: Expression,
        position: Position? = targetExpr.position
    ): Statement {
        if (op == null) {
            return when (targetExpr) {
                is LocalVariableExpression -> LocalVariableAssignmentStatement(
                    targetExpr.variable,
                    StringBoxing.autoConvert(valueExpr, targetExpr.variable.type),
                    position
                )
                is MemberExpression -> VariableAssignmentStatement(
                    targetExpr.expression,
                    targetExpr.member,
                    targetExpr.struct,
                    StringBoxing.autoConvert(valueExpr, targetExpr.member.type),
                    position
                )
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) {
                        this.parser.reporter.logError(ast, targetExpr.position!!, "Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}")
                    }
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(valueExpr, targetExpr.variable.type),
                        position
                    )
                }
                else -> {
                    error("Not mutable", targetExpr.position!!)
                }
            }
        } else {
            return when (targetExpr) {
                is LocalVariableExpression -> {
                    val actualAssignment = exprParser.buildBinaryExpression(targetExpr, op, valueExpr, position)
                    LocalVariableAssignmentStatement(
                        targetExpr.variable,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type),
                        position
                    )
                }
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) {
                        return this.error("Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}", targetExpr.position!!)
                    }
                    val actualAssignment = exprParser.buildBinaryExpression(targetExpr, op, valueExpr, position)
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type),
                        position
                    )
                }
                is MemberExpression -> {
                    val baseExprType = ExpressionTypes.getExpressionType(targetExpr.expression)
                    val tempBaseVar = LocalVariable(
                        obfuscate("compiler@compoundAssignBase${getUniqueName()}"),
                        baseExprType
                    )
                    parser.localVariables.add(tempBaseVar)

                    val declareStmt = VariableStatement(targetExpr.expression, tempBaseVar, position)
                    val tempBaseExpr = LocalVariableExpression(tempBaseVar, position)
                    val readExpr = MemberExpression(tempBaseExpr, targetExpr.member, targetExpr.struct, position)
                    val actualAssignment = exprParser.buildBinaryExpression(readExpr, op, valueExpr, position)

                    val assignStmt = VariableAssignmentStatement(
                        tempBaseExpr,
                        targetExpr.member,
                        targetExpr.struct,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.member.type),
                        position
                    )

                    IfStatement(
                        condition = BooleanLiteral(true, position),
                        thenBlock = CodeBlock().also {
                            it.code.add(declareStmt)
                            it.code.add(assignStmt)
                            it.localVariables.add(tempBaseVar)
                        },
                        position = position
                    )
                }
                else -> {
                    this.error("Not mutable, tried to assign to non assignable expression", targetExpr.position!!)
                }
            }
        }
    }

    fun parseBlock(block: CodeBlock, blockCtx: ScratcherLangParser.BlockContext, injectVariables: List<LocalVariable> = listOf()) {
        val prevLocalVariables = parser.localVariables.toList()
        parser.localVariables.addAll(injectVariables)
        val startIndex = parser.localVariables.size

        blockCtx.statement().map { parseStatement(it) }.forEach {
            block.code.add(it)
        }
        blockCtx.returnStmt()?.let {
            block.code.add(ReturnStatement(it.expression()?.let { expr ->
                val parsed = exprParser.parseExpression(expr, parser.currentFunction?.returnType)
                StringBoxing.autoConvert(parsed, parser.currentFunction?.returnType)
            }, it.position))
        }

        block.localVariables.addAll(injectVariables)
        block.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))

        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)
    }

    fun error(
        message: String,
        position: Position
    ): Statement {
        this.parser.reporter.logError(ast, position, message)
        return ExpressionStatement(ErrorExpression, null)
    }
}