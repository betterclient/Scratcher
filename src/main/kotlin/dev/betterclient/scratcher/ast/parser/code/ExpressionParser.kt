package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.ExpressionTypes.type
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.ast.parser.position
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.simple
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ArrayLib
import org.antlr.v4.kotlinruntime.ast.Position

class ExpressionParser(
    val parser: Stage1Parser,
    val ast: ASTFile
) {
    val reporter = parser.reporter
    val literalParser = LiteralParser(this, parser)
    var currentPosition: Position? = null

    fun parseExpression(
        ctx: ScratcherLangParser.ExpressionContext,
        expectedType: Type? = null
    ): Expression {
        currentPosition = ctx.position
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
                expression = parseExpression(ctx.expression()),
                position = ctx.position
            )
            is ScratcherLangParser.LiteralExprContext -> literalParser.parseLiteral(ctx.literal())
            is ScratcherLangParser.IdExprContext -> parseIdentifier(ctx.text, ctx.position)
            is ScratcherLangParser.ThisExprContext -> {
                if (parser.currentFunction?.parameters?.any { it.name == "this" } == true) {
                    return ParameterExpression(
                        parser.currentFunction?.parameters?.find { it.name == "this" }!!,
                        ctx.position
                    )
                }
                return error(ctx.position!!, ast, "Not a receiver function!")
            }
            is ScratcherLangParser.MultExprContext -> buildBinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                op = when {
                    ctx.MOD() != null -> BinaryOperator.MODULO
                    ctx.SLASH() != null -> BinaryOperator.DIVIDE
                    ctx.STAR() != null -> BinaryOperator.MULTIPLY
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!),
                position = ctx.position
            )
            is ScratcherLangParser.AddExprContext -> buildBinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                op = when {
                    ctx.PLUS() != null -> BinaryOperator.ADD
                    ctx.MINUS() != null -> BinaryOperator.SUBTRACT
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!),
                position = ctx.position
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
                right = parseExpression(ctx.expression(1)!!),
                position = ctx.position
            )
            is ScratcherLangParser.EqExprContext -> parseEqExpr(ctx)
            is ScratcherLangParser.ExactEqualsExprContext -> parseExactEqualsExpr(ctx)
            is ScratcherLangParser.AndExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.AND,
                right = parseExpression(ctx.expression(1)!!),
                position = ctx.position
            )
            is ScratcherLangParser.OrExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.OR,
                right = parseExpression(ctx.expression(1)!!),
                position = ctx.position
            )
            is ScratcherLangParser.ScopeExprContext -> parseScopeExpr(ctx)
            is ScratcherLangParser.MemberExprContext -> parseMemberExpr(ctx, expectedType)
            is ScratcherLangParser.NullExprContext -> NullExpression
            is ScratcherLangParser.AssertNonNullContext -> {
                NonNullAssertExpression(parseExpression(ctx.expression()), ctx.position)
            }
            is ScratcherLangParser.NonNullOrElseContext -> {
                NonNullOrElseExpression(
                    parseExpression(ctx.expression(0)!!),
                    parseExpression(ctx.expression(1)!!),
                    ctx.position
                )
            }
            is ScratcherLangParser.SafeDotExprContext -> {
                val structExpr = parseExpression(ctx.expression())
                val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
                    val baseType = type.asNonNull() as? SimpleType
                    baseType?.sourceAST?.structs?.find { it.type == baseType }?: run {
                        return error(ctx.position!!, ast, "$type is a primitive type or enum at ${ctx.position}, expected a struct, found $baseType")
                    }
                }
                val memberName = ctx.IDENTIFIER().text

                val member = struct.parameters.find { it.name == memberName }?: run {
                    return error(ctx.position!!, ast, "Struct ${struct.name} does not have $memberName")
                }
                if (member.private && struct.sourceAST != this.parser.ast) {
                    error(ctx.position!!, ast, "Struct ${struct.name}'s member \"${member.name}\" is private!")
                }

                return SafeDotExpression(struct, structExpr, member, ctx.position)
            }
            is ScratcherLangParser.IndexExprContext -> {
                val list = parseExpression(ctx.expression(0)!!)
                val index = parseExpression(ctx.expression(1)!!)
                val type = ExpressionTypes.getExpressionType(list)
                if (list is StaticListExpression) {
                    return parser.functionResolver.resolveStaticListFunction(list, "get", listOf(index))
                }

                parser.functionResolver.resolveReceiverFunction(list, "get", listOf(index), filter = { it.operator })
                    ?: run {
                        return error(ctx.position!!, ast, "Cannot resolve ${type}.get(${index.type})")
                    }
            }
            is ScratcherLangParser.WhenExprContext -> {
                parseWhenExpr(ctx.whenExpression())
            }
            is ScratcherLangParser.IfExprContext -> {
                WhenExpression(
                    null,
                    parseIfExpr(ctx.ifExpression()),
                    ctx.position
                )
            }
            is ScratcherLangParser.FuncRefExprContext -> {
                parser.functionResolver.figureOutFunctionSimple(ctx.functionIdentifier())?.let {
                    FunctionLiteral(
                        it,
                        ctx.position
                    )
                }?: ErrorExpression
            }
            is ScratcherLangParser.DynamicCallExprContext -> {
                parseDynamicCall(ctx, expectedType)
            }
            is ScratcherLangParser.LambdaExprContext -> {
                parseLambda(ctx)
            }
            is ScratcherLangParser.CheckSealedEnumTypeExprContext -> parseIsExpr(ctx)
            is ScratcherLangParser.CastSealedEnumExprContext -> parseCastExpr(ctx)
            is ScratcherLangParser.NewArrayExprContext -> parseArrayExpr(ctx, expectedType)
            else -> throw NotImplementedException("No parser for expr ${ctx.text} yet!")
        }

        return StringBoxing.autoConvert(expr, expectedType)
    }

    private fun parseExactEqualsExpr(ctx: ScratcherLangParser.ExactEqualsExprContext): Expression {
        val isEq = ctx.EXACT_EQUALS() != null
        val strictOp = if (isEq) BinaryOperator.STRICT_EQUAL else BinaryOperator.STRICT_NOT_EQUAL
        val normalOp = if (isEq) BinaryOperator.EQUAL else BinaryOperator.NOT_EQUAL

        val left = parseExpression(ctx.expression(0)!!)
        val right = parseExpression(ctx.expression(1)!!)
        val lt = ExpressionTypes.getExpressionType(left)
        val rt = ExpressionTypes.getExpressionType(right)

        val directComp = listOf(PrimitiveType.Str, PrimitiveType.Char)

        if (lt.asNonNull() !in directComp && rt.asNonNull() !in directComp)
            return BinaryExpression(left, normalOp, right, ctx.position)
        if (lt.asNonNull() in directComp && rt.asNonNull() in directComp)
            return BinaryExpression(left, strictOp, right, ctx.position)

        val sb = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" }
            ?: return BinaryExpression(left, normalOp, right, ctx.position)

        val lb = isStringBox(lt, sb)
        val rb = isStringBox(rt, sb)

        if (lb || rb) {
            val boxExpr = if (lb) left else right
            val strExpr = if (lb) right else left
            return bindBoxOnce(boxExpr, ctx.position) { b ->
                val u = unboxStringBox(b, sb, ctx.position)
                if (isEq) and(neq(b, NullExpression, ctx.position), BinaryExpression(u, strictOp, strExpr, ctx.position), ctx.position)
                else or(eq(b, NullExpression, ctx.position), BinaryExpression(u, strictOp, strExpr, ctx.position), ctx.position)
            }
        }

        return BinaryExpression(left, normalOp, right, ctx.position)
    }

    private fun parseArrayExpr(ctx: ScratcherLangParser.NewArrayExprContext, expectedType: Type?): Expression {
        val expectedElemType = when (val exp = expectedType?.asNonNull()) {
            is ArrayType -> exp.elementType
            else -> null
        }

        val elements = ctx.expression().map { parseExpression(it, expectedElemType) }

        val actualElemType = when {
            elements.isNotEmpty() -> {
                val elementTypes = elements.map { ExpressionTypes.getExpressionType(it) }
                val inferred = elementTypes.reduceIndexed { index, left, right ->
                    unifyTypes(left, right)
                        ?: return error(
                            elements[index].position!!,
                            ast,
                            "Cannot unify element types in array literal, left: $left, right: $right"
                        )
                }
                if (expectedElemType != null && inferred.isAssignable(expectedElemType)) {
                    expectedElemType
                } else {
                    inferred
                }
            }
            expectedElemType != null -> expectedElemType
            else -> run {
                return error(ctx.position!!, ast, "Cannot determine type for empty array literal []. Please specify an explicit type.")
            }
        }
        val type = ArrayType(actualElemType)

        val outVar = LocalVariable("array_expr_out_${getUniqueName()}", type)
        val prepend = mutableListOf<Statement>()

        val allocCall = CallExpression(
            func = ArrayLib.newArray,
            arguments = listOf(
                TypeLiteral(actualElemType, ctx.position),
                IntLiteral(elements.size.toBigInteger(), ctx.position)
            ),
            position = ctx.position
        )
        prepend.add(VariableStatement(allocCall, outVar, ctx.position))

        elements.forEachIndexed { index, elem ->
            val elemPos = elem.position ?: ctx.position
            prepend.add(
                ExpressionStatement(
                    CallExpression(
                        func = ArrayLib.replace,
                        arguments = listOf(
                            LocalVariableExpression(outVar, elemPos),
                            StringBoxing.autoConvert(elem, actualElemType),
                            IntLiteral(index.toBigInteger(), elemPos)
                        ),
                        position = elemPos
                    ),
                    elemPos
                )
            )
        }

        return StatementExpression(
            statements = prepend,
            expression = LocalVariableExpression(outVar, ctx.position),
            position = ctx.position
        )
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
                expectedType,
                ctx.position
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
                expectedType,
                ctx.position
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
                    return error(ctx.position!!, ast, "Sealed enum variant ${sealed.name}.${variant.name.substringAfter(".")} expects ${variant.parameters.size} argument(s), got ${args.size}")
                }
                if (sealed.private && sealed.sourceAST != parser.ast) {
                    error(ctx.position!!, ast, "Sealed enum ${sealed.name} is private and cannot be accessed from ${parser.ast.simplePath}")
                }
                val inflated = args.mapIndexed { i, arg ->
                    StringBoxing.autoConvert(arg, variant.parameters[i].type)
                }
                return SealedEnumConstructionExpression(sealed, variant, inflated, ctx.position)
            }

            val receiverExpr = parseExpression(innerExpr.expression())
            val methodName = innerExpr.IDENTIFIER().text

            if (receiverExpr is StaticListExpression) {
                return parser.functionResolver.resolveStaticListFunction(receiverExpr, methodName, args, ctx.position)
            }

            val callExpr = parser.functionResolver.resolveReceiverFunction(
                receiverExpr = receiverExpr,
                methodName = methodName,
                arguments = args,
                position = ctx.position
            )

            val memberExpr = reporter.catching { parseMemberExpr(innerExpr) }
            val funcType = memberExpr?.type as? FunctionType
            if (funcType != null && callExpr != null) {
                return error(ctx.position!!, ast, "Ambiguous reference, found extension function ${callExpr.func} and $funcType from struct parameters")
            }

            callExpr ?: run {
                val memberExpr = parseMemberExpr(innerExpr)
                val funcType = ExpressionTypes.getExpressionType(memberExpr) as? FunctionType
                if (memberExpr == ErrorExpression) return@run memberExpr

                DynamicCallExpression(
                    function = memberExpr,
                    type = funcType?: run {
                        return error(ctx.position!!, ast, "Dynamic call without function!")
                    },
                    arguments = args,
                    position = ctx.position
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
                    ?: run {
                        return error(ctx.position!!, ast, "Dynamic call without function!")
                    },
                arguments = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf(),
                position = ctx.position
            )
        }
    }

    private fun parseSafeDotDynamicCall(
        innerExpr: ScratcherLangParser.SafeDotExprContext,
        expectedType: Type?,
        ctx: ScratcherLangParser.DynamicCallExprContext
    ): Expression {
        val original = reporter.catching { parseExpression(innerExpr, expectedType) }
        if (original != null) {
            val fnType = ExpressionTypes.getExpressionType(original) as? FunctionType
            if (fnType != null) {
                return DynamicCallExpression(
                    function = original,
                    type = fnType,
                    arguments = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf(),
                    position = ctx.position
                )
            }
        }

        val receiver = parseExpression(innerExpr.expression(), null)
        val userArgs = ctx.argList()?.expression()?.map { parseExpression(it) } ?: listOf()

        val resolved = this.parser.functionResolver.resolveReceiverFunction(
            NonNullAssertExpression(receiver, innerExpr.position),
            innerExpr.IDENTIFIER().text,
            userArgs,
            position = innerExpr.position
        ) ?: run {
            return original?: error(innerExpr.position!!, ast, "Receiver function ${innerExpr.IDENTIFIER().text} not found")
        }

        val receiverVar = LocalVariable(
            "safeDotCall@receiver@${getUniqueName()}",
            ExpressionTypes.getExpressionType(receiver)
        )
        val actualResolved = resolved.copy(
            arguments = resolved.arguments.mapIndexed { index, expression ->
                if (index == 0) NonNullAssertExpression(LocalVariableExpression(receiverVar, innerExpr.position), innerExpr.position)
                else expression
            }
        )

        if (actualResolved.func.returnType == PrimitiveType.Void) {
            return StatementExpression(
                statements = listOf(
                    VariableStatement(receiver, receiverVar, ctx.position),
                    IfStatement(
                        condition = neq(LocalVariableExpression(receiverVar, ctx.position), NullExpression, ctx.position),
                        thenBlock = CodeBlock().also {
                            it.code.add(ExpressionStatement(actualResolved, ctx.position))
                        },
                        position = ctx.position
                    )
                ),
                expression = NullExpression,
                position = ctx.position
            )
        }

        val outType = resolved.func.returnType.asNullable()
        val out = LocalVariable("safeDotCall@out@${getUniqueName()}", outType)
        return StatementExpression(
            statements = listOf(
                VariableStatement(NullExpression, out, ctx.position),
                VariableStatement(receiver, receiverVar, ctx.position),
                IfStatement(
                    condition = neq(LocalVariableExpression(receiverVar, ctx.position), NullExpression, ctx.position),
                    thenBlock = CodeBlock().also {
                        it.code.add(LocalVariableAssignmentStatement(
                            out,
                            StringBoxing.autoConvert(actualResolved, outType),
                            ctx.position
                        ))
                    },
                    position = ctx.position
                )
            ),
            expression = LocalVariableExpression(out, ctx.position),
            position = ctx.position
        )
    }

    private fun parseLambda(ctx: ScratcherLangParser.LambdaExprContext): LambdaExpression {
        val args0 = ctx.lambdaDecl()
        val args = if (args0.LPAREN() != null) {
            //arg list
            args0.IDENTIFIER().map { it.text }.zip(
                args0.type().map { figureOutType(reporter, this.parser.ctx, this.ast, it, localTypeBindings = parser.currentTypeBindings) }
            ).map {
                LocalVariable(it.first, it.second)
            }
        } else {
            listOf(
                LocalVariable(
                    args0.IDENTIFIER(0)!!.text,
                    figureOutType(reporter, this.parser.ctx, this.ast, args0.type(0)!!, localTypeBindings = parser.currentTypeBindings)
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
                        val returnExpr = parseExpression(block.expression())
                        code.code.add(ReturnStatement(returnExpr, block.expression().position ?: block.position))
                        parser.localVariables.clear()
                        parser.localVariables.addAll(prevLocalVariables)
                    } else {
                        val prevLocalVariables = parser.localVariables.toList()
                        parser.localVariables.addAll(args)
                        val startIndex = parser.localVariables.size

                        block.statement().map { parser.statementParser.parseStatement(it) }.forEach(code.code::add)
                        val result = parseExpression(block.expression())
                        code.code.add(ReturnStatement(result, block.expression().position ?: block.position))

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
            block = block,
            position = ctx.position
        )
    }

    private fun parseEqExpr(ctx: ScratcherLangParser.EqExprContext): Expression {
        val op = if (ctx.EQ() != null) BinaryOperator.EQUAL else BinaryOperator.NOT_EQUAL
        val left = parseExpression(ctx.expression(0)!!)
        val right = parseExpression(ctx.expression(1)!!)
        val leftType = ExpressionTypes.getExpressionType(left)
        val rightType = ExpressionTypes.getExpressionType(right)

        if (leftType == PrimitiveType.Null || rightType == PrimitiveType.Null) {
            return BinaryExpression(left, op, right, ctx.position)
        }

        val stringBoxStruct = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" } ?: return BinaryExpression(left, op, right, ctx.position)
        val leftIsBox = isStringBox(leftType, stringBoxStruct)
        val rightIsBox = isStringBox(rightType, stringBoxStruct)

        return when {
            leftIsBox && rightIsBox -> boxVsBoxEquality(left, right, op, stringBoxStruct, ctx.position)
            leftIsBox && rightType == PrimitiveType.Str -> boxVsStrEquality(left, right, op, stringBoxStruct, ctx.position)
            rightIsBox && leftType == PrimitiveType.Str -> boxVsStrEquality(right, left, op, stringBoxStruct, ctx.position)
            else -> BinaryExpression(left, op, right, ctx.position)
        }
    }

    private fun isStringBox(type: Type, stringBoxStruct: Struct): Boolean {
        return type is NullableType && type.inner == stringBoxStruct.type
    }

    private fun boxVsStrEquality(boxExpr: Expression, strExpr: Expression, op: BinaryOperator, stringBoxStruct: Struct, position: Position?): Expression {
        return bindBoxOnce(boxExpr, position) { box ->
            val unboxed = unboxStringBox(box, stringBoxStruct, position)

            when (op) {
                BinaryOperator.EQUAL -> and(neq(box, NullExpression, position), eq(unboxed, strExpr, position), position)
                else -> or(eq(box, NullExpression, position), neq(unboxed, strExpr, position), position)
            }
        }
    }

    private fun boxVsBoxEquality(left: Expression, right: Expression, op: BinaryOperator, stringBoxStruct: Struct, position: Position?): Expression {
        return bindBoxTwice(left, right, position) { l, r ->
            val lIsNull = eq(l, NullExpression, position)
            val rIsNull = eq(r, NullExpression, position)
            val lNotNull = neq(l, NullExpression, position)
            val rNotNull = neq(r, NullExpression, position)
            val stringsEqual = eq(unboxStringBox(l, stringBoxStruct, position), unboxStringBox(r, stringBoxStruct, position), position)
            val stringsNotEqual = neq(unboxStringBox(l, stringBoxStruct, position), unboxStringBox(r, stringBoxStruct, position), position)

            when (op) {
                BinaryOperator.EQUAL -> or(
                    and(lIsNull, rIsNull, position),
                    and(lNotNull, and(rNotNull, stringsEqual, position), position),
                    position
                )
                else -> or(
                    and(lIsNull, rNotNull, position),
                    or(and(lNotNull, rIsNull, position), and(lNotNull, and(rNotNull, stringsNotEqual, position), position), position),
                    position
                )
            }
        }
    }

    private fun bindBoxOnce(boxExpr: Expression, position: Position? = null, use: (Expression) -> Expression): Expression {
        if (boxExpr.simple) return use(boxExpr)

        val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(boxExpr))
        val targetPos = position ?: boxExpr.position
        return StatementExpression(listOf(VariableStatement(boxExpr, temp, targetPos)), use(LocalVariableExpression(temp, position)), position)
    }

    private fun bindBoxTwice(left: Expression, right: Expression, position: Position? = null, use: (Expression, Expression) -> Expression): Expression {
        val stmts = mutableListOf<Statement>()
        val leftRef = if (left.simple) {
            left
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(left))
            stmts.add(VariableStatement(left, temp, position ?: left.position))
            LocalVariableExpression(temp, position)
        }
        val rightRef = if (right.simple) {
            right
        } else {
            val temp = LocalVariable("eq@box@${getUniqueName()}", ExpressionTypes.getExpressionType(right))
            stmts.add(VariableStatement(right, temp, position ?: right.position))
            LocalVariableExpression(temp, position)
        }
        val body = use(leftRef, rightRef)

        return if (stmts.isEmpty()) body else StatementExpression(stmts, body, position)
    }

    private fun unboxStringBox(expr: Expression, stringBoxStruct: Struct, position: Position? = expr.position): Expression {
        return MemberExpression(expr, stringBoxStruct.parameters.first { it.name == "str" }, stringBoxStruct, position)
    }

    private fun eq(left: Expression, right: Expression, position: Position? = null): Expression = BinaryExpression(left, BinaryOperator.EQUAL, right, position)
    private fun neq(left: Expression, right: Expression, position: Position? = null): Expression = BinaryExpression(left, BinaryOperator.NOT_EQUAL, right, position)
    private fun and(left: Expression, right: Expression, position: Position? = null): Expression = BinaryExpression(left, BinaryOperator.AND, right, position)
    private fun or(left: Expression, right: Expression, position: Position? = null): Expression = BinaryExpression(left, BinaryOperator.OR, right, position)

    fun parseIfExpr(ctx: ScratcherLangParser.IfExpressionContext): List<WhenBranch> {
        val branch = mutableListOf<WhenBranch>()

        val thenCond = parseExpression(ctx.expression())
        val thenBlock = parseExprBlock(ctx.exprBlock(0)!!)
        branch.add(WhenBranch(
            thenCond, thenBlock, position = ctx.position
        ))
        if (ctx.ifExpression() == null) {
            val elseBlock = parseExprBlock(ctx.exprBlock(1)!!)
            branch.add(WhenBranch(
                BooleanLiteral(true, ctx.position), elseBlock, isElse = true, position = ctx.position
            ))
        } else {
            branch.addAll(parseIfExpr(ctx.ifExpression()!!))
            if (branch.filter { it.cond is BooleanLiteral && it.cond.value }.size > 1) {
                error(ctx.position!!, ast, "Duplicate else blocks")
            }
        }

        return branch
    }

    private fun parseExprBlock(block: ScratcherLangParser.ExprBlockContext): CodeBlock {
        val out = CodeBlock()

        if (block.LBRACE() == null) {
            val expr = parseExpression(block.expression())
            out.code.add(ExpressionStatement(expr, block.expression().position ?: block.position))
            return out
        }

        val prevLocalVariables = parser.localVariables.toList()
        val startIndex = parser.localVariables.size

        block.statement().map { parser.statementParser.parseStatement(it) }.forEach(out.code::add)
        val result = parseExpression(block.expression())
        out.code.add(ExpressionStatement(result, block.expression().position ?: block.position))

        out.localVariables.addAll(parser.localVariables.subList(startIndex, parser.localVariables.size))
        parser.localVariables.clear()
        parser.localVariables.addAll(prevLocalVariables)

        return out
    }

    fun parseWhenExpr(ctx: ScratcherLangParser.WhenExpressionContext): Expression {
        val subject = ctx.expression()?.let { parseExpression(it) }
        val subjectVar = subject?.let { LocalVariable("whenStatement@subject${getUniqueName()}", ExpressionTypes.getExpressionType(
            it)) }
        val subjectAssignment = subjectVar?.let { VariableStatement(subject, it, ctx.expression()?.position ?: ctx.position) }
        val entries = ctx.whenEntry()
        if (entries.isEmpty()) {
            return error(ctx.position!!, ast, "When expression must have at least one branch")
        }
        if (entries.count { it.whenCondition() is ScratcherLangParser.ElseCondContext } > 1) {
            return error(ctx.position!!, ast, "Duplicate ELSE condition")
        }
        for (i in entries.indices) {
            if (entries[i].whenCondition() is ScratcherLangParser.ElseCondContext && i != entries.lastIndex) {
                return error(ctx.position!!, ast, "ELSE must be the last branch in when expression")
            }
        }

        return WhenExpression(
            subjectAssignment,
            entries.map { entry ->
                val condition = entry.whenCondition()
                val smartCastInfo = if (condition is ScratcherLangParser.SmartCastConditionContext) {
                    if (subjectVar == null) {
                        return error(condition.position!!, ast, "Must have a subject to use pattern matching condition")
                    }
                    val subjectSealedType = subjectVar.type.asNonNull() as? SealedEnumType
                    val inferred = if (subjectSealedType != null) tryResolveIsAsFromLeft(subjectSealedType, condition.type()) else null
                    val (sealed, variant, tag) = inferred ?: run {
                        val targetType = figureOutType(reporter, parser.ctx, ast, condition.type(), localTypeBindings = parser.currentTypeBindings)
                        val variantType = targetType.asNonNull() as? SimpleType
                            ?: return error(condition.position!!, ast, "Pattern matching target must be a sealed enum variant")
                        findVariantInfo(variantType)
                            ?: return error(condition.position!!, ast, "Type ${condition.type().text} is not a sealed enum variant")
                    }
                    val effectiveTag = if (variant.parameters.isEmpty()) -tag - 1 else tag
                    Triple(sealed, variant, effectiveTag)
                } else null

                val cond = when(condition) {
                    is ScratcherLangParser.ExprConditionContext -> {
                        condition.expression().let {
                            if (subjectVar != null) {
                                val sealedCheck = tryResolveWhenBranchAsSealedCheck(subjectVar, it)
                                if (sealedCheck != null) return@let sealedCheck
                            }
                            val expr = parseExpression(it)
                            if (subjectVar != null) {
                                BinaryExpression(
                                    left = LocalVariableExpression(subjectVar, condition.position),
                                    right = expr,
                                    operator = BinaryOperator.EQUAL,
                                    position = condition.position
                                )
                            } else expr
                        }
                    }
                    is ScratcherLangParser.ElseCondContext -> {
                        BooleanLiteral(true, condition.position)
                    }
                    is ScratcherLangParser.SmartCastConditionContext -> {
                        val (sealed, variant, effectiveTag) = smartCastInfo!!
                        CheckSealedEnumTypeExpression(
                            expr = LocalVariableExpression(subjectVar!!, condition.position),
                            targetVariant = variant,
                            sealedEnum = sealed,
                            tag = effectiveTag,
                            position = condition.position
                        )
                    }
                    else -> throw NotImplementedException("No implementation found for ${entry.text}")
                }
                val isElse = condition is ScratcherLangParser.ElseCondContext
                val castedObj = if (condition is ScratcherLangParser.SmartCastConditionContext) {
                    LocalVariable(condition.IDENTIFIER().text, smartCastInfo!!.second.type)
                } else null

                val branchBlock = CodeBlock()
                val localVariablesSnapshot = parser.localVariables.toList()
                if (castedObj != null) {
                    val (sealed, variant, effectiveTag) = smartCastInfo!!
                    branchBlock.code.add(VariableStatement(SealedEnumCastExpression(
                        expr = LocalVariableExpression(subjectVar!!, condition.position),
                        targetVariant = variant,
                        sealedEnum = sealed,
                        tag = effectiveTag,
                        safe = false,
                        position = condition.position
                    ), castedObj, condition.position))

                    parser.localVariables.add(castedObj)
                }

                if (entry.expression() != null) {
                    val expr = parseExpression(entry.expression()!!)
                    branchBlock.code.add(ExpressionStatement(expr, entry.expression()?.position ?: entry.position))
                } else if (entry.codeBlock() != null) {
                    val block = entry.codeBlock()!!
                    block.block()?.let {
                        parser.statementParser.parseBlock(branchBlock, it)
                    }
                    block.statement()?.let {
                        branchBlock.code.add(parser.statementParser.parseStatement(it))
                    }
                }

                parser.localVariables.clear()
                parser.localVariables.addAll(localVariablesSnapshot)

                WhenBranch(
                    cond,
                    branchBlock,
                    isElse,
                    entry.position
                )
            },
            ctx.position
        )
    }

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext, expectedType: Type? = null): Expression {
        val sealedVariant = tryResolveSealedVariantConstruction(ctx, expectedType, null)
        if (sealedVariant != null) {
            val (sealed, variant) = sealedVariant
            if (variant.parameters.isNotEmpty()) {
                return error(ctx.position!!, ast, "Sealed enum variant ${sealed.name}.${variant.name.substringAfter(".")} requires arguments, use ${sealed.name}.${variant.name.substringAfter(".")}(...)")
            }
            if (sealed.sourceAST != parser.ast && sealed.private) {
                error(ctx.position!!, ast, "Sealed enum ${sealed.name} is private and cannot be accessed from ${parser.ast.simplePath}!")
            }
            return SealedEnumConstructionExpression(sealed, variant, emptyList(), ctx.position)
        }

        val enum = when (val leftExpr = ctx.expression()) {
            is ScratcherLangParser.IdExprContext -> {
                val name = leftExpr.text
                ast.enums.find { it.name == name }
                    ?: ast.flatImportNames.values.flatMap { it.enums }.find { it.name == name }
                    ?: ast.wildcardImportSources.flatMap { it.enums }.find { it.name == name }
            }
            is ScratcherLangParser.ScopeExprContext -> {
                val importName = leftExpr.IDENTIFIER(0)!!.text
                val enumName = leftExpr.IDENTIFIER(1)!!.text
                ast.imports[importName]?.enums?.find { it.name == enumName }?.also {
                    if (it.private && it.sourceAST != this.parser.ast) {
                        error(ctx.expression().position!!, ast, "Enum ${it.name} is private!")
                    }
                }
            }
            else -> null
        }

        if (enum != null) {
            val memberName = ctx.IDENTIFIER().text
            val ordinal = enum.values.indexOf(memberName)
            if (ordinal == -1) {
                return error(ctx.IDENTIFIER().position(), ast, "Enum ${enum.name} does not have value $memberName")
            }
            if (enum.private && enum.sourceAST != this.parser.ast) {
                error(ctx.IDENTIFIER().position(), ast, "Enum ${enum.name} is private!")
            }
            return EnumLiteral(enum, memberName, ordinal, ctx.position)
        }

        val structExpr = parseExpression(ctx.expression())
        val struct = ExpressionTypes.getExpressionType(structExpr).let { type ->
            val baseType = type.asNonNull() as? SimpleType
            baseType?.sourceAST?.structs?.find { it.type == baseType }?:
                return error(ctx.expression().position!!, ast, "$type is a primitive type(?), expected a struct, found $baseType")
        }
        val memberName = ctx.IDENTIFIER().text

        val member = struct.parameters.find { it.name == memberName }?:
            return error(ctx.expression().position!!, ast, "Struct ${struct.name} does not have $memberName")

        if (member.private && struct.sourceAST != this.parser.ast) {
            error(ctx.IDENTIFIER().position(), ast, "Struct ${struct.name}'s member \"${member.name}\" is private!")
        }

        return MemberExpression(structExpr, member, struct, ctx.position)
    }

    private fun parseScopeExpr(ctx: ScratcherLangParser.ScopeExprContext): Expression {
        val import = ctx.IDENTIFIER(0)!!.text
        val variable = ctx.IDENTIFIER(1)!!.text
        val importedAST = ast.imports[import]
            ?: return error(ctx.IDENTIFIER(0)!!.position(), ast, "Import \"$import\" not found for ${ctx.text}")

        importedAST.staticLists.find { it.name == variable }?.let {
            if (it.private && importedAST != ast) {
                error(ctx.position!!, ast, "Static list $variable is private and cannot be accessed from ${ast.simplePath}")
            }
            return StaticListExpression(it, ctx.position)
        }

        val tlVar = importedAST.variables.find { it.name == variable }
            ?: return error(ctx.IDENTIFIER(1)!!.position(), ast, "${ctx.text} not found")

        if (tlVar.private && importedAST != ast) {
            error(ctx.position!!, ast, "Variable ${ctx.text} is private and cannot be accessed from ${ast.simplePath}")
        }

        return VariableExpression(tlVar, importedAST, ctx.position)
    }

    private fun parseIdentifier(text: String, position: Position? = null): Expression {
        //either top level variable or local variable or parameter
        val variableFinding = parser.localVariables.find { it.name == text }
        if (variableFinding != null) {
            return LocalVariableExpression(variableFinding, position)
        }

        val parameterFinding = parser.currentFunction?.parameters?.find { it.name == text }
        if (parameterFinding != null) {
            return ParameterExpression(parameterFinding, position)
        }

        ast.staticLists.find { it.name == text }?.let { return StaticListExpression(it, position) }
        ast.variables.find { it.name == text }?.let { return VariableExpression(it, ast, position) }

        val flatSource = ast.flatImportNames[text]
        if (flatSource != null) {
            flatSource.staticLists.find { it.name == text }?.let {
                if (it.private && flatSource != ast) {
                    error(position!!, ast, "Static list ${it.name} is private and cannot be accessed from ${ast.simplePath}")
                }
                return StaticListExpression(it, position)
            }
            val importedVar = flatSource.variables.find { it.name == text }
            if (importedVar != null) {
                if (importedVar.private && flatSource != ast) {
                    error(position!!, ast, "Variable $text is private and cannot be accessed from ${ast.simplePath}")
                }
                return VariableExpression(importedVar, flatSource, position)
            }
        }

        for (wildcardAst in ast.wildcardImportSources) {
            val privateMatch = wildcardAst.staticLists.find { it.name == text && it.private }
            if (privateMatch != null && wildcardAst != ast) {
                error(position!!, ast, "Static list $text is private and cannot be accessed from ${ast.simplePath}")
            }
            wildcardAst.staticLists.find { it.name == text && !it.private }?.let {
                return StaticListExpression(it, position)
            }
        }

        for (wildcardAst in ast.wildcardImportSources) {
            val privateMatch = wildcardAst.variables.find { it.name == text && it.private }
            if (privateMatch != null && wildcardAst != ast) {
                error(position!!, ast, "Variable $text is private and cannot be accessed from ${ast.simplePath}")
            }
            val importedVar = wildcardAst.variables.find { it.name == text && !it.private }
            if (importedVar != null) return VariableExpression(importedVar, wildcardAst, position)
        }

        return error(position!!, ast, "Variable $text not found!")
    }

    private fun requireSealedVisible(sealed: SealedEnum) {
        if (sealed.private && sealed.sourceAST != ast) {
             error(currentPosition!!, ast, "Sealed enum ${sealed.name} is private and cannot be accessed from ${ast.simplePath}")
        }
    }

    private fun findSealedEnumByName(name: String): SealedEnum? {
        val base = name.substringBefore("@")
        ast.sealedEnums.find { it.name == name }?.let { requireSealedVisible(it); return it }
        ast.imports.values.flatMap { it.sealedEnums }.find { it.name == name }?.let { requireSealedVisible(it); return it }
        ast.flatImportNames.values.flatMap { it.sealedEnums }.find { it.name == name }?.let { requireSealedVisible(it); return it }
        ast.wildcardImportSources.flatMap { it.sealedEnums }.find { it.name == name }?.let { requireSealedVisible(it); return it }
        ast.sealedEnums.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.imports.values.flatMap { it.sealedEnums }.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.flatImportNames.values.flatMap { it.sealedEnums }.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.wildcardImportSources.flatMap { it.sealedEnums }.find { it.name == base }?.let { requireSealedVisible(it); return it }

        findSealedTemplateByName(base)?.let { requireSealedVisible(it); return it }
        return null
    }

    private fun findSealedTemplateByName(name: String): SealedEnum? {
        val base = name.substringBefore("@")
        ast.sealedEnumTemplates.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.flatImportNames.values.flatMap { it.sealedEnumTemplates }.find { it.name == base }?.let { requireSealedVisible(it); return it }
        ast.wildcardImportSources.flatMap { it.sealedEnumTemplates }.find { it.name == base }?.let { requireSealedVisible(it); return it }
        if (base != name) {
            ast.sealedEnumTemplates.find { it.name == name }?.let { requireSealedVisible(it); return it }
            ast.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == name }?.let { requireSealedVisible(it); return it }
            ast.flatImportNames.values.flatMap { it.sealedEnumTemplates }.find { it.name == name }?.let { requireSealedVisible(it); return it }
            ast.wildcardImportSources.flatMap { it.sealedEnumTemplates }.find { it.name == name }?.let { requireSealedVisible(it); return it }
        }
        return null
    }

    private fun findVariantInfo(variantType: SimpleType): Triple<SealedEnum, Struct, Int>? {
        val allSealed = ast.sealedEnums +
                ast.imports.values.flatMap { it.sealedEnums } +
                ast.flatImportNames.values.flatMap { it.sealedEnums } +
                ast.wildcardImportSources.flatMap { it.sealedEnums }
        for (sealed in allSealed) {
            for ((idx, variant) in sealed.types.withIndex()) {
                if (variant.type == variantType) {
                    requireSealedVisible(sealed)
                    return Triple(sealed, variant, idx)
                }
            }
        }
        return null
    }

    private fun tryResolveSealedVariantConstruction(memberCtx: ScratcherLangParser.MemberExprContext, expectedType: Type? = null, argTypes: List<Type>? = null): Pair<SealedEnum, Struct>? {
        val leftCtx = memberCtx.expression()
        val sealedBaseName: String
        val qualifiedSealed: SealedEnum?
        val qualifiedTemplate: SealedEnum?
        when (leftCtx) {
            is ScratcherLangParser.IdExprContext -> {
                if (resolvesAsVariable(leftCtx.text)) return null
                sealedBaseName = leftCtx.text.substringBefore("@").substringBefore("<")
                qualifiedSealed = null
                qualifiedTemplate = null
            }
            is ScratcherLangParser.ScopeExprContext -> {
                val importName = leftCtx.IDENTIFIER(0)?.text ?: return null
                val sealedName = leftCtx.IDENTIFIER(1)?.text ?: return null
                val imported = ast.imports[importName] ?: return null
                sealedBaseName = sealedName.substringBefore("@").substringBefore("<")
                qualifiedSealed = imported.sealedEnums.find { it.name == sealedBaseName }
                    ?.also { requireSealedVisible(it) }
                qualifiedTemplate = imported.sealedEnumTemplates.find { it.name == sealedBaseName }
                    ?.also { requireSealedVisible(it) }
            }
            else -> return null
        }
        val variantShort = memberCtx.IDENTIFIER().text

        val concreteSealed = if (qualifiedSealed != null || qualifiedTemplate != null) {
            qualifiedSealed
        } else {
            findSealedEnumByName(sealedBaseName)
        }
        if (concreteSealed != null && concreteSealed.typeParameters.isEmpty()) {
            val variant = concreteSealed.types.find { it.name.substringAfter(".") == variantShort } ?: return null
            return Pair(concreteSealed, variant)
        }

        val template = qualifiedTemplate ?: (if (qualifiedSealed == null) findSealedTemplateByName(sealedBaseName) else null) ?: run {
            val exact = qualifiedSealed ?: findSealedEnumByName(sealedBaseName) ?: return null
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
            requireSealedVisible(template)
            val sealedType = reporter.catching {
                Generics.resolveGenericSealedEnum(reporter, parser.ctx, ast, ast, template.name, typeArgs, ast)
            } as? SealedEnumType ?: return null

            val concreteSealedEnum = (ast.sealedEnums.find { it.type == sealedType }
                ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == sealedType }
                ?: findSealedEnumByName(sealedType.name)
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
            ?: ast.sealedEnums.find { it.type == subjectType }?.also { requireSealedVisible(it) }
            ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == subjectType }?.also { requireSealedVisible(it) }
            ?: return null
        requireSealedVisible(sealed)
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
        return CheckSealedEnumTypeExpression(LocalVariableExpression(subjectVar, condCtx.position), variant, sealed, effectiveTag, condCtx.position)
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
                return CheckSealedEnumTypeExpression(left, variant, sealed, effectiveTag, ctx.position)
            }
        }
        val targetType = figureOutType(reporter, parser.ctx, ast, ctx.type(), localTypeBindings = parser.currentTypeBindings)
        val variantType = targetType.asNonNull() as? SimpleType
            ?: return error(ctx.position!!, ast, "IS check target must be a sealed enum variant, got $targetType")
        val info = findVariantInfo(variantType)
            ?: return error(ctx.position!!, ast, "Type ${ctx.type().text} is not a sealed enum variant")

        val (sealed, variant, tag) = info
        val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
        val leftNonNull = leftType.asNonNull()
        if (leftNonNull != sealed.type) {
            if (leftType !is NullableType || leftType.inner != sealed.type) {
                error(ctx.position!!, ast, "IS check left type $leftType is not ${sealed.type}")
            }
        }
        return CheckSealedEnumTypeExpression(left, variant, sealed, effectiveTag, ctx.position)
    }

    private fun parseCastExpr(ctx: ScratcherLangParser.CastSealedEnumExprContext): Expression {
        val safe = ctx.NULLABLE() != null
        val left = parseExpression(ctx.expression())
        val leftType = ExpressionTypes.getExpressionType(left)
        val leftSealedType = leftType.asNonNull() as? SealedEnumType
        if (leftSealedType != null) {
            val inferred = tryResolveIsAsFromLeft(leftSealedType, ctx.type())
            if (inferred != null) {
                val (sealed, variant, tag) = inferred
                val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
                return SealedEnumCastExpression(left, variant, sealed, effectiveTag, safe, ctx.position)
            }
        }
        val targetType = figureOutType(reporter, parser.ctx, ast, ctx.type(), localTypeBindings = parser.currentTypeBindings)
        val variantType = targetType.asNonNull() as? SimpleType
            ?: return error(ctx.position!!, ast, "AS cast target must be a sealed enum variant, got $targetType")
        val info = findVariantInfo(variantType)
            ?: return error(ctx.position!!, ast, "Type ${ctx.type().text} is not a sealed enum variant")

        val (sealed, variant, tag) = info
        val effectiveTag = if (variant.parameters.isEmpty()) -tag-1 else tag
        val leftNonNull = leftType.asNonNull()
        if (leftNonNull != sealed.type) {
            if (leftType !is NullableType || leftType.inner != sealed.type) {
                error(ctx.position!!, ast, "AS cast left type $leftType is not ${sealed.type}")
            }
        }
        return SealedEnumCastExpression(left, variant, sealed, effectiveTag, safe, ctx.position)
    }

    private fun tryResolveIsAsFromLeft(leftSealedType: SealedEnumType, targetTypeCtx: ScratcherLangParser.TypeContext): Triple<SealedEnum, Struct, Int>? {
        val pathCtx = targetTypeCtx as? ScratcherLangParser.PathTypeContext ?: return null
        val ids = pathCtx.typePath().IDENTIFIER()
        val hasArgs = pathCtx.type().isNotEmpty()
        val sealed = ast.sealedEnums.find { it.type == leftSealedType }?.also { requireSealedVisible(it) }
            ?: parser.ctx.asts.values.flatMap { it.sealedEnums }.find { it.type == leftSealedType }?.also { requireSealedVisible(it) }
            ?: findSealedEnumByName(leftSealedType.name) ?: return null
        requireSealedVisible(sealed)
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
                val explicitType = figureOutType(reporter, parser.ctx, ast, targetTypeCtx, localTypeBindings = parser.currentTypeBindings)
                val variantType = explicitType.asNonNull() as? SimpleType ?: return null
                findVariantInfo(variantType)
            } catch (_: Exception) { null }
        }
        val idx = sealed.types.indexOfFirst { it.name.substringAfter(".") == variantShort }
        if (idx == -1) return null
        val variant = sealed.types[idx]
        return Triple(sealed, variant, idx)
    }

    fun buildBinaryExpression(left: Expression, op: BinaryOperator, right: Expression, position: Position? = null): Expression {
        val opName = when (op) {
            BinaryOperator.ADD -> "plus"
            BinaryOperator.SUBTRACT -> "minus"
            BinaryOperator.MULTIPLY -> "times"
            BinaryOperator.DIVIDE -> "div"
            BinaryOperator.MODULO -> "mod"
            else -> null
        }

        if (opName != null) {
            val leftType = ExpressionTypes.getExpressionType(left)
            val rightType = ExpressionTypes.getExpressionType(right)
            val leftIsNum = leftType == PrimitiveType.Integer || leftType == PrimitiveType.Float
            val rightIsNum = rightType == PrimitiveType.Integer || rightType == PrimitiveType.Float

            if (!leftIsNum || !rightIsNum) {
                parser.functionResolver.resolveOperatorFunction(left, opName, right)?.let {
                    return it
                }
            }
        }

        return BinaryExpression(left, op, right, position)
    }

    fun error(position: Position, ast: ASTFile, text: String): ErrorExpression {
        return this.reporter.error(position, ast, text)
    }
}