package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.std.StandardLibASTGenerator

object StringBoxing {
    fun autoConvert(expr: Expression, expectedType: Type?, context: CompilationContext): Expression {
        if (expectedType == null) return expr

        return unboxIfNeeded(expr, expectedType, context).let {
            boxIfNeeded(it, expectedType, context)
        }
    }

    fun boxIfNeeded(expr: Expression, expectedType: Type?, context: CompilationContext): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(context, expr)
        val stringBoxStruct = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" }!!

        if (expectedType.asNonNull().toString() == "str" && expectedType is NullableType && exprType == PrimitiveType.Str) {
            return CallExpression(stringBoxStruct.allocFunc, listOf(expr))
        }
        return expr
    }

    fun unboxIfNeeded(expr: Expression, expectedType: Type?, context: CompilationContext): Expression {
        if (expectedType == null) return expr

        val exprType = ExpressionTypes.getExpressionType(context, expr)
        val stringBoxStruct = StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" }!!

        if (expectedType == PrimitiveType.Str &&
            exprType is NullableType &&
            exprType.asNonNull().toString() == "str") {

            val strField = stringBoxStruct.parameters.first { it.name == "str" }
            return MemberExpression(expr, strField, stringBoxStruct)
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