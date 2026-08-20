package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.simple
import dev.betterclient.scratcher.std.StandardLibASTGenerator
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
        val expr = when (ctx) {
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
            is ScratcherLangParser.ThisExprContext -> {
                if (parser.currentFunction?.parameters?.any { it.name == "this" } == true) {
                    return ParameterExpression(
                        parser.currentFunction?.parameters?.find { it.name == "this" }!!
                    )
                }
                throw NotFoundException("Not a receiver function!")
            }
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
            is ScratcherLangParser.EqExprContext -> parseEqExpr(ctx)
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
            is ScratcherLangParser.SafeDotExprContext -> {
                val structExpr = parseExpression(ctx.expression())
                val struct = ExpressionTypes.getExpressionType(parser.ctx, structExpr).let { type ->
                    val baseType = type.asNonNull() as? SimpleType
                    baseType?.sourceAST?.structs?.find { it.type == baseType }?: throw GeneralCompilerException("$type is a primitive type or enum at ${ctx.position}, expected a struct, found $baseType")
                }
                val memberName = ctx.IDENTIFIER().text

                val member = struct.parameters.find { it.name == memberName }?: throw NotFoundException("Struct ${struct.name} does not have $memberName")

                return SafeDotExpression(struct, structExpr, member)
            }
            is ScratcherLangParser.ListCreationExprContext -> {
                val elementType = figureOutType(
                    parser.ctx,
                    ast,
                    ctx.type(),
                    localTypeBindings = parser.currentTypeBindings
                )
                CallExpression(
                    func = ListLib.newList,
                    arguments = mutableListOf<Expression>(TypeLiteral(elementType)).also {
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
                parseDynamicCall(ctx, expectedType)
            }
            is ScratcherLangParser.LambdaExprContext -> {
                parseLambda(ctx)
            }
            else -> throw NotImplementedException("No parser for expr ${ctx.text} yet!")
        }

        return StringBoxing.autoConvert(expr, expectedType, parser.ctx)
    }

    private fun parseDynamicCall(
        ctx: ScratcherLangParser.DynamicCallExprContext,
        expectedType: Type?
    ): Expression = when (val innerExpr = ctx.expression()) {
        is ScratcherLangParser.IdExprContext -> {
            parser.functionResolver.figureOutFunctionInternal(
                null,
                innerExpr.text,
                innerExpr.text,
                ctx.argList(),
                expectedType
            )
        }

        is ScratcherLangParser.ScopeExprContext -> {
            val importName = innerExpr.IDENTIFIER(0)!!.text
            val funcName = innerExpr.IDENTIFIER(1)!!.text
            parser.functionResolver.figureOutFunctionInternal(
                importName,
                funcName,
                ctx.text,
                ctx.argList(),
                expectedType
            )
        }

        is ScratcherLangParser.MemberExprContext -> {
            val receiverExpr = parseExpression(innerExpr.expression())
            val methodName = innerExpr.IDENTIFIER().text
            val userArgs = ctx.argList()?.expression()?.map { parseExpression(it) } ?: emptyList()

            val callExpr = parser.functionResolver.resolveReceiverFunction(
                receiverExpr = receiverExpr,
                methodName = methodName,
                arguments = userArgs
            )

            try {
                val memberExpr = parseMemberExpr(innerExpr)
                val funcType = ExpressionTypes.getExpressionType(parser.ctx, memberExpr) as? FunctionType

                if (funcType != null && callExpr != null) {
                    throw NotFoundException("Ambiguous reference, found extension function ${callExpr.func} and $funcType from struct parameters")
                }
            } catch (_: Exception) { } //will throw if its primitive!!

            callExpr ?: run {
                val memberExpr = parseMemberExpr(innerExpr)
                val funcType = ExpressionTypes.getExpressionType(parser.ctx, memberExpr) as? FunctionType

                DynamicCallExpression(
                    function = memberExpr,
                    type = funcType!!,
                    arguments = userArgs
                )
            }
        }

        is ScratcherLangParser.SafeDotExprContext -> {
            parseSafeDotDynamicCall(innerExpr, expectedType, ctx)
        }

        else -> {
            val func = parseExpression(innerExpr)
            DynamicCallExpression(
                function = func,
                type = ExpressionTypes.getExpressionType(parser.ctx, func) as? FunctionType
                    ?: throw GeneralCompilerException(
                        "Dynamic call without a function?"
                    ),
                arguments = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf()
            )
        }
    }

    private fun parseSafeDotDynamicCall(
        innerExpr: ScratcherLangParser.SafeDotExprContext,
        expectedType: Type?,
        ctx: ScratcherLangParser.DynamicCallExprContext
    ): Expression {
        val original = runCatching { parseExpression(innerExpr, expectedType) }
        if (original.isSuccess) {
            val fnExpr = original.getOrNull()!!
            val fnType = ExpressionTypes.getExpressionType(parser.ctx, fnExpr) as? FunctionType
            if (fnType != null) {
                return DynamicCallExpression(
                    function = fnExpr,
                    type = fnType,
                    arguments = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf()
                )
            }
        }

        val receiver = parseExpression(innerExpr.expression(), null)
        val userArgs = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf()

        val resolved = this.parser.functionResolver.resolveReceiverFunction(
            NonNullAssertExpression(receiver),
            innerExpr.IDENTIFIER().text,
            userArgs
        ) ?: throw (original.exceptionOrNull() ?: NotFoundException("Receiver function ${innerExpr.IDENTIFIER().text} not found"))

        val receiverVar = LocalVariable(
            "safeDotCall@receiver@${getUniqueName()}",
            ExpressionTypes.getExpressionType(parser.ctx, receiver)
        )
        val actualResolved = resolved.copy(
            arguments = resolved.arguments.mapIndexed { index, expression ->
                if (index == 0) NonNullAssertExpression(LocalVariableExpression(receiverVar))
                else expression
            }
        )

        if (actualResolved.func.returnType == PrimitiveType.Void) {
            return StatementExpression(
                statements = listOf(
                    VariableStatement(receiver, receiverVar),
                    IfStatement(
                        condition = neq(LocalVariableExpression(receiverVar), NullExpression),
                        thenBlock = CodeBlock().also {
                            it.code.add(ExpressionStatement(actualResolved))
                        }
                    )
                ),
                expression = NullExpression
            )
        }

        val outType = resolved.func.returnType.asNullable()
        val out = LocalVariable("safeDotCall@out@${getUniqueName()}", outType)
        return StatementExpression(
            statements = listOf(
                VariableStatement(NullExpression, out),
                VariableStatement(receiver, receiverVar),
                IfStatement(
                    condition = neq(LocalVariableExpression(receiverVar), NullExpression),
                    thenBlock = CodeBlock().also {
                        it.code.add(LocalVariableAssignmentStatement(
                            out,
                            StringBoxing.autoConvert(actualResolved, outType, parser.ctx)
                        ))
                    }
                )
            ),
            expression = LocalVariableExpression(out)
        )
    }

    private fun parseLambda(ctx: ScratcherLangParser.LambdaExprContext): LambdaExpression {
        val args0 = ctx.lambdaDecl()
        val args = if (args0.LPAREN() != null) {
            //arg list
            args0.IDENTIFIER().map { it.text }.zip(
                args0.type().map { figureOutType(this.parser.ctx, this.ast, it, localTypeBindings = parser.currentTypeBindings) }
            ).map {
                LocalVariable(it.first, it.second)
            }
        } else {
            listOf(
                LocalVariable(
                    args0.IDENTIFIER(0)!!.text,
                    figureOutType(this.parser.ctx, this.ast, args0.type(0)!!, localTypeBindings = parser.currentTypeBindings)
                )
            )
        }

        val block = when (val b = ctx.lambdaBlock()) {
            is ScratcherLangParser.BlockLambdaContext -> {
                CodeBlock().also {
                    this.parser.statementParser.parseBlock(it, b.block(), args)
                }
            }
            is ScratcherLangParser.ExprLambdaContext -> {
                CodeBlock().also { code ->
                    val block = b.exprBlock()
                    if (block.LBRACE() == null) {
                        val prevLocalVariables = parser.localVariables.toList()
                        parser.localVariables.addAll(args)
                        code.code.add(ReturnStatement(parseExpression(block.expression())))
                        parser.localVariables.clear()
                        parser.localVariables.addAll(prevLocalVariables)
                    } else {
                        val prevLocalVariables = parser.localVariables.toList()
                        parser.localVariables.addAll(args)
                        val startIndex = parser.localVariables.size

                        block.statement().map { parser.statementParser.parseStatement(it) }.forEach(code.code::add)
                        val result = parseExpression(block.expression())
                        code.code.add(ReturnStatement(result))

                        code.localVariables.addAll(args)
                        code.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))
                        parser.localVariables.clear()
                        parser.localVariables.addAll(prevLocalVariables)
                    }
                }
            }

            else -> throw NotImplementedException("Unsupported lambda type $b")
        }

        return LambdaExpression(
            parameters = args,
            block = block
        )
    }

    private fun parseEqExpr(ctx: ScratcherLangParser.EqExprContext): Expression {
        val op = if (ctx.EQ() != null) BinaryOperator.EQUAL else BinaryOperator.NOT_EQUAL
        val left = parseExpression(ctx.expression(0)!!)
        val right = parseExpression(ctx.expression(1)!!)
        val leftType = ExpressionTypes.getExpressionType(parser.ctx, left)
        val rightType = ExpressionTypes.getExpressionType(parser.ctx, right)

        if (leftType == PrimitiveType.Null || rightType == PrimitiveType.Null) {
            return BinaryExpression(left, op, right)
        }

        val stringBoxStruct = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" } ?: return BinaryExpression(left, op, right)
        val leftIsBox = isStringBox(leftType, stringBoxStruct)
        val rightIsBox = isStringBox(rightType, stringBoxStruct)

        return when {
            leftIsBox && rightIsBox -> boxVsBoxEquality(left, right, op, stringBoxStruct)
            leftIsBox && rightType == PrimitiveType.Str -> boxVsStrEquality(left, right, op, stringBoxStruct)
            rightIsBox && leftType == PrimitiveType.Str -> boxVsStrEquality(right, left, op, stringBoxStruct)
            else -> BinaryExpression(left, op, right)
        }
    }

    private fun isStringBox(type: Type, stringBoxStruct: Struct): Boolean {
        return type is NullableType && type.inner == stringBoxStruct.type
    }

    private fun boxVsStrEquality(boxExpr: Expression, strExpr: Expression, op: BinaryOperator, stringBoxStruct: Struct): Expression {
        return bindBoxOnce(boxExpr) { box ->
            val unboxed = unboxStringBox(box, stringBoxStruct)

            when (op) {
                BinaryOperator.EQUAL -> and(neq(box, NullExpression), eq(unboxed, strExpr))
                else -> or(eq(box, NullExpression), neq(unboxed, strExpr))
            }
        }
    }

    private fun boxVsBoxEquality(left: Expression, right: Expression, op: BinaryOperator, stringBoxStruct: Struct): Expression {
        return bindBoxTwice(left, right) { l, r ->
            val lIsNull = eq(l, NullExpression)
            val rIsNull = eq(r, NullExpression)
            val lNotNull = neq(l, NullExpression)
            val rNotNull = neq(r, NullExpression)
            val stringsEqual = eq(unboxStringBox(l, stringBoxStruct), unboxStringBox(r, stringBoxStruct))
            val stringsNotEqual = neq(unboxStringBox(l, stringBoxStruct), unboxStringBox(r, stringBoxStruct))

            when (op) {
                BinaryOperator.EQUAL -> or(
                    and(lIsNull, rIsNull),
                    and(lNotNull, and(rNotNull, stringsEqual))
                )
                else -> or(
                    and(lIsNull, rNotNull),
                    or(and(lNotNull, rIsNull), and(lNotNull, and(rNotNull, stringsNotEqual)))
                )
            }
        }
    }

    private fun bindBoxOnce(boxExpr: Expression, use: (Expression) -> Expression): Expression {
        if (boxExpr.simple) return use(boxExpr)

        val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(parser.ctx, boxExpr))
        return StatementExpression(listOf(VariableStatement(boxExpr, temp)), use(LocalVariableExpression(temp)))
    }

    private fun bindBoxTwice(left: Expression, right: Expression, use: (Expression, Expression) -> Expression): Expression {
        val stmts = mutableListOf<Statement>()
        val leftRef = if (left.simple) {
            left
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(parser.ctx, left))
            stmts.add(VariableStatement(left, temp))
            LocalVariableExpression(temp)
        }
        val rightRef = if (right.simple) {
            right
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(parser.ctx, right))
            stmts.add(VariableStatement(right, temp))
            LocalVariableExpression(temp)
        }
        val body = use(leftRef, rightRef)

        return if (stmts.isEmpty()) body else StatementExpression(stmts, body)
    }

    private fun unboxStringBox(expr: Expression, stringBoxStruct: Struct): Expression {
        return MemberExpression(expr, stringBoxStruct.parameters.first { it.name == "str" }, stringBoxStruct)
    }

    private fun eq(left: Expression, right: Expression): Expression = BinaryExpression(left, BinaryOperator.EQUAL, right)
    private fun neq(left: Expression, right: Expression): Expression = BinaryExpression(left, BinaryOperator.NOT_EQUAL, right)
    private fun and(left: Expression, right: Expression): Expression = BinaryExpression(left, BinaryOperator.AND, right)
    private fun or(left: Expression, right: Expression): Expression = BinaryExpression(left, BinaryOperator.OR, right)

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
        val startIndex = parser.localVariables.size

        block.statement().map { parser.statementParser.parseStatement(it) }.forEach(out.code::add)
        val result = parseExpression(block.expression())
        out.code.add(ExpressionStatement(result))

        out.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))
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