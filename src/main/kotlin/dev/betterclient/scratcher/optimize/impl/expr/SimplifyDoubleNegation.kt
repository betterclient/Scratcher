package dev.betterclient.scratcher.optimize.impl.expr

import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.EnumLiteral
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionLiteral
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.Literal
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.parser.CompilationContext

object SimplifyDoubleNegation : Optimization("Simplify double negation") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
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