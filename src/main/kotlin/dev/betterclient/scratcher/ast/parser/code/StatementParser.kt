package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.DuplicateDefinitionException
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NotImplementedException
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TypeAnalysisException
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.VoidVariableException
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.lib.ListLib
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
                val resolvedType = type ?: ExpressionTypes.getExpressionType(parser.ctx, value)

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
                val elementType = (ExpressionTypes.getExpressionType(parser.ctx, list).asNonNull() as? ListType)?.elementType

                ExpressionStatement(
                    CallExpression(
                        func = ListLib.replace,
                        listOf(list, StringBoxing.autoConvert(item, elementType, parser.ctx), index)
                    )
                )
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
                    it.code.add(ReturnStatement(returnExpr?.let { StringBoxing.autoConvert(it, parser.currentFunction?.returnType, parser.ctx) }))
                })
            }
            is ScratcherLangParser.ForStmtContext -> {
                parseForStatement(child)
            }
            is ScratcherLangParser.PostIncStmtContext -> {
                parsePostIncStmt(child)
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
        val type = ExpressionTypes.getExpressionType(parser.ctx, list)
        if (type is NullableType) throw TypeAnalysisException("List is nullable in for statement at ${child.position}")
        if (type !is ListType) throw TypeAnalysisException("For statement doesn't have a list at ${child.position}")

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
                            func = ListLib.length,
                            arguments = listOf(LocalVariableExpression(listVar))
                        ),
                        block = CodeBlock().also { inner ->
                            parseBlock(inner, child.block(), injectVariables = listOf(variable))
                            inner.code.add(
                                0, LocalVariableAssignmentStatement(
                                    variable, CallExpression(
                                        func = ListLib.itemAt,
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
                    StringBoxing.autoConvert(valueExpr, targetExpr.variable.type, parser.ctx)
                )
                is MemberExpression -> VariableAssignmentStatement(
                    targetExpr.expression,
                    targetExpr.member,
                    targetExpr.struct,
                    StringBoxing.autoConvert(valueExpr, targetExpr.member.type, parser.ctx)
                )
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) throw GeneralCompilerException("Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}")
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(valueExpr, targetExpr.variable.type, parser.ctx)
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
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type, parser.ctx)
                    )
                }
                is VariableExpression -> {
                    if (!targetExpr.variable.mutable) throw GeneralCompilerException("Tried to assign to immutable field ${targetExpr.sourceAST.simplePath}::${targetExpr.variable.name}")
                    val actualAssignment = BinaryExpression(targetExpr, op, valueExpr)
                    TLVariableAssignmentStatement(
                        targetExpr.variable,
                        targetExpr.sourceAST,
                        StringBoxing.autoConvert(actualAssignment, targetExpr.variable.type, parser.ctx)
                    )
                }
                is MemberExpression -> {
                    val baseExprType = ExpressionTypes.getExpressionType(parser.ctx, targetExpr.expression)
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
                        StringBoxing.autoConvert(actualAssignment, targetExpr.member.type, parser.ctx)
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
        val prevLocalVariables = parser.localVariables.map { it }
        parser.localVariables.addAll(injectVariables)
        blockCtx.statement().map { parseStatement(it); }.forEach {
            block.code.add(it)
        }
        blockCtx.returnStmt()?.let {
            block.code.add(ReturnStatement(it.expression()?.let { expr -> StringBoxing.autoConvert(exprParser.parseExpression(expr), parser.currentFunction?.returnType, parser.ctx) }))
        }

        block.localVariables.addAll(parser.localVariables)
        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)
    }
}