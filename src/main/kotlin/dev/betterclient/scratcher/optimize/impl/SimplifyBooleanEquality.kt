package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object SimplifyBooleanEquality : Optimization {
    override fun shouldApply(func: Function, callGraph: TCallGraph) = true
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitBinaryExpression(left: Expression, right: Expression, operator: BinaryOperator): Expression {
                if (operator == BinaryOperator.EQUAL || operator == BinaryOperator.NOT_EQUAL) {
                    if (left is BooleanLiteral || right is BooleanLiteral) {
                        val boolVal = (left as? BooleanLiteral ?: right as BooleanLiteral).value
                        val value = if (left is BooleanLiteral) right else left

                        val type = ExpressionTypes.getExpressionType(value)
                        if (type == Type.bool) {
                            modified = true
                            return if (operator == BinaryOperator.EQUAL) {
                                if (boolVal) value else UnaryExpression(UnaryOperator.NOT, value)
                            } else {
                                if (boolVal) UnaryExpression(UnaryOperator.NOT, value) else value
                            }
                        }
                    }
                }

                return super.visitBinaryExpression(left, right, operator)
            }
        })
        return modified
    }
}