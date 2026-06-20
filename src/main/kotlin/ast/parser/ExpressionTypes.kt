package dev.betterclient.ast.parser

import dev.betterclient.ast.*

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
                    leftType == Type.str || rightType == Type.str -> Type.str
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