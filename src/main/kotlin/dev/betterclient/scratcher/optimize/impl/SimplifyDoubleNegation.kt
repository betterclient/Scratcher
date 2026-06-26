package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.ast.Function

object SimplifyDoubleNegation : Optimization {
    override fun shouldApply(func: Function, callGraph: TCallGraph): Boolean = true

    override fun apply(func: Function, graph: TCallGraph): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitUnaryExpression(operator: UnaryOperator, expression: Expression): Expression {
                if (operator == UnaryOperator.PLUS) {
                    modified = true
                    return expression
                }

                if (operator == UnaryOperator.MINUS && expression is UnaryExpression && expression.operator == UnaryOperator.MINUS) {
                    modified = true
                    return expression.expression
                }

                if (operator == UnaryOperator.NOT && expression is UnaryExpression && expression.operator == UnaryOperator.NOT) {
                    modified = true
                    return expression.expression
                }

                return super.visitUnaryExpression(operator, expression)
            }
        })
        return modified
    }
}