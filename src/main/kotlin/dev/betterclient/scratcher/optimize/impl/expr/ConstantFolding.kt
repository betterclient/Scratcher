package dev.betterclient.scratcher.optimize.impl.expr

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object ConstantFolding : Optimization("Constant Folding") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
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
                        BinaryOperator.STRICT_EQUAL -> foldStrictEqual(left, right, false)
                        BinaryOperator.STRICT_NOT_EQUAL -> foldStrictEqual(left, right, true)
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
                fun Literal.toStr(): String? = when(this) {
                    is BooleanLiteral -> this.value.toString()
                    is CharLiteral -> this.value.toString()
                    is FloatLiteral -> this.value.toString()
                    is IntLiteral -> this.value.toString()
                    is StringLiteral -> this.value
                    NullExpression -> "null"
                    is EnumLiteral -> null
                    is FunctionLiteral -> null
                    is TypeLiteral -> null
                }

                val leftAsStr = (left as? Literal)?.toStr()
                val rightAsStr = (right as? Literal)?.toStr()

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

            override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
                if (statements.isEmpty()) {
                    modified = true
                    return expression
                }
                if (expression is StatementExpression) {
                    modified = true
                    return StatementExpression(statements + expression.statements, expression.expression)
                }
                return super.visitStatementExpression(statements, expression)
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
                left.value.equals(right.value, ignoreCase = true)
            }
            left is StringLiteral && right is CharLiteral -> {
                left.value.equals(right.value.toString(), ignoreCase = true)
            }
            left is CharLiteral && right is CharLiteral -> {
                left.value.equals(right.value, ignoreCase = true)
            }
            left is CharLiteral && right is StringLiteral -> {
                left.value.toString().equals(right.value, ignoreCase = true)
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

    private fun foldStrictEqual(left: Literal, right: Literal, negate: Boolean): BooleanLiteral {
        return BooleanLiteral(
            when (left) {
                is StringLiteral if right is StringLiteral -> {
                    left.value == right.value
                }
                is StringLiteral if right is CharLiteral -> {
                    left.value == right.value.toString()
                }
                is CharLiteral if right is CharLiteral -> {
                    left.value == right.value
                }
                is CharLiteral if right is StringLiteral -> {
                    left.value.toString() == right.value
                }
                else -> throw UnreachableException()
            }.let {
                if (negate) !it else it
            }
        )
    }
}