package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.std.StandardLibASTGenerator

object StringBoxing {
    val stringBoxStruct: Struct
        get() = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" }!!

    fun autoConvert(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        return unboxIfNeeded(expr, expectedType).let {
            boxIfNeeded(it, expectedType)
        }
    }

    fun boxIfNeeded(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(expr)

        if (expectedType.asNonNull().toString() == "str" && expectedType is NullableType && exprType == PrimitiveType.Str) {
            return CallExpression(stringBoxStruct.allocFunc, listOf(expr))
        }
        return expr
    }

    fun unboxIfNeeded(expr: Expression, expectedType: Type?): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(expr)

        if (expectedType == PrimitiveType.Str &&
            exprType is NullableType &&
            exprType.asNonNull().toString() == "str") {

            val strField = stringBoxStruct.parameters.first { it.name == "str" }
            val temp = LocalVariable("unbox@${getUniqueName()}", exprType)
            val boxRef = LocalVariableExpression(temp)

            return WhenExpression(
                subject = VariableStatement(expr, temp),
                branches = listOf(
                    WhenBranch(
                        cond = BinaryExpression(boxRef, BinaryOperator.EQUAL, NullExpression),
                        block = CodeBlock().also {
                            it.code.add(ExpressionStatement(StringLiteral("null")))
                        }
                    ),
                    WhenBranch(
                        cond = BooleanLiteral(true),
                        block = CodeBlock().also {
                            it.code.add(ExpressionStatement(MemberExpression(boxRef, strField, stringBoxStruct)))
                        },
                        isElse = true
                    )
                )
            )
        }

        return expr
    }

    fun init() {
        StandardLibASTGenerator.compilerLib.structs.add(Struct(
            name = "StringBox",
            parameters = mutableListOf(Parameter("str", PrimitiveType.Str)),
            sourceAST = StandardLibASTGenerator.compilerLib
        ))
    }
}