package dev.betterclient.scratcher.optimize.impl.expr

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.floor

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
                operator: BinaryOperator,
                position: Position?
            ): Expression {
                if (left is Literal && right is Literal) {
                    modified = true
                    return when(operator) {
                        BinaryOperator.MULTIPLY -> foldConstantSimpleMath(left, right, BinaryOperator.MULTIPLY, position)
                        BinaryOperator.DIVIDE -> foldConstantSimpleMath(left, right, BinaryOperator.DIVIDE, position)
                        BinaryOperator.MODULO -> foldConstantSimpleMath(left, right, BinaryOperator.MODULO, position)
                        BinaryOperator.ADD -> foldConstantSimpleMath(left, right, BinaryOperator.ADD, position)
                        BinaryOperator.SUBTRACT -> foldConstantSimpleMath(left, right, BinaryOperator.SUBTRACT, position)
                        BinaryOperator.LESS_THAN -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.LESS_THAN, position)
                        BinaryOperator.GREATER_THAN -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.GREATER_THAN, position)
                        BinaryOperator.LESS_EQUAL -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.LESS_EQUAL, position)
                        BinaryOperator.GREATER_EQUAL -> foldConstantNumberComparisonOperation(left, right, BinaryOperator.GREATER_EQUAL, position)
                        BinaryOperator.EQUAL -> foldEqualNotEqual(left, right, BinaryOperator.EQUAL, position)
                        BinaryOperator.NOT_EQUAL -> foldEqualNotEqual(left, right, BinaryOperator.NOT_EQUAL, position)
                        BinaryOperator.STRICT_EQUAL -> foldStrictEqual(left, right, false, position)
                        BinaryOperator.STRICT_NOT_EQUAL -> foldStrictEqual(left, right, true, position)
                        BinaryOperator.AND -> foldConstantBoolComparisonOperation(left, right, BinaryOperator.AND, position)
                        BinaryOperator.OR -> foldConstantBoolComparisonOperation(left, right, BinaryOperator.OR, position)
                    }
                }

                return super.visitBinaryExpression(left, right, operator, position)
            }

            override fun visitUnaryExpression(
                operator: UnaryOperator,
                expression: Expression,
                position: Position?
            ): Expression {
                if (expression is Literal) {
                    return when (operator) {
                        UnaryOperator.PLUS -> expression
                        UnaryOperator.MINUS -> if (expression is FloatLiteral) {
                            FloatLiteral(0.toBigDecimal() - expression.value, position)
                        } else {
                            IntLiteral(0.toBigInteger() - (expression as IntLiteral).value, position)
                        }
                        UnaryOperator.NOT -> BooleanLiteral((expression as BooleanLiteral).value.not(), position)
                    }
                }

                return super.visitUnaryExpression(operator, expression, position)
            }

            override fun visitConcatExpression(left: Expression, right: Expression, position: Position?): Expression {
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
                    return StringLiteral(leftAsStr + rightAsStr, position)
                } else if (leftAsStr == "" || rightAsStr == "") {
                    return if (leftAsStr != null) {
                        right
                    } else {
                        left
                    }
                }

                return super.visitConcatExpression(left, right, position)
            }

            override fun visitStatementExpression(
                statements: List<Statement>,
                expression: Expression,
                position: Position?
            ): Expression {
                if (statements.isEmpty()) {
                    modified = true
                    return expression
                }
                if (expression is StatementExpression) {
                    modified = true
                    return StatementExpression(statements + expression.statements, expression.expression, position)
                }
                return super.visitStatementExpression(statements, expression, position)
            }
        })
        return modified
    }

    private fun foldEqualNotEqual(left: Expression, right: Expression, operator: BinaryOperator, position: Position?): BooleanLiteral {
        val isEqual = when {
            left is BooleanLiteral && right is BooleanLiteral -> {
                left.value == right.value
            }
            left is StringLiteral && right is StringLiteral -> {
                val d1 = left.value.trim().toDoubleOrNull()
                val d2 = right.value.trim().toDoubleOrNull()
                if (d1 != null && d2 != null) {
                    d1 == d2
                } else {
                    left.value.equals(right.value, ignoreCase = true)
                }
            }
            (left is StringLiteral || left is CharLiteral) && (right is IntLiteral || right is FloatLiteral) -> {
                val s = if (left is StringLiteral) left.value else (left as CharLiteral).value.toString()
                val num = s.trim().toDoubleOrNull()
                val rightNum = (right as? IntLiteral)?.value?.toDouble() ?: (right as FloatLiteral).value.toDouble()
                num != null && num == rightNum
            }
            (left is IntLiteral || left is FloatLiteral) && (right is StringLiteral || right is CharLiteral) -> {
                val s = if (right is StringLiteral) right.value else (right as CharLiteral).value.toString()
                val num = s.trim().toDoubleOrNull()
                val leftNum = (left as? IntLiteral)?.value?.toDouble() ?: (left as FloatLiteral).value.toDouble()
                num != null && num == leftNum
            }
            (left is IntLiteral || left is FloatLiteral) && (right is IntLiteral || right is FloatLiteral) -> {
                val leftValue = (left as? IntLiteral)?.value?.toBigDecimal() ?: (left as FloatLiteral).value
                val rightValue = (right as? IntLiteral)?.value?.toBigDecimal() ?: (right as FloatLiteral).value
                leftValue.compareTo(rightValue) == 0
            }
            left is StringLiteral && right is CharLiteral -> {
                left.value.equals(right.value.toString(), ignoreCase = true)
            }
            left is CharLiteral && right is CharLiteral -> {
                left.value.equals(right.value, ignoreCase = true)
            }
            else -> {
                (left as? Literal)?.withoutPosition() == (right as? Literal)?.withoutPosition()
            }
        }

        return BooleanLiteral(
            when (operator) {
                BinaryOperator.EQUAL -> isEqual
                BinaryOperator.NOT_EQUAL -> !isEqual
                else -> throw UnreachableException()
            },
            position
        )
    }

    private fun Literal.withoutPosition(): Literal = when (this) {
        is IntLiteral -> copy(position = null)
        is FloatLiteral -> copy(position = null)
        is BooleanLiteral -> copy(position = null)
        is StringLiteral -> copy(position = null)
        is CharLiteral -> copy(position = null)
        is EnumLiteral -> copy(position = null)
        is FunctionLiteral -> copy(position = null)
        is TypeLiteral -> copy(position = null)
        NullExpression -> NullExpression
    }

    private fun foldConstantNumberComparisonOperation(left: Literal, right: Literal, operator: BinaryOperator, position: Position?): BooleanLiteral {
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
            },
            position
        )
    }

    private fun foldConstantSimpleMath(left: Literal, right: Literal, operator: BinaryOperator, position: Position?): Expression {
        val returnFloat = left is FloatLiteral || right is FloatLiteral || operator == BinaryOperator.DIVIDE
        return if (returnFloat) {
            val d1 = (left as? IntLiteral)?.value?.toDouble() ?: (left as FloatLiteral).value.toDouble()
            val d2 = (right as? IntLiteral)?.value?.toDouble() ?: (right as FloatLiteral).value.toDouble()
            if (operator == BinaryOperator.DIVIDE && d2 == 0.0) return BinaryExpression(left, operator, right, position)

            val res = when (operator) {
                BinaryOperator.MULTIPLY -> d1 * d2
                BinaryOperator.DIVIDE -> d1 / d2
                BinaryOperator.MODULO -> d1 - d2 * floor(d1 / d2)
                BinaryOperator.ADD -> d1 + d2
                BinaryOperator.SUBTRACT -> d1 - d2
                else -> throw UnreachableException()
            }

            if (!res.isFinite()) return BinaryExpression(left, operator, right, position)
            FloatLiteral(BigDecimal.valueOf(res), position)
        } else {
            val l = (left as IntLiteral).value
            val r = (right as IntLiteral).value
            if ((operator == BinaryOperator.MODULO) && r == BigInteger.ZERO) {
                return BinaryExpression(left, operator, right, position)
            }
            when (operator) {
                BinaryOperator.MULTIPLY -> IntLiteral(l * r, position)
                BinaryOperator.ADD -> IntLiteral(l + r, position)
                BinaryOperator.SUBTRACT -> IntLiteral(l - r, position)
                BinaryOperator.MODULO -> {
                    val rem = ((l % r) + r) % r
                    IntLiteral(if (rem != BigInteger.ZERO && rem.signum() != r.signum()) rem + r else rem, position)
                }
                else -> throw UnreachableException()
            }
        }
    }

    private fun foldConstantBoolComparisonOperation(left: Literal, right: Literal, operator: BinaryOperator, position: Position?): BooleanLiteral {
        val leftValue = (left as BooleanLiteral).value
        val rightValue = (right as BooleanLiteral).value

        return BooleanLiteral(
            when (operator) {
                BinaryOperator.AND -> leftValue && rightValue
                BinaryOperator.OR -> leftValue || rightValue
                else -> throw UnreachableException()
            },
            position
        )
    }

    private fun foldStrictEqual(left: Literal, right: Literal, negate: Boolean, position: Position?): BooleanLiteral {
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
            },
            position
        )
    }
}