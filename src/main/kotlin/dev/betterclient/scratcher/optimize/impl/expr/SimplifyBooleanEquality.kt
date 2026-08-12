package dev.betterclient.scratcher.optimize.impl.expr

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object SimplifyBooleanEquality : Optimization("Simplify boolean equality") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitBinaryExpression(left: Expression, right: Expression, operator: BinaryOperator): Expression {
                if (operator == BinaryOperator.EQUAL || operator == BinaryOperator.NOT_EQUAL) {
                    if (left is BooleanLiteral || right is BooleanLiteral) {
                        val boolVal = (left as? BooleanLiteral ?: right as BooleanLiteral).value
                        val value = if (left is BooleanLiteral) right else left

                        val type = ExpressionTypes.getExpressionType(context, value)
                        if (type == PrimitiveType.Bool) {
                            modified = true
                            return if (operator == BinaryOperator.EQUAL) {
                                if (boolVal) value else UnaryExpression(UnaryOperator.NOT, value)
                            } else {
                                if (boolVal) UnaryExpression(UnaryOperator.NOT, value) else value
                            }
                        }
                    }
                }
                if (operator == BinaryOperator.AND || operator == BinaryOperator.OR) {
                    if (left is BooleanLiteral || right is BooleanLiteral) {
                        val boolVal = (left as? BooleanLiteral ?: right as BooleanLiteral).value
                        val value = if (left is BooleanLiteral) right else left

                        val type = ExpressionTypes.getExpressionType(context, value)
                        if (type == PrimitiveType.Bool) {
                            if (operator == BinaryOperator.AND) {
                                if (boolVal) {
                                    modified = true
                                    return value
                                } else {
                                    if (!hasSideEffects(value)) {
                                        modified = true
                                        return BooleanLiteral(false)
                                    }
                                }
                            } else {
                                if (!boolVal) {
                                    modified = true
                                    return value
                                } else {
                                    if (!hasSideEffects(value)) {
                                        modified = true
                                        return BooleanLiteral(true)
                                    }
                                }
                            }
                        }
                    }
                }

                return super.visitBinaryExpression(left, right, operator)
            }
        })
        return modified
    }

    private fun hasSideEffects(expr: Expression): Boolean {
        return when (expr) {
            is Literal -> false
            is LocalVariableExpression -> false
            is ParameterExpression -> false
            is VariableExpression -> false
            is UnaryExpression -> hasSideEffects(expr.expression)
            is BinaryExpression -> hasSideEffects(expr.left) || hasSideEffects(expr.right)
            is ConcatExpression -> hasSideEffects(expr.left) || hasSideEffects(expr.right)
            is MemberExpression -> hasSideEffects(expr.expression)
            is SafeDotExpression -> hasSideEffects(expr.target)
            is CallExpression, is DynamicCallExpression -> true
            is NonNullAssertExpression -> hasSideEffects(expr.expression)
            is NonNullOrElseExpression -> hasSideEffects(expr.operand1) || hasSideEffects(expr.operand2)
            is StatementExpression -> true
            is TemporaryExpression -> true
            is WhenExpression -> true
        }
    }
}