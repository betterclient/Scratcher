package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.except.UnreachableException

object ExpressionTypes {
    fun getExpressionType(expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(expr)
            is CallExpression -> expr.func.returnType
            is BooleanLiteral -> Type.bool
            is FloatLiteral -> Type.float
            is IntLiteral -> Type.int
            is StringLiteral -> Type.str
            is LocalVariableExpression -> expr.variable.type
            is MemberExpression -> expr.member.type
            is UnaryExpression -> getExpressionType(expr.expression)
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is ConcatExpression -> Type.str
            is NullExpression -> Type.nullType
            is NonNullAssertExpression -> getExpressionType(expr.expression).asNonNull()
            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun figureOutBinaryExprReturn(expr: BinaryExpression): Type {
        val leftType = getExpressionType(expr.left)
        val rightType = getExpressionType(expr.right)

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