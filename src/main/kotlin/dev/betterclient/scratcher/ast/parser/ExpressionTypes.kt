package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.std.lib.ListLib

object ExpressionTypes {
    fun getExpressionType(context: CompilationContext, expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(context, expr)
            is CallExpression -> {
                if (expr.func.sourceAST.path == "list") {
                    ListLib.getActualReturnType(context, expr) { getExpressionType(context, it) }
                } else expr.func.returnType
            }
            is BooleanLiteral -> Type.bool
            is FloatLiteral -> Type.float
            is IntLiteral -> Type.int
            is StringLiteral -> Type.str
            is LocalVariableExpression -> expr.variable.type
            is MemberExpression -> expr.member.type
            is UnaryExpression -> getExpressionType(context, expr.expression)
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is EnumLiteral -> expr.enum.type
            is ConcatExpression -> Type.str
            is NullExpression -> Type.nullType
            is NonNullAssertExpression -> getExpressionType(context, expr.expression).asNonNull()
            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun figureOutBinaryExprReturn(context: CompilationContext, expr: BinaryExpression): Type {
        val leftType = getExpressionType(context, expr.left)
        val rightType = getExpressionType(context, expr.right)

        return when (expr.operator) {
            BinaryOperator.ADD, BinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE,
            BinaryOperator.MODULO -> {
                when {
                    leftType == Type.float || rightType == Type.float -> Type.float
                    else -> Type.int
                }
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL,
            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.AND,
            BinaryOperator.OR -> Type.bool
        }
    }
}