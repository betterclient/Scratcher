package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object DeadStoreElimination : Optimization("Dead store elimination") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        var modified = false
        val readCounts = mutableMapOf<LocalVariable, Int>()

        //do a read to get read counts
        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                readCounts[variable] = (readCounts[variable] ?: 0) + 1
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
                readCounts[variable] = (readCounts[variable] ?: 0) + 1
                return super.visitTemporaryLocalVariableIndexExpression(variable)
            }
        })

        visit(func, object : ASTVisitor() {
            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if ((readCounts[variable] ?: 0) == 0) {
                    modified = true
                    if (defaultValue != null && hasSideEffects(defaultValue)) {
                        return ExpressionStatement(defaultValue)
                    }
                    return null
                }
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                if ((readCounts[variable] ?: 0) == 0) {
                    modified = true
                    if (hasSideEffects(assignment)) {
                        return ExpressionStatement(assignment)
                    }
                    return null
                }
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
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
            is CallExpression -> true
            is NonNullAssertExpression -> true
            is TemporaryExpression -> true
        }
    }
}
