package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.std.lib.ListLib

class ExpressionParser(
    val parser: Stage1Parser,
    val ast: ASTFile
) {
    val literalParser = LiteralParser(this, parser)
    fun parseExpression(
        ctx: ScratcherLangParser.ExpressionContext,
        expectedType: Type? = null
    ): Expression {
        return when (ctx) {
            is ScratcherLangParser.ParensExprContext -> parseExpression(ctx.expression(), expectedType)
            is ScratcherLangParser.CallExprContext -> parser.functionResolver.figureOutFunction(ctx.functionIdentifier(), ctx.argList(), expectedType)
            is ScratcherLangParser.UnaryExprContext -> UnaryExpression(
                operator = when {
                    ctx.PLUS() != null -> UnaryOperator.PLUS
                    ctx.MINUS() != null -> UnaryOperator.MINUS
                    ctx.BANG() != null -> UnaryOperator.NOT
                    else -> throw NotImplementedException("Unknown or missing unary operator in expression: ${ctx.text}")
                },
                expression = parseExpression(ctx.expression())
            )
            is ScratcherLangParser.LiteralExprContext -> literalParser.parseLiteral(ctx.literal())
            is ScratcherLangParser.IdExprContext -> parseIdentifier(ctx.text)
            is ScratcherLangParser.MultExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.MOD() != null -> BinaryOperator.MODULO
                    ctx.SLASH() != null -> BinaryOperator.DIVIDE
                    ctx.STAR() != null -> BinaryOperator.MULTIPLY
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!),
            )
            is ScratcherLangParser.AddExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.PLUS() != null -> BinaryOperator.ADD
                    ctx.MINUS() != null -> BinaryOperator.SUBTRACT
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
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
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.EqExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.EQ() != null -> BinaryOperator.EQUAL
                    ctx.NE() != null -> BinaryOperator.NOT_EQUAL
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
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
            is ScratcherLangParser.NonNullOrElseContext -> {
                NonNullOrElseExpression(
                    parseExpression(ctx.expression(0)!!),
                    parseExpression(ctx.expression(1)!!)
                )
            }
            is ScratcherLangParser.ListCreationExprContext -> {
                val list = figureOutType(parser.ctx, ast, ctx.type(), localTypeBindings = parser.currentTypeBindings)
                CallExpression(
                    ListLib.newList,
                    mutableListOf(
                        StringLiteral( //so sorry for this but im not bothering adding more expressions just for this one fricking function
                            list.toString()
                        )
                    ).also {
                        if (CompilationConstants.MARK_AND_SWEEP_GC) {
                            it.add(StringLiteral(
                                "l"
                            ))
                        }
                    }
                )
            }
            is ScratcherLangParser.IndexExprContext -> {
                val list = parseExpression(ctx.expression(0)!!)
                val index = parseExpression(ctx.expression(1)!!)
                CallExpression(
                    func = ListLib.itemAt,
                    listOf(list, index)
                )
            }
            is ScratcherLangParser.WhenExprContext -> {
                parseWhenExpr(ctx.whenExpression())
            }
            is ScratcherLangParser.IfExprContext -> {
                WhenExpression(
                    null,
                    parseIfExpr(ctx.ifExpression())
                )
            }
            is ScratcherLangParser.FuncRefExprContext -> {
                FunctionLiteral(
                    parser.functionResolver.figureOutFunctionSimple(ctx.functionIdentifier())
                )
            }
            is ScratcherLangParser.DynamicCallExprContext -> {
                val innerExpr = ctx.expression()
                when (innerExpr) {
                    is ScratcherLangParser.IdExprContext -> {
                        parser.functionResolver.figureOutFunctionInternal(null, innerExpr.text, innerExpr.text, ctx.argList(), expectedType)
                    }
                    is ScratcherLangParser.ScopeExprContext -> {
                        val importName = innerExpr.IDENTIFIER(0)!!.text
                        val funcName = innerExpr.IDENTIFIER(1)!!.text
                        parser.functionResolver.figureOutFunctionInternal(importName, funcName, innerExpr.text, ctx.argList(), expectedType)
                    }
                    else -> {
                        val func = parseExpression(innerExpr)
                        DynamicCallExpression(
                            function = func,
                            type = ExpressionTypes.getExpressionType(parser.ctx, func) as? FunctionType ?: throw GeneralCompilerException(
                                "Dynamic call without a function?"
                            ),
                            arguments = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf()
                        )
                    }
                }
            }
            else -> throw NotImplementedException("No parser for expr ${ctx.text} yet!")
        }
    }

    fun parseIfExpr(ctx: ScratcherLangParser.IfExpressionContext): List<WhenBranch> {
        val branch = mutableListOf<WhenBranch>()

        val thenCond = parseExpression(ctx.expression())
        val thenBlock = parseExprBlock(ctx.exprBlock(0)!!)
        branch.add(WhenBranch(
            thenCond, thenBlock
        ))
        if (ctx.ifExpression() == null) {
            val elseBlock = parseExprBlock(ctx.exprBlock(1)!!)
            branch.add(WhenBranch(
                BooleanLiteral(true), elseBlock, isElse = true
            ))
        } else {
            branch.addAll(parseIfExpr(ctx.ifExpression()!!))
            if (branch.filter { it.cond is BooleanLiteral && it.cond.value }.size > 1) {
                throw DuplicateDefinitionException("Duplicate else blocks in if expression $ctx")
            }
        }

        return branch
    }

    private fun parseExprBlock(block: ScratcherLangParser.ExprBlockContext): CodeBlock {
        val out = CodeBlock()

        if (block.LBRACE() == null) {
            out.code.add(ExpressionStatement(parseExpression(block.expression())))
            return out
        }

        val prevLocalVariables = parser.localVariables.toList()
        block.statement().map { parser.statementParser.parseStatement(it) }.forEach(out.code::add)
        val result = parseExpression(block.expression())
        out.code.add(ExpressionStatement(result))

        out.localVariables.addAll(parser.localVariables)
        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)

        return out
    }

    private fun parseWhenExpr(ctx: ScratcherLangParser.WhenExpressionContext): Expression {
        val subject = ctx.expression()?.let { parseExpression(it) }
        val subjectVar = subject?.let { LocalVariable("whenStatement@subject${getUniqueName()}", ExpressionTypes.getExpressionType(parser.ctx, it)) }
        val subjectAssignment = subjectVar?.let { VariableStatement(subject, it) }
        val entries = ctx.whenEntry()
        if (entries.isEmpty()) {
            throw GeneralCompilerException("When expression must have at least one branch at ${ctx.position?.start}")
        }
        if (entries.count { it.whenCondition().ELSE() != null } > 1) {
            throw DuplicateDefinitionException("Duplicate ELSE condition in ${ctx.position?.start}")
        }
        for (i in entries.indices) {
            if (entries[i].whenCondition().ELSE() != null && i != entries.lastIndex) {
                throw GeneralCompilerException("ELSE must be the last branch in when expression at ${ctx.position?.start}")
            }
        }

        return WhenExpression(
            subjectAssignment,
            entries.map { entry ->
                val isElse = entry.whenCondition().ELSE() != null
                val cond = entry.whenCondition().expression()?.let {
                    val expr = parseExpression(it)
                    if (subjectVar != null) {
                        BinaryExpression(
                            left = LocalVariableExpression(subjectVar),
                            right = expr,
                            operator = BinaryOperator.EQUAL
                        )
                    } else expr
                } ?: BooleanLiteral(true) //else

                val branchBlock = CodeBlock()
                if (entry.expression() != null) {
                    branchBlock.code.add(ExpressionStatement(parseExpression(entry.expression()!!)))
                } else if (entry.codeBlock() != null) {
                    val block = entry.codeBlock()!!
                    block.block()?.let {
                        parser.statementParser.parseBlock(branchBlock, it)
                    }
                    block.statement()?.let {
                        branchBlock.code.add(parser.statementParser.parseStatement(it))
                    }

                }

                WhenBranch(
                    cond,
                    branchBlock,
                    isElse
                )
            }
        )
    }

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext): Expression {
        val enum = when (val leftExpr = ctx.expression()) {
            is ScratcherLangParser.IdExprContext -> {
                val name = leftExpr.text
                ast.enums.find { it.name == name }
                    ?: ast.imports.values.flatMap { it.enums }.find { it.name == name }
            }
            is ScratcherLangParser.ScopeExprContext -> {
                val importName = leftExpr.IDENTIFIER(0)!!.text
                val enumName = leftExpr.IDENTIFIER(1)!!.text
                ast.imports[importName]?.enums?.find { it.name == enumName }
            }
            else -> null
        }

        if (enum != null) {
            val memberName = ctx.IDENTIFIER().text
            val ordinal = enum.values.indexOf(memberName)
            if (ordinal == -1) {
                throw NotFoundException("Enum ${enum.name} does not have value $memberName")
            }
            return EnumLiteral(enum, memberName, ordinal)
        }

        val structExpr = parseExpression(ctx.expression())
        val struct = ExpressionTypes.getExpressionType(parser.ctx, structExpr).let { type ->
            val baseType = type.asNonNull() as? SimpleType
            baseType?.sourceAST?.structs?.find { it.type == baseType }?: throw GeneralCompilerException("$type is a primitive type(?) at ${ctx.position}, expected a struct, found $baseType")
        }
        val memberName = ctx.IDENTIFIER().text

        val member = struct.parameters.find { it.name == memberName }?: throw NotFoundException("Struct ${struct.name} does not have $memberName")

        return MemberExpression(structExpr, member, struct)
    }

    private fun parseScopeExpr(ctx: ScratcherLangParser.ScopeExprContext): Expression {
        //this is just for accessing tl variables from imports
        val import = ctx.IDENTIFIER(0)!!.text
        val variable = ctx.IDENTIFIER(1)!!.text

        return ast.imports[import]?.variables?.find { it.name == variable }?.let { VariableExpression(it, ast.imports[import]!!) }
            ?: throw NotFoundException("${ctx.text} not found")
    }

    private fun parseIdentifier(text: String): Expression {
        //either top level variable or local variable or parameter
        val variableFinding = parser.localVariables.find { it.name == text }
        if (variableFinding != null) {
            return LocalVariableExpression(variableFinding)
        }

        val parameterFinding = parser.currentFunction?.parameters?.find { it.name == text }
        if (parameterFinding != null) {
            return ParameterExpression(parameterFinding)
        }

        val variable = ast.variables.find { it.name == text }?: throw NotFoundException("Variable $text not found")
        return VariableExpression(variable, ast)
    }
}