package dev.betterclient.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.BinaryExpression
import dev.betterclient.ast.BinaryOperator
import dev.betterclient.ast.BooleanLiteral
import dev.betterclient.ast.CallExpression
import dev.betterclient.ast.CodeBlock
import dev.betterclient.ast.Expression
import dev.betterclient.ast.ExpressionStatement
import dev.betterclient.ast.FloatLiteral
import dev.betterclient.ast.Function
import dev.betterclient.ast.IfElseStatement
import dev.betterclient.ast.IfStatement
import dev.betterclient.ast.IntLiteral
import dev.betterclient.ast.LocalVariable
import dev.betterclient.ast.LocalVariableAssignmentStatement
import dev.betterclient.ast.LocalVariableExpression
import dev.betterclient.ast.MemberExpression
import dev.betterclient.ast.ParameterExpression
import dev.betterclient.ast.RepeatStatement
import dev.betterclient.ast.Statement
import dev.betterclient.ast.StringLiteral
import dev.betterclient.ast.TLVariableAssignmentStatement
import dev.betterclient.ast.UnaryExpression
import dev.betterclient.ast.UnaryOperator
import dev.betterclient.ast.VariableAssignmentStatement
import dev.betterclient.ast.VariableExpression
import dev.betterclient.ast.VariableStatement
import dev.betterclient.ast.WhileStatement
import dev.betterclient.std.StandardLibASTGenerator

class Stage1Parser(val ctx: CompilationContext, val ast: ASTFile) {
    fun parse() {
        ast.completedStage1Parsing = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedStage1Parsing) {
                ast.completedStage1Parsing = true
                Stage1Parser(ctx, ast).parse()
            }
        }

        if (StandardLibASTGenerator.lib.containsValue(ast)) return //do not try to parse standard lib

        parseInternal()
    }

    val localVariables = mutableListOf<LocalVariable>() //keep track of local variables
    var currentFunction: Function? = null

    private fun parseInternal() {
        for (variable in ast.variables) {
            variable.ctx?.let {
                variable.defaultValue = parseExpression(it)
            }
        }

        currentFunction = null
        localVariables.clear()

        ast.functions.forEach {
            currentFunction = it
            localVariables.clear()
            parseBlock(it.code, it.ctx!!.block())
        }
        currentFunction = null
    }

    private fun parseBlock(block: CodeBlock, blockCtx: ScratcherLangParser.BlockContext) {
        val prevLocalVariables = localVariables.map { it }
        blockCtx.statement().map { parseStatement(it) }.forEach {
            block.code.add(it)
        }

        block.localVariables.addAll(localVariables)
        localVariables.clear()
        localVariables.addAll(prevLocalVariables)
    }

    private fun parseStatement(ctx: ScratcherLangParser.StatementContext): Statement {
        return when (val child = ctx.getChild(0)) {
            is ScratcherLangParser.VarDeclContext -> {
                val type = figureOutType(this.ctx, ast, child.type())
                val name = child.IDENTIFIER().text
                val value = parseExpression(child.expression())

                val variable = LocalVariable(name, type)
                localVariables.add(variable)
                VariableStatement(value, variable)
            }
            is ScratcherLangParser.ExprStmtContext -> ExpressionStatement(parseExpression(child.expression()))
            is ScratcherLangParser.AssignStmtContext -> {
                val variableExpr = parseExpression(child.expression(0)!!)
                val assignmentExpr = parseExpression(child.expression(1)!!)

                when(variableExpr) {
                    is LocalVariableExpression -> {
                        LocalVariableAssignmentStatement(variableExpr.variable, assignmentExpr)
                    }
                    is MemberExpression -> {
                        VariableAssignmentStatement(variableExpr.member, variableExpr.struct, assignmentExpr)
                    }
                    is VariableExpression -> {
                        if (!variableExpr.variable.mutable) throw IllegalStateException("Tried to assign to immutable field ${variableExpr.sourceAST.path}::${variableExpr.variable.name}")

                        TLVariableAssignmentStatement(variableExpr.variable, variableExpr.sourceAST, assignmentExpr)
                    }
                    else -> throw IllegalStateException("Tried to assign to non assignable expression $variableExpr")
                }
            }
            is ScratcherLangParser.IfStmtContext -> {
                val cond = parseExpression(child.expression())
                val thenBlock = CodeBlock().also { parseBlock(it, child.block(0)!!) }

                if (child.ELSE() != null) {
                    val elseBlock = CodeBlock().also { parseBlock(it, child.block(1)!!) }

                    IfElseStatement(cond, thenBlock, elseBlock)
                } else {
                    IfStatement(cond, thenBlock)
                }
            }
            is ScratcherLangParser.WhileStmtContext -> {
                val cond = parseExpression(child.expression())
                val whileBlock = CodeBlock().also { parseBlock(it, child.block()) }
                WhileStatement(cond, whileBlock)
            }
            is ScratcherLangParser.RepeatStmtContext -> {
                val amount = parseExpression(child.expression())
                val repeatBlock = CodeBlock().also { parseBlock(it, child.block()) }
                RepeatStatement(amount, repeatBlock)
            }
            else -> throw IllegalStateException("Unknown statement type: ${child?.text}")
        }
    }

    private fun parseExpression(ctx: ScratcherLangParser.ExpressionContext): Expression {
        return when (ctx) {
            is ScratcherLangParser.ParensExprContext -> parseExpression(ctx.expression())
            is ScratcherLangParser.CallExprContext -> CallExpression(
                func = figureOutFunction(ctx.funcCall(), ctx.argList()),
                arguments = ctx.argList()?.expression()?.map { parseExpression(it) }?: listOf()
            )
            is ScratcherLangParser.UnaryExprContext -> UnaryExpression(
                operator = when {
                    ctx.PLUS() != null -> UnaryOperator.PLUS
                    ctx.MINUS() != null -> UnaryOperator.MINUS
                    ctx.BANG() != null -> UnaryOperator.NOT
                    else -> throw IllegalStateException("Unknown or missing unary operator in expression: ${ctx.text}")
                },
                expression = parseExpression(ctx.expression())
            )
            is ScratcherLangParser.LiteralExprContext -> parseLiteral(ctx.literal())
            is ScratcherLangParser.IdExprContext -> parseIdentifier(ctx.text)
            is ScratcherLangParser.MultExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.MOD() != null -> BinaryOperator.MODULO
                    ctx.SLASH() != null -> BinaryOperator.DIVIDE
                    ctx.STAR() != null -> BinaryOperator.MULTIPLY
                    else -> throw IllegalStateException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!),
            )
            is ScratcherLangParser.AddExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.PLUS() != null -> BinaryOperator.ADD
                    ctx.MINUS() != null -> BinaryOperator.SUBTRACT
                    else -> throw IllegalStateException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.RelExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.GE() != null -> BinaryOperator.GREATER_EQUAL
                    ctx.GT() != null -> BinaryOperator.GREATER_THAN
                    ctx.LE() != null -> BinaryOperator.LESS_EQUAL
                    ctx.LT() != null -> BinaryOperator.LESS_THAN
                    else -> throw IllegalStateException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.EqExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.EQ() != null -> BinaryOperator.EQUAL
                    ctx.NE() != null -> BinaryOperator.NOT_EQUAL
                    else -> throw IllegalStateException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.AndExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.AND,
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.OrExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.OR,
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.ScopeExprContext -> parseScopeExpr(ctx)
            is ScratcherLangParser.MemberExprContext -> parseMemberExpr(ctx)
            else -> throw UnsupportedOperationException("No parser for expr ${ctx.text} yet!")
        }
    }

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext): Expression {
        val structExpr = parseExpression(ctx.expression())
        val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
            type.sourceAST!!.structs.find { it.type == type }?: throw UnsupportedOperationException("$type is a primitive type(?) at ${ctx.position}")
        }
        val memberName = ctx.IDENTIFIER().text

        val member = struct.parameters.find { it.name == memberName }?: throw NullPointerException("Struct ${struct.name} does not have $memberName")

        return MemberExpression(structExpr, member, struct)
    }

    private fun parseScopeExpr(ctx: ScratcherLangParser.ScopeExprContext): Expression {
        //this is just for accessing tl variables from imports
        val import = ctx.IDENTIFIER(0)!!.text
        val variable = ctx.IDENTIFIER(1)!!.text

        return ast.imports[import]?.variables?.find { it.name == variable }?.let { VariableExpression(it, ast.imports[import]!!) }
            ?: throw NullPointerException("${ctx.text} not found")
    }

    private fun parseIdentifier(text: String): Expression {
        //either top level variable or local variable or parameter
        val variableFinding = localVariables.find { it.name == text }
        if (variableFinding != null) {
            return LocalVariableExpression(variableFinding)
        }

        val parameterFinding = currentFunction?.parameters?.find { it.name == text }
        if (parameterFinding != null) {
            return ParameterExpression(parameterFinding)
        }

        val variable = ast.variables.find { it.name == text }?: throw NullPointerException("Variable $text not found")
        return VariableExpression(variable, ast)
    }

    private fun figureOutFunction(
        funcCall: ScratcherLangParser.FuncCallContext,
        argList: ScratcherLangParser.ArgListContext?
    ): Function {
        val sourceAST = if (funcCall.IDENTIFIER() != null) {
            ast
        } else {
            val import = funcCall.typePath()!!.IDENTIFIER(0)!!.text
            ast.imports[import]?: throw Exception("Import not found $import for $funcCall.")
        }

        val funcName = if (funcCall.IDENTIFIER() != null) {
            funcCall.IDENTIFIER()!!.text
        } else {
            funcCall.typePath()!!.IDENTIFIER(1)!!.text
        }

        val expectedArgListTypes = argList?.expression()?.map { expr -> ExpressionTypes.getExpressionType(parseExpression(expr)) }?: listOf()

        return sourceAST.functions.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            return@find expectedArgListTypes == foundArgListTypes
        }?: let {
            //maybe its a struct initializer
            sourceAST.structs.find {
                if (it.name != funcName) return@find false

                val foundArgListTypes = it.parameters.map { par -> par.type }
                return@find expectedArgListTypes == foundArgListTypes
            }?.initFunc?: throw NullPointerException("Function not found: ${sourceAST.path}::${funcName}")
        }
    }

    private fun parseLiteral(ctx: ScratcherLangParser.LiteralContext): Expression {
        return when {
            ctx.FALSE() != null -> BooleanLiteral(false)
            ctx.TRUE() != null -> BooleanLiteral(true)
            ctx.FLOAT() != null -> FloatLiteral(ctx.FLOAT()!!.text.toFloatOrNull()?: throw Exception("${ctx.FLOAT()?.text} is not a float!"))
            ctx.INT() != null -> IntLiteral(ctx.INT()!!.text.toIntOrNull()?: throw Exception("${ctx.INT()?.text} is not an int!"))
            ctx.stringLiteral() != null -> parseStringInterp(ctx.stringLiteral()!!.stringPart())
            else -> throw UnsupportedOperationException("$ctx is not one of the expected types.")
        }
    }

    private fun parseStringInterp(parts: List<ScratcherLangParser.StringPartContext>): Expression {
        if (parts.isEmpty()) return StringLiteral("")
        val exprs = parts.map { part ->
            when {
                part.STR_TEXT() != null -> {
                    StringLiteral(part.STR_TEXT()!!.text)
                }
                part.STR_ESC() != null -> {
                    StringLiteral(unescapeString(part.STR_ESC()!!.text))
                }
                part.DOLLAR() != null -> {
                    StringLiteral("$")
                }
                part.interpolation() != null -> {
                    parseExpression(part.interpolation()!!.expression())
                }
                else -> StringLiteral("")
            }
        }

        return exprs.reduce { left, right ->
            BinaryExpression(left, BinaryOperator.ADD, right)
        }
    }

    private fun unescapeString(esc: String): String {
        return when (esc) {
            "\\n" -> "\n"
            "\\t" -> "\t"
            "\\r" -> "\r"
            "\\\"" -> "\""
            "\\\\" -> "\\"
            "\\$" -> "$"
            else -> if (esc.startsWith("\\")) esc.substring(1) else esc
        }
    }
}