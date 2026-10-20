package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName

object StringBoxing {
    val stringBoxStruct: Struct
        get() = CompilationContext.std.compilerLib.structs.find { it.name == "StringBox" }!!

    fun autoConvert(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        return boxIfNeeded(expr, expectedType)
    }

    fun boxIfNeeded(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(expr)

        if (expectedType.asNonNull().toString() == "str" && expectedType is NullableType && exprType == PrimitiveType.Str) {
            return CallExpression(stringBoxStruct.allocFunc, listOf(expr), expr.position)
        }
        return expr
    }

    fun unboxIfNeeded(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(expr)

        if (expectedType == PrimitiveType.Str &&
            exprType is NullableType &&
            exprType.asNonNull().toString() == "str") {

            val temp = LocalVariable("unbox@${getUniqueName()}", exprType)
            val pos = expr.position
            val boxRef = LocalVariableExpression(temp, pos)

            return WhenExpression(
                subject = VariableStatement(expr, temp, pos),
                branches = listOf(
                    WhenBranch(
                        cond = BinaryExpression(boxRef, BinaryOperator.EQUAL, NullExpression, position = pos),
                        block = CodeBlock().also {
                            it.code.add(ExpressionStatement(StringLiteral("null", position = pos), position = pos))
                        }, position = pos
                    ),
                    WhenBranch(
                        cond = BooleanLiteral(true, position = pos),
                        block = CodeBlock().also {
                            it.code.add(ExpressionStatement(NonNullAssertExpression(boxRef, position = pos), position = pos))
                        },
                        isElse = true,
                        position = pos
                    )
                ),
                pos
            )
        }

        return expr
    }

    fun init() {
        CompilationContext.std.compilerLib.structs.add(Struct(
            name = "StringBox",
            parameters = mutableListOf(Parameter("str", PrimitiveType.Str, false)),
            sourceAST = CompilationContext.std.compilerLib,
            private = false
        ))
    }
}