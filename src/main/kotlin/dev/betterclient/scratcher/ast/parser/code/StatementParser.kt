package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ArrayLib
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
                    figureOutType(parser.ctx, ast, it, localTypeBindings = parser.currentTypeBindings)
                }
                val value = exprParser.parseExpression(child.expression(), type)
                val resolvedType = type ?: ExpressionTypes.getExpressionType(value)

                if (resolvedType == PrimitiveType.Null) throw GeneralCompilerException("Expected any type, found null, variable $name in ${ast.simplePath}::${parser.currentFunction?.name}")
                val variable = LocalVariable(name, resolvedType)
                if (variable.type == PrimitiveType.Void) throw VoidVariableException("Variable ${ast.simplePath}::${parser.currentFunction?.name}::${variable.name} is type void.")
                if (parser.localVariables.find { it.name == variable.name } != null) throw DuplicateDefinitionException("Variable ${variable.name} already exists in ${ast.simplePath}::${parser.currentFunction?.name}")
                parser.localVariables.add(variable)
                VariableStatement(value, variable)
            }
            is ScratcherLangParser.ExprStmtContext -> ExpressionStatement(exprParser.parseExpression(child.expression()))
            is ScratcherLangParser.AssignIndexStmtContext -> {
                val list = exprParser.parseExpression(child.expression(0)!!)
                val index = exprParser.parseExpression(child.expression(1)!!)
                val item = exprParser.parseExpression(child.expression(2)!!)
                val type = ExpressionTypes.getExpressionType(list)
                if(type is SimpleType && type.sourceAST.simplePath == "list" && type.name.startsWith("List")) {
                    val call = parser.functionResolver.resolveReceiverFunction(list, "replace", listOf(index, item))
                        ?: throw NotFoundException("Cannot resolve replace for $type")
                    ExpressionStatement(call)
                } else {
                    val elementType = (ExpressionTypes.getExpressionType(list).asNonNull() as? ArrayType)?.elementType

                    ExpressionStatement(
                        CallExpression(
                            func = ArrayLib.replace,
                            listOf(list, StringBoxing.autoConvert(item, elementType), index)
                        )
                    )
                }
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
                WhileStatement(cond, whileBlock)
            }
            is ScratcherLangParser.RepeatStmtContext -> {
                val amount = exprParser.parseExpression(child.expression())
                val repeatBlock = CodeBlock().also { parseBlock(it, child.block()) }
                RepeatStatement(amount, repeatBlock)
            }
            is ScratcherLangParser.ReturnIfStmtContext -> {
                val returnExpr = if (child.expression().size == 2) exprParser.parseExpression(child.expression(0)!!) else null
                val cond = exprParser.parseExpression(child.expression().last())

                IfStatement(cond, CodeBlock().also {
                    it.code.add(ReturnStatement(returnExpr?.let { StringBoxing.autoConvert(it, parser.currentFunction?.returnType) }))
                })
            }
            is ScratcherLangParser.ForStmtContext -> {
                parseForStatement(child)
            }
            is ScratcherLangParser.PostIncStmtContext -> {
                parsePostIncStmt(child)
            }
            is ScratcherLangParser.WhenStmtContext -> {
                ExpressionStatement(exprParser.parseWhenExpr(child.whenExpression()))
            }
            else -> throw NotImplementedException("Unknown statement type: ${child?.text}")
        }
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

        return buildAssignment(variableExpr, op, assignmentExpr)
    }

    private fun parseIfStatement(child: ScratcherLangParser.IfStmtContext): Statement {
        val cond = exprParser.parseExpression(child.expression())
        val thenBlock = CodeBlock().also { parseBlock(it, child.block(0)!!) }

        return if (child.ELSE() != null) {
            val elseBlock = if (child.ifStmt() != null) {
                //else if!!!
                val nestedIf = parseIfStatement(child.ifStmt()!!)

                CodeBlock().also {
                    it.code.add(nestedIf)
                }
            } else {
                CodeBlock().also { parseBlock(it, child.block(1)!!) }
            }
            IfElseStatement(cond, thenBlock, elseBlock)
        } else {
            IfStatement(cond, thenBlock)
        }
    }

    private fun parseForStatement(child: ScratcherLangParser.ForStmtContext): IfStatement {
        val list = exprParser.parseExpression(child.expression())
        val type = ExpressionTypes.getExpressionType(list)
        if (type is NullableType) throw TypeAnalysisException("List is nullable in for statement at ${child.position}")
        if (type is SimpleType && type.sourceAST.simplePath == "list" && type.name.startsWith("List")) {
            return parseListForStatement(child, list, type)
        }

        if (type !is ArrayType) throw TypeAnalysisException("For statement doesn't have an array at ${child.position}")

        val listVar = LocalVariable(
            obfuscate("compiler@forStmtList${getUniqueName()}"),
            type
        )
        val variable = LocalVariable(
            name = child.IDENTIFIER().text,
            child.type()?.let {
                figureOutType(parser.ctx, ast, it, localTypeBindings = parser.currentTypeBindings)
            }?: type.elementType
        )
        val indexVariable = LocalVariable(
            obfuscate("compiler@forStmtIndex${getUniqueName()}"),
            PrimitiveType.Integer
        )

        //is there a way to unsee code that you wrote?
        //this is a hack for both: hiding these variables from user code and bypassing the single statement limit
        return IfStatement(
            condition = BooleanLiteral(true),
            thenBlock = CodeBlock().also {
                val prevLocalVariables = parser.localVariables.map { variable -> variable }
                parser.localVariables.add(listVar)
                parser.localVariables.add(indexVariable)
                it.code.add(VariableStatement(list, listVar))
                it.code.add(VariableStatement(IntLiteral(BigInteger.ZERO), indexVariable))
                it.code.add(VariableStatement(null, variable))
                it.code.add(
                    RepeatStatement(
                        amount = CallExpression(
                            func = ArrayLib.length,
                            arguments = listOf(LocalVariableExpression(listVar))
                        ),
                        block = CodeBlock().also { inner ->
                            parseBlock(inner, child.block(), injectVariables = listOf(variable))
                            inner.code.add(
                                0, LocalVariableAssignmentStatement(
                                    variable, CallExpression(
                                        func = ArrayLib.itemAt,
                                        listOf(LocalVariableExpression(listVar), LocalVariableExpression(indexVariable))
                                    )
                                )
                            )
                            inner.code.add(
                                LocalVariableAssignmentStatement(
                                    indexVariable, BinaryExpression(
                                        left = LocalVariableExpression(indexVariable),
                                        right = IntLiteral(1.toBigInteger()),
                                        operator = BinaryOperator.ADD
                                    )
                                )
                            )
                        }
                    ))
                it.localVariables.addAll(parser.localVariables)
                parser.localVariables.clear()
                parser.localVariables.addAll(prevLocalVariables)
            }
        )
    }

    private fun parseListForStatement(
        child: ScratcherLangParser.ForStmtContext,
        list: Expression,
        type: SimpleType
    ): IfStatement {
        val listStruct = (ast.structs + ast.structTemplates + ast.imports.values.flatMap { it.structs + it.structTemplates }).find { it.type == type }
            ?: (StandardLibASTGenerator.list.value.structs + StandardLibASTGenerator.list.value.structTemplates).find { it.type == type }!!
        val ptrMember = listStruct.parameters.find { it.name == "ptr" || it.type is ArrayType }!!
        val listType = (ptrMember.type as ArrayType).elementType
        val indexVar = LocalVariable("list@for@index", PrimitiveType.Integer)
        val currentObjVar = LocalVariable(child.IDENTIFIER().text, listType)
        val listVar = LocalVariable("list@for@list", listStruct.type)

        return IfStatement(BooleanLiteral(true), CodeBlock().also {
            it.code.add(VariableStatement(IntLiteral(BigInteger.ZERO), indexVar))
            it.code.add(VariableStatement(null, currentObjVar))
            it.code.add(VariableStatement(list, listVar))
            it.code.add(RepeatStatement(
                amount = MemberExpression(
                    expression = LocalVariableExpression(listVar),
                    member = listStruct.parameters.find { it.type == PrimitiveType.Integer }!!, //length
                    struct = listStruct
                ),
                block = CodeBlock().also { block ->
                    block.code.add(LocalVariableAssignmentStatement(
                        variable = currentObjVar,
                        assignment = CallExpression(
                            func = ArrayLib.itemAt,
                            arguments = listOf(
                                MemberExpression(
                                    expression = LocalVariableExpression(listVar),
                                    member = ptrMember,
                                    struct = listStruct
                                ),
                                LocalVariableExpression(indexVar)
                            )
                        )
                    ))

                    parseBlock(block, child.block(), injectVariables = listOf(currentObjVar))

                    block.code.add(LocalVariableAssignmentStatement(
                        variable = indexVar,
                        assignment = BinaryExpression(
                            left = LocalVariableExpression(indexVar),
                            right = IntLiteral(BigInteger.ONE),
                            operator = BinaryOperator.ADD
                        )
                    ))
                }
            ))
        })
    }

    private fun parsePostIncStmt(ctx: ScratcherLangParser.PostIncStmtContext): Statement {
        val targetExpr = when (ctx) {
            is ScratcherLangParser.PlusPlusContext -> exprParser.parseExpression(ctx.expression())
            is ScratcherLangParser.MinusMinusContext -> exprParser.parseExpression(ctx.expression())
            else -> throw UnsupportedOperationException(ctx.text)
        }

        val amount = if (ctx is ScratcherLangParser.PlusPlusContext) 1 else -1
        val amountExpr = IntLiteral(amount.toBigInteger())

        return buildAssignment(targetExpr, BinaryOperator.ADD, amountExpr)
    }

    private fun buildAssignment(
        targetExpr: Expression,
        op: BinaryOperator?,
        valueExpr: Expression
    ): Statement {
        if (op == null) {
            return when (targetExpr) {
                is LocalVariableExpression -> LocalVariableAssignmentStatement(
                    targetExpr.variable,
                    StringBoxing.autoConvert(valueExpr, targetExpr.variable.type)
                )
                is MemberExpression -> VariableAssignmentStatement(
                    targetExpr.expression,
                    targetExpr.member,
                    targetExpr.struct,
                    StringBoxing.autoConvert(valueExpr, targetExpr.member.type)
                )
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) throw GeneralCompilerException("Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}")
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(valueExpr, targetExpr.variable.type)
                    )
                }
                else -> throw GeneralCompilerException("Not mutable, tried to assign to non assignable expression $targetExpr")
            }
        } else {
            return when (targetExpr) {
                is LocalVariableExpression -> {
                    val actualAssignment = BinaryExpression(targetExpr, op, valueExpr)
                    LocalVariableAssignmentStatement(
                        targetExpr.variable,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type)
                    )
                }
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) throw GeneralCompilerException("Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}")
                    val actualAssignment = BinaryExpression(targetExpr, op, valueExpr)
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type)
                    )
                }
                is MemberExpression -> {
                    val baseExprType = ExpressionTypes.getExpressionType(targetExpr.expression)
                    val tempBaseVar = LocalVariable(
                        obfuscate("compiler@compoundAssignBase${getUniqueName()}"),
                        baseExprType
                    )
                    parser.localVariables.add(tempBaseVar)

                    val declareStmt = VariableStatement(targetExpr.expression, tempBaseVar)
                    val tempBaseExpr = LocalVariableExpression(tempBaseVar)
                    val readExpr = MemberExpression(tempBaseExpr, targetExpr.member, targetExpr.struct)
                    val actualAssignment = BinaryExpression(readExpr, op, valueExpr)

                    val assignStmt = VariableAssignmentStatement(
                        tempBaseExpr,
                        targetExpr.member,
                        targetExpr.struct,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.member.type)
                    )

                    IfStatement(
                        condition = BooleanLiteral(true),
                        thenBlock = CodeBlock().also {
                            it.code.add(declareStmt)
                            it.code.add(assignStmt)
                            it.localVariables.add(tempBaseVar)
                        }
                    )
                }
                else -> throw GeneralCompilerException("Not mutable, tried to assign to non assignable expression $targetExpr")
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
            }))
        }

        block.localVariables.addAll(injectVariables)
        block.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))

        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)
    }
}