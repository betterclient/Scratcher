package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.ConcatExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NonNullAssertExpression
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.std.StandardLibASTGenerator

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
            variable.ctx = null
        }

        ast.eventListeners.forEach { listener ->
            listener.ctx?.let {
                listener.code.code.add(ExpressionStatement(
                    CallExpression(it, mutableListOf())
                ))
            }
        }

        currentFunction = null
        localVariables.clear()

        ast.functions.forEach {
            currentFunction = it
            localVariables.clear()
            if (ast.path != "typecheck") {
                addParameterChecks(it.code, it.parameters)
            }
            parseBlock(it.code, it.ctx!!)
            it.ctx = null
        }
        currentFunction = null
    }

    private fun addParameterChecks(
        code: CodeBlock,
        parameters: MutableList<Parameter>
    ) {
        if (CompilationConstants.DISABLE_TYPE_CHECKER) return

        for (parameter in parameters) {
            if (parameter.type == Type.float) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloatObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloat" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not a float!")
                    ).toMutableList())
                ))
            } else if (parameter.type == Type.int) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkIntObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkInt" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not an integer!")
                    ).toMutableList())
                ))
            }
        }
    }

    private fun parseBlock(block: CodeBlock, blockCtx: ScratcherLangParser.BlockContext) {
        val prevLocalVariables = localVariables.map { it }
        blockCtx.statement().map { parseStatement(it); }.forEach {
            block.code.add(it)
        }
        blockCtx.returnStmt()?.let {
            block.code.add(ReturnStatement(it.expression()?.let { expr -> parseExpression(expr) }))
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
                if (variable.type == Type.void) throw UnsupportedOperationException("Variable ${ast.simplePath}::${variable.name} is type void.")
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
                        VariableAssignmentStatement(variableExpr.expression, variableExpr.member, variableExpr.struct, assignmentExpr)
                    }
                    is VariableExpression -> {
                        if (!variableExpr.variable.mutable) throw IllegalStateException("Tried to assign to immutable field ${variableExpr.sourceAST.simplePath}::${variableExpr.variable.name}")

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
            is ScratcherLangParser.ReturnIfStmtContext -> {
                val returnExpr = if (child.expression().size == 2) parseExpression(child.expression(0)!!) else null
                val cond = parseExpression(child.expression().last())

                IfStatement(cond, CodeBlock().also {
                    it.code.add(ReturnStatement(returnExpr))
                })
            }
            else -> throw IllegalStateException("Unknown statement type: ${child?.text}")
        }
    }

    private fun parseExpression(ctx: ScratcherLangParser.ExpressionContext): Expression {
        return when (ctx) {
            is ScratcherLangParser.ParensExprContext -> parseExpression(ctx.expression())
            is ScratcherLangParser.CallExprContext -> figureOutFunction(ctx.functionIdentifier(), ctx.argList())
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
            is ScratcherLangParser.NullExprContext -> NullExpression
            is ScratcherLangParser.AssertNonNullContext -> {
                NonNullAssertExpression(parseExpression(ctx.expression()))
            }
            else -> throw UnsupportedOperationException("No parser for expr ${ctx.text} yet!")
        }
    }

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext): Expression {
        val structExpr = parseExpression(ctx.expression())
        val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
            val baseType = type.asNonNull()
            baseType.sourceAST!!.structs.find { it.type == baseType }?: throw UnsupportedOperationException("$type is a primitive type(?) at ${ctx.position}")
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
        funcCall: ScratcherLangParser.FunctionIdentifierContext,
        argList: ScratcherLangParser.ArgListContext?
    ): Expression {
        val sourceAST = if (funcCall.IDENTIFIER() != null) {
            ast
        } else {
            val import = funcCall.typePath()!!.IDENTIFIER(0)!!.text
            ast.imports[import]?: throw Exception("Import not found $import for ${funcCall.text}.")
        }

        val funcName = if (funcCall.IDENTIFIER() != null) {
            funcCall.IDENTIFIER()!!.text
        } else {
            funcCall.typePath()!!.IDENTIFIER(1)!!.text
        }

        val expectedArgListTypes = argList?.expression()?.map { expr -> ExpressionTypes.getExpressionType(parseExpression(expr)) }?: listOf()
        val args = argList?.expression()?.map { parseExpression(it) }?: listOf()

        var resolvedFunc = sourceAST.functions.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            matchesArgumentsExactly(expectedArgListTypes, foundArgListTypes)
        }

        if (resolvedFunc == null) {
            resolvedFunc = sourceAST.functions.find {
                if (it.name != funcName) return@find false

                val foundArgListTypes = it.parameters.map { par -> par.type }
                matchesArguments(expectedArgListTypes, foundArgListTypes)
            }
        }

        resolvedFunc?.let {
            if (!it.userAccessible) {
                throw UnsupportedOperationException("Function ${funcCall.text} is not accessible.")
            }
            return CallExpression(
                func = it,
                arguments = args
            )
        }

        sourceAST.structs.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            return@find matchesArguments(
                expectedArgListTypes,
                foundArgListTypes
            )
        }?.let {
            return CallExpression(it.allocFunc, args)
        }

        val targetFunc = "$funcName(${expectedArgListTypes.joinToString(", ") { it.name }})"
        val candidates = mutableListOf<String>()
        sourceAST.functions.filter { it.name == funcName }.forEach { func ->
            candidates.add("Function \"${func.returnType.name} ${func.name}(${func.parameters.joinToString(", ") { "${it.type} ${it.name}" }})\"")
        }
        sourceAST.structs.filter { it.name == targetFunc }.forEach { struct ->
            candidates.add("Struct \"${struct.name}\"")
        }

        throw NullPointerException("Function $targetFunc not found, candidates: \n${candidates.joinToString("\n")}\nStackTrace:")
    }

    private fun matchesArgumentsExactly(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false
        return provided.zip(expected).all { (from, to) ->
            from == to
        }
    }

    private fun matchesArguments(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false

        return provided.zip(expected).all { (from, to) ->
            from.isAssignable(to)
        }
    }

    private fun parseLiteral(ctx: ScratcherLangParser.LiteralContext): Expression {
        return when {
            ctx.FALSE() != null -> BooleanLiteral(false)
            ctx.TRUE() != null -> BooleanLiteral(true)
            ctx.FLOAT() != null -> FloatLiteral(ctx.FLOAT()!!.text.toBigDecimalOrNull()?: throw Exception("${ctx.FLOAT()?.text} is not a float!"))
            ctx.INT() != null -> IntLiteral(ctx.INT()!!.text.toBigIntegerOrNull()?: throw Exception("${ctx.INT()?.text} is not an int!"))
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
        val exprsReduced = if (exprs.size == 1) {
            if (ExpressionTypes.getExpressionType(exprs[0]) == Type.str) {
                exprs[0]
            } else {
                ConcatExpression(exprs[0], StringLiteral(""))
            }
        } else {
            exprs.reduce { left, right ->
                ConcatExpression(left, right)
            }
        }

        return exprsReduced
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