package dev.betterclient.scratcher.optimize.impl.variable

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import org.antlr.v4.kotlinruntime.ast.Position

object DeadStoreElimination : Optimization("Dead store elimination") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        var modified = false
        val readCounts = mutableMapOf<LocalVariable, Int>()

        //do a read to get read counts
        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitLocalVariableExpression(variable: LocalVariable, position: Position?): Expression {
                readCounts[variable] = (readCounts[variable] ?: 0) + 1
                return super.visitLocalVariableExpression(variable, position)
            }

            override fun visitTemporaryLocalVariableIndexExpression(
                variable: LocalVariable,
                position: Position?
            ): Expression {
                readCounts[variable] = (readCounts[variable] ?: 0) + 1
                return super.visitTemporaryLocalVariableIndexExpression(variable, position)
            }
        })

        visit(func, object : ASTVisitor() {
            override fun visitVariableStatement(
                defaultValue: Expression?,
                variable: LocalVariable,
                position: Position?
            ): Statement? {
                if ((readCounts[variable] ?: 0) == 0) {
                    modified = true
                    if (defaultValue != null && hasSideEffects(defaultValue)) {
                        return ExpressionStatement(defaultValue, position)
                    }
                    return null
                }
                return super.visitVariableStatement(defaultValue, variable, position)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression,
                position: Position?
            ): Statement? {
                if ((readCounts[variable] ?: 0) == 0) {
                    modified = true
                    if (hasSideEffects(assignment)) {
                        return ExpressionStatement(assignment, position)
                    }
                    return null
                }
                return super.visitLocalVariableAssignmentStatement(variable, assignment, position)
            }
        })

        return modified
    }

    fun hasSideEffects(expr: Expression): Boolean {
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
            is NonNullAssertExpression -> hasSideEffects(expr.expression)
            is NonNullOrElseExpression -> hasSideEffects(expr.operand1) || hasSideEffects(expr.operand2)
            is CallExpression, is DynamicCallExpression -> true
            is StatementExpression -> true
            is LambdaExpression -> true
            is TemporaryExpression -> true
            is WhenExpression -> true
            is CheckSealedEnumTypeExpression -> hasSideEffects(expr.expr)
            is SealedEnumCastExpression -> hasSideEffects(expr.expr)
            is SealedEnumConstructionExpression -> true
            is StaticListExpression -> false
            is StaticListItemExpression -> hasSideEffects(expr.index)
            is StaticListLengthExpression -> false
            is StaticListContainsExpression -> hasSideEffects(expr.item)
            is StaticListItemIndexExpression -> hasSideEffects(expr.item)
        }
    }
}
