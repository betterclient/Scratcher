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
            is ScratcherLangParser.MemberExprContext -> parseMemberExpr(ctx, expectedType)
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
                val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
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
            is ScratcherLangParser.CheckSealedEnumTypeExprContext -> parseIsExpr(ctx)
            is ScratcherLangParser.CastSealedEnumExprContext -> parseCastExpr(ctx)
            else -> throw NotImplementedException("No parser for expr ${ctx.text} yet!")
        }

        return StringBoxing.autoConvert(expr, expectedType)
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
            val argCtxs = ctx.argList()?.expression() ?: emptyList()
            val args = argCtxs.map { parseExpression(it) }
            val argTypes = args.map { ExpressionTypes.getExpressionType(it) }

            val sealedVariant = tryResolveSealedVariantConstruction(innerExpr, expectedType, argTypes)
            if (sealedVariant != null) {
                val (sealed, variant) = sealedVariant
                if (args.size != variant.parameters.size) {
                    throw GeneralCompilerException("Sealed enum variant ${sealed.name}.${variant.name.substringAfter(".")} expects ${variant.parameters.size} argument(s), got ${args.size} at ${ctx.position}")
                }
                val inflated = args.mapIndexed { i, arg ->
                    StringBoxing.autoConvert(arg, variant.parameters[i].type)
                }
                return SealedEnumConstructionExpression(sealed, variant, inflated)
            }

            val receiverExpr = parseExpression(innerExpr.expression())
            val methodName = innerExpr.IDENTIFIER().text

            val callExpr = parser.functionResolver.resolveReceiverFunction(
                receiverExpr = receiverExpr,
                methodName = methodName,
                arguments = args
            )

            try {
                val memberExpr = parseMemberExpr(innerExpr)
                val funcType = ExpressionTypes.getExpressionType(memberExpr) as? FunctionType

                if (funcType != null && callExpr != null) {
                    throw NotFoundException("Ambiguous reference, found extension function ${callExpr.func} and $funcType from struct parameters")
                }
            } catch (_: Exception) { } //will throw if its primitive!!

            callExpr ?: run {
                val memberExpr = parseMemberExpr(innerExpr)
                val funcType = ExpressionTypes.getExpressionType(memberExpr) as? FunctionType

                DynamicCallExpression(
                    function = memberExpr,
                    type = funcType!!,
                    arguments = args
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
                type = ExpressionTypes.getExpressionType(func) as? FunctionType
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
            val fnType = ExpressionTypes.getExpressionType(fnExpr) as? FunctionType
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
            ExpressionTypes.getExpressionType(receiver)
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
                            StringBoxing.autoConvert(actualResolved, outType)
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
        val leftType = ExpressionTypes.getExpressionType(left)
        val rightType = ExpressionTypes.getExpressionType(right)

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

        val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(boxExpr))
        return StatementExpression(listOf(VariableStatement(boxExpr, temp)), use(LocalVariableExpression(temp)))
    }

    private fun bindBoxTwice(left: Expression, right: Expression, use: (Expression, Expression) -> Expression): Expression {
        val stmts = mutableListOf<Statement>()
        val leftRef = if (left.simple) {
            left
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(left))
            stmts.add(VariableStatement(left, temp))
            LocalVariableExpression(temp)
        }
        val rightRef = if (right.simple) {
            right
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(right))
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

    fun parseWhenExpr(ctx: ScratcherLangParser.WhenExpressionContext): Expression {
        val subject = ctx.expression()?.let { parseExpression(it) }
        val subjectVar = subject?.let { LocalVariable("whenStatement@subject${getUniqueName()}", ExpressionTypes.getExpressionType(
            it)) }
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
                    if (subjectVar != null) {
                        val sealedCheck = tryResolveWhenBranchAsSealedCheck(subjectVar, it)
                        if (sealedCheck != null) return@let sealedCheck
                    }
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

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext, expectedType: Type? = null): Expression {
        val sealedVariant = tryResolveSealedVariantConstruction(ctx, expectedType, null)
        if (sealedVariant != null) {
            val (sealed, variant) = sealedVariant
            if (variant.parameters.isNotEmpty()) {
                throw GeneralCompilerException("Sealed enum variant ${sealed.name}.${variant.name.substringAfter(".")} requires arguments, use ${sealed.name}.${variant.name.substringAfter(".")}(...)")
            }
            return SealedEnumConstructionExpression(sealed, variant, emptyList())
        }

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
        val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
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

    private fun findSealedEnumByName(name: String): SealedEnum? {
        val base = name.substringBefore("@")
        ast.sealedEnums.find { it.name == name }?.let { return it }
        ast.imports.values.flatMap { it.sealedEnums }.find { it.name == name }?.let { return it }
        ast.sealedEnums.find { it.name == base }?.let { return it }
        ast.imports.values.flatMap { it.sealedEnums }.find { it.name == base }?.let { return it }

        findSealedTemplateByName(base)?.let { return it }
        return null
    }

    private fun findSealedTemplateByName(name: String): SealedEnum? {
        val base = name.substringBefore("@")
        return ast.sealedEnumTemplates.find { it.name == base }
            ?: ast.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == base }
            ?: ast.sealedEnumTemplates.find { it.name == name }
            ?: ast.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == name }
    }

    private fun findVariantInfo(variantType: SimpleType): Triple<SealedEnum, Struct, Int>? {
        val allSealed = ast.sealedEnums + ast.imports.values.flatMap { it.sealedEnums }
        for (sealed in allSealed) {
            for ((idx, variant) in sealed.types.withIndex()) {
                if (variant.type == variantType) return Triple(sealed, variant, idx)
            }
        }
        return null
    }

    private fun tryResolveSealedVariantConstruction(memberCtx: ScratcherLangParser.MemberExprContext, expectedType: Type? = null, argTypes: List<Type>? = null): Pair<SealedEnum, Struct>? {
        val leftCtx = memberCtx.expression() as? ScratcherLangParser.IdExprContext ?: return null
        if (resolvesAsVariable(leftCtx.text)) return null

        val sealedBaseName = leftCtx.text.substringBefore("@").substringBefore("<")
        val variantShort = memberCtx.IDENTIFIER().text

        val concreteSealed = findSealedEnumByName(sealedBaseName)
        if (concreteSealed != null && concreteSealed.typeParameters.isEmpty()) {
            val variant = concreteSealed.types.find { it.name.substringAfter(".") == variantShort } ?: return null
            return Pair(concreteSealed, variant)
        }

        val template = findSealedTemplateByName(sealedBaseName) ?: run {
            val exact = findSealedEnumByName(sealedBaseName) ?: return null
            val variant = exact.types.find { it.name.substringAfter(".") == variantShort } ?: return null
            return Pair(exact, variant)
        }

        val bindings = mutableMapOf<String, Type>()
        var deduced = false

        if (expectedType is SealedEnumType && expectedType.name.substringBefore("@") == template.name) {
            if (expectedType.typeBindings.isNotEmpty()) {
                bindings.putAll(expectedType.typeBindings)
                deduced = true
            } else {
                val concreteForExpected = findSealedEnumByName(expectedType.name)
                if (concreteForExpected != null && concreteForExpected.typeBindings.isNotEmpty()) {
                    bindings.putAll(concreteForExpected.typeBindings)
                    deduced = true
                }
            }
        }

        if (argTypes != null) {
            val placeholderVariant = template.types.find { it.name.substringAfter(".") == variantShort }
            if (placeholderVariant != null) {
                for (i in argTypes.indices) {
                    val paramType = placeholderVariant.parameters.getOrNull(i)?.type ?: continue
                    Generics.deduceTypeArgs(paramType, argTypes[i], template.typeParameters, bindings)
                }
                if (template.typeParameters.all { bindings.containsKey(it) }) deduced = true
            }
        }

        if (!deduced && parser.currentTypeBindings.isNotEmpty()) {
            for (tp in template.typeParameters) {
                parser.currentTypeBindings[tp]?.let { bindings[tp] = it }
            }
            if (template.typeParameters.all { bindings.containsKey(it) }) deduced = true
        }

        if (!deduced) {
            val retType = parser.currentFunction?.returnType as? SealedEnumType
            if (retType != null && retType.name.substringBefore("@") == template.name && retType.typeBindings.isNotEmpty()) {
                bindings.putAll(retType.typeBindings)
            } else if (retType != null) {
                val retSealed = findSealedEnumByName(retType.name)
                if (retSealed != null && retSealed.typeBindings.isNotEmpty() && retSealed.name.substringBefore("@") == template.name) {
                    bindings.putAll(retSealed.typeBindings)
                }
            }
        }

        if (template.typeParameters.all { bindings.containsKey(it) }) {
            val typeArgs = template.typeParameters.map { bindings[it]!! }
            val sealedType = try {
                Generics.resolveGenericSealedEnum(parser.ctx, ast, template.name, typeArgs)
            } catch (_: Exception) { return null }
            val concreteSealedEnum = (ast.sealedEnums.find { it.type == sealedType }
                ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == sealedType }
                ?: findSealedEnumByName((sealedType as SealedEnumType).name)
                ?: template.sourceAST.sealedEnums.find { it.type == sealedType }) ?: return null
            val variant = concreteSealedEnum.types.find { it.name.substringAfter(".") == variantShort } ?: return null
            return Pair(concreteSealedEnum, variant)
        }

        return null
    }

    private fun resolvesAsVariable(name: String): Boolean {
        if (parser.localVariables.any { it.name == name }) return true
        if (parser.currentFunction?.parameters?.any { it.name == name } == true) return true
        return ast.variables.any { it.name == name }
    }

    private fun tryResolveWhenBranchAsSealedCheck(subjectVar: LocalVariable, condCtx: ScratcherLangParser.ExpressionContext): CheckSealedEnumTypeExpression? {
        val subjectType = subjectVar.type.asNonNull() as? SealedEnumType ?: return null
        val sealed = findSealedEnumByName(subjectType.name)
            ?: ast.sealedEnums.find { it.type == subjectType }
            ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == subjectType }
            ?: return null
        val baseName = sealed.name.substringBefore("@")
        val variant = when (condCtx) {
            is ScratcherLangParser.MemberExprContext -> {
                val left = condCtx.expression() as? ScratcherLangParser.IdExprContext ?: return null
                val leftBase = left.text.substringBefore("@")
                if (leftBase != baseName && left.text != sealed.name) return null
                val variantShort = condCtx.IDENTIFIER().text
                sealed.types.find { it.name.substringAfter(".") == variantShort }
            }
            is ScratcherLangParser.IdExprContext -> {
                val short = condCtx.text
                sealed.types.find { it.name.substringAfter(".") == short }
            }
            else -> null
        } ?: return null
        val tag = sealed.types.indexOf(variant)
        val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
        return CheckSealedEnumTypeExpression(LocalVariableExpression(subjectVar), variant, sealed, effectiveTag)
    }

    private fun parseIsExpr(ctx: ScratcherLangParser.CheckSealedEnumTypeExprContext): Expression {
        val left = parseExpression(ctx.expression())
        val leftType = ExpressionTypes.getExpressionType(left)
        val leftSealedType = leftType.asNonNull() as? SealedEnumType

        if (leftSealedType != null) {
            val inferred = tryResolveIsAsFromLeft(leftSealedType, ctx.type())
            if (inferred != null) {
                val (sealed, variant, tag) = inferred
                val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
                return CheckSealedEnumTypeExpression(left, variant, sealed, effectiveTag)
            }
        }
        val targetType = figureOutType(parser.ctx, ast, ctx.type(), localTypeBindings = parser.currentTypeBindings)
        val variantType = targetType.asNonNull() as? SimpleType
            ?: throw TypeAnalysisException("IS check target must be a sealed enum variant, got $targetType at ${ctx.position}")
        val info = findVariantInfo(variantType)
            ?: throw NotFoundException("Type ${ctx.type().text} is not a sealed enum variant")
        val (sealed, variant, tag) = info
        val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
        val leftNonNull = leftType.asNonNull()
        if (leftNonNull != sealed.type) {
            if (leftType !is NullableType || leftType.inner != sealed.type) {
                throw TypeAnalysisException("IS check left type $leftType is not ${sealed.type} at ${ctx.position}")
            }
        }
        return CheckSealedEnumTypeExpression(left, variant, sealed, effectiveTag)
    }

    private fun parseCastExpr(ctx: ScratcherLangParser.CastSealedEnumExprContext): Expression {
        val left = parseExpression(ctx.expression())
        val leftType = ExpressionTypes.getExpressionType(left)
        val leftSealedType = leftType.asNonNull() as? SealedEnumType
        if (leftSealedType != null) {
            val inferred = tryResolveIsAsFromLeft(leftSealedType, ctx.type())
            if (inferred != null) {
                val (sealed, variant, tag) = inferred
                val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
                return SealedEnumCastExpression(left, variant, sealed, effectiveTag)
            }
        }
        val targetType = figureOutType(parser.ctx, ast, ctx.type(), localTypeBindings = parser.currentTypeBindings)
        val variantType = targetType.asNonNull() as? SimpleType
            ?: throw TypeAnalysisException("AS cast target must be a sealed enum variant, got $targetType at ${ctx.position}")
        val info = findVariantInfo(variantType)
            ?: throw NotFoundException("Type ${ctx.type().text} is not a sealed enum variant")
        val (sealed, variant, tag) = info
        val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
        val leftNonNull = leftType.asNonNull()
        if (leftNonNull != sealed.type) {
            if (leftType !is NullableType || leftType.inner != sealed.type) {
                throw TypeAnalysisException("AS cast left type $leftType is not ${sealed.type} at ${ctx.position}")
            }
        }
        return SealedEnumCastExpression(left, variant, sealed, effectiveTag)
    }

    private fun tryResolveIsAsFromLeft(leftSealedType: SealedEnumType, targetTypeCtx: ScratcherLangParser.TypeContext): Triple<SealedEnum, Struct, Int>? {
        val pathCtx = targetTypeCtx as? ScratcherLangParser.PathTypeContext ?: return null
        val ids = pathCtx.typePath().IDENTIFIER()
        val hasArgs = pathCtx.type().isNotEmpty()
        val sealed = ast.sealedEnums.find { it.type == leftSealedType }
            ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == leftSealedType }
            ?: findSealedEnumByName(leftSealedType.name) ?: return null
        val baseName = sealed.name.substringBefore("@")
        val variantShort: String = when {
            ids.size == 1 -> {
                ids[0].text
            }
            ids.size >= 2 -> {
                val qualifier = ids[ids.size - 2].text.substringBefore("@")
                if (qualifier != baseName) return null
                ids.last().text
            }
            else -> return null
        }
        if (hasArgs) {
            return try {
                val explicitType = figureOutType(parser.ctx, ast, targetTypeCtx, localTypeBindings = parser.currentTypeBindings)
                val variantType = explicitType.asNonNull() as? SimpleType ?: return null
                findVariantInfo(variantType)
            } catch (_: Exception) { null }
        }
        val idx = sealed.types.indexOfFirst { it.name.substringAfter(".") == variantShort }
        if (idx == -1) return null
        val variant = sealed.types[idx]
        return Triple(sealed, variant, idx)
    }
}