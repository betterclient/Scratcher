package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.Literal
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object ConstantFolding : Optimization("Constant Folding") {
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitBinaryExpression(
                left: Expression,
                right: Expression,
                operator: BinaryOperator
            ): Expression {
                if (left is Literal && right is Literal) {
                    modified = true
                    return when(operator) {
                        BinaryOperator.MULTIPLY -> foldConstantSimpleMath(left, right, BinaryOperator.MULTIPLY)
                        BinaryOperator.DIVIDE -> foldConstantSimpleMath(left, right, BinaryOperator.DIVIDE)
                        BinaryOperator.MODULO -> foldConstantSimpleMath(left, right, BinaryOperator.MODULO)
                        BinaryOperator.ADD -> foldConstantSimpleMath(left, right, BinaryOperator.ADD)
                        BinaryOperator.SUBTRACT -> foldConstantSimpleMath(left, right, BinaryOperator.SUBTRACT)
                        BinaryOperator.LESS_THAN -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.LESS_THAN)
                        BinaryOperator.GREATER_THAN -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.GREATER_THAN)
                        BinaryOperator.LESS_EQUAL -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.LESS_EQUAL)
                        BinaryOperator.GREATER_EQUAL -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.GREATER_EQUAL)
                        BinaryOperator.EQUAL -> foldEqualNotEqual(left, right, BinaryOperator.EQUAL)
                        BinaryOperator.NOT_EQUAL -> foldEqualNotEqual(left, right, BinaryOperator.NOT_EQUAL)
                        BinaryOperator.AND -> foldConstantBoolComparisonOperation(left, right, BinaryOperator.AND)
                        BinaryOperator.OR -> foldConstantBoolComparisonOperation(left, right, BinaryOperator.OR)
                    }
                }

                return super.visitBinaryExpression(left, right, operator)
            }

            override fun visitUnaryExpression(operator: UnaryOperator, expression: Expression): Expression {
                if (expression is Literal) {
                    return when (operator) {
                        UnaryOperator.PLUS -> expression
                        UnaryOperator.MINUS -> if (expression is FloatLiteral) {
                            FloatLiteral(0.toBigDecimal() - expression.value)
                        } else {
                            IntLiteral(0.toBigInteger() - (expression as IntLiteral).value)
                        }
                        UnaryOperator.NOT -> BooleanLiteral((expression as BooleanLiteral).value.not())
                    }
                }

                return super.visitUnaryExpression(operator, expression)
            }

            override fun visitConcatExpression(left: Expression, right: Expression): Expression {
                val leftAsStr = (left as? StringLiteral)?.value
                val rightAsStr = (right as? StringLiteral)?.value

                if (leftAsStr != null && rightAsStr != null) {
                    return StringLiteral(leftAsStr + rightAsStr)
                } else if (leftAsStr == "" || rightAsStr == "") {
                    return if (leftAsStr != null) {
                        right
                    } else {
                        left
                    }
                }

                return super.visitConcatExpression(left, right)
            }
        })
        return modified
    }

    private fun foldEqualNotEqual(left: Expression, right: Expression, operator: BinaryOperator): BooleanLiteral {
        val isEqual = when {
            left is BooleanLiteral && right is BooleanLiteral -> {
                left.value == right.value
            }
            (left is IntLiteral || left is FloatLiteral) && (right is IntLiteral || right is FloatLiteral) -> {
                val leftValue = (left as? IntLiteral)?.value?.toBigDecimal() ?: (left as FloatLiteral).value
                val rightValue = (right as? IntLiteral)?.value?.toBigDecimal() ?: (right as FloatLiteral).value
                leftValue.compareTo(rightValue) == 0
            }
            left is StringLiteral && right is StringLiteral -> {
                left.value == right.value
            }
            else -> {
                left == right
            }
        }

        return BooleanLiteral(
            when (operator) {
                BinaryOperator.EQUAL -> isEqual
                BinaryOperator.NOT_EQUAL -> !isEqual
                else -> throw UnreachableException()
            }
        )
    }

    private fun foldConstantNumberComparisonOperation(left: Literal, right: Literal, operator: BinaryOperator): BooleanLiteral {
        val leftValue = (left as? IntLiteral)?.value?.toBigDecimal()?: (left as FloatLiteral).value
        val rightValue = (right as? IntLiteral)?.value?.toBigDecimal()?: (right as FloatLiteral).value
        //^^ if these fail the typechecker is to blame, not us

        return BooleanLiteral(
            when (operator) {
                BinaryOperator.LESS_THAN -> leftValue < rightValue
                BinaryOperator.GREATER_THAN -> leftValue > rightValue
                BinaryOperator.LESS_EQUAL -> leftValue <= rightValue
                BinaryOperator.GREATER_EQUAL -> leftValue >= rightValue
                else -> throw UnreachableException()
            }
        )
    }

    private fun foldConstantSimpleMath(left: Literal, right: Literal, operator: BinaryOperator): Literal {
        val returnFloat = left is FloatLiteral || right is FloatLiteral
        return if (returnFloat) {
            val leftValue = (left as? IntLiteral)?.value?.toBigDecimal()?: (left as FloatLiteral).value
            val rightValue = (right as? IntLiteral)?.value?.toBigDecimal()?: (right as FloatLiteral).value
            when (operator) {
                BinaryOperator.MULTIPLY -> FloatLiteral(leftValue * rightValue)
                BinaryOperator.DIVIDE -> FloatLiteral(leftValue / rightValue)
                BinaryOperator.MODULO -> FloatLiteral(leftValue % rightValue)
                BinaryOperator.ADD -> FloatLiteral(leftValue + rightValue)
                BinaryOperator.SUBTRACT -> FloatLiteral(leftValue - rightValue)
                else -> throw UnreachableException()
            }
        } else {
            val leftValue = (left as IntLiteral).value
            val rightValue = (right as IntLiteral).value
            when (operator) {
                BinaryOperator.MULTIPLY -> IntLiteral(leftValue * rightValue)
                BinaryOperator.DIVIDE -> IntLiteral(leftValue / rightValue)
                BinaryOperator.MODULO -> IntLiteral(leftValue % rightValue)
                BinaryOperator.ADD -> IntLiteral(leftValue + rightValue)
                BinaryOperator.SUBTRACT -> IntLiteral(leftValue - rightValue)
                else -> throw UnreachableException()
            }
        }
    }

    private fun foldConstantBoolComparisonOperation(left: Literal, right: Literal, operator: BinaryOperator): BooleanLiteral {
        val leftValue = (left as BooleanLiteral).value
        val rightValue = (right as BooleanLiteral).value

        return BooleanLiteral(when (operator) {
            BinaryOperator.AND -> leftValue && rightValue
            BinaryOperator.OR -> leftValue || rightValue
            else -> throw UnreachableException()
        })
    }
}