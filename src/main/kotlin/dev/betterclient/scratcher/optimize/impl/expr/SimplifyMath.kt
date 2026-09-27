package dev.betterclient.scratcher.optimize.impl.expr

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.simple
import java.math.BigInteger

object SimplifyMath : Optimization("Simplify math") {
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
                val simplified = simplify(left, right, operator)
                if (simplified != null) {
                    modified = true
                    return simplified
                }
                return super.visitBinaryExpression(left, right, operator)
            }
        })
        return modified
    }

    private fun simplify(left: Expression, right: Expression, operator: BinaryOperator): Expression? {
        val leftZero = left is IntLiteral && left.value == BigInteger.ZERO
        val rightZero = right is IntLiteral && right.value == BigInteger.ZERO

        val leftOne = left is IntLiteral && left.value == BigInteger.ONE
        val rightOne = right is IntLiteral && right.value == BigInteger.ONE

        val leftTrue = left is BooleanLiteral && left.value
        val leftFalse = left is BooleanLiteral && !left.value
        val rightTrue = right is BooleanLiteral && right.value
        val rightFalse = right is BooleanLiteral && !right.value

        return when (operator) {
            BinaryOperator.ADD -> when {
                leftZero -> right
                rightZero -> left
                else -> null
            }
            BinaryOperator.SUBTRACT -> when {
                rightZero -> left
                leftZero -> UnaryExpression(UnaryOperator.MINUS, right)
                else -> null
            }
            BinaryOperator.DIVIDE -> when {
                rightZero -> null
                rightOne -> left
                leftZero && right is IntLiteral -> left
                else -> null
            }
            BinaryOperator.MODULO -> when {
                rightZero -> null
                leftZero && right is IntLiteral -> left
                rightOne && left is IntLiteral -> IntLiteral(BigInteger.ZERO)
                else -> null
            }
            BinaryOperator.MULTIPLY -> when {
                leftOne -> right
                rightOne -> left
                leftZero && right.simple -> left
                rightZero && left.simple -> right
                else -> null
            }
            BinaryOperator.AND -> when {
                leftTrue -> right
                leftFalse -> left
                rightTrue -> left
                rightFalse && left.simple -> right
                else -> null
            }
            BinaryOperator.OR -> when {
                leftFalse -> right
                leftTrue -> left
                rightFalse -> left
                rightTrue && left.simple -> right
                else -> null
            }
            else -> null
        }
    }
}