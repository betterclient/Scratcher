package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.OptimizationUtils
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object PromoteToGlobals : Optimization("Promoto to globals") {
    override fun shouldApply(func: Function, callGraph: TCallGraph): Boolean {
        return func.warp && OptimizationUtils.filter(func, callGraph) { !it.warp }.isEmpty()
    }

    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        var modified = false
        val eligible = findEligibleLocals(func, graph)


        return modified
    }

    private fun findEligibleLocals(current: Function, graph: TCallGraph): List<LocalVariable> {
        val out = mutableListOf<LocalVariable>()
        val locals = OptimizationUtils.countLocals(current)
        if (!OptimizationUtils.isRecursive(current, graph)) {
            return locals
        }

        val variableStates = locals.associateWith { VariableState.NOT_DECLARED }.toMutableMap()

        //this is called liveness analysis(??)
        visit(current, object : ASTVisitor() {
            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (defaultValue != null) {
                    variableStates[variable] = VariableState.DIRTY
                }

                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                if (variableStates[variable] != VariableState.UNSAFE) {
                    variableStates[variable] = VariableState.DIRTY
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                if(variableStates[variable] == VariableState.USED) {
                    variableStates[variable] = VariableState.UNSAFE
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                if (OptimizationUtils.hasCalls(current, func, graph)) {
                    //recursive!
                    variableStates.replaceAll { _, state ->
                        if (state == VariableState.DIRTY) {
                            VariableState.USED
                        } else state
                    }
                }

                return super.visitCallExpression(func, args)
            }
        })

        return out
    }
}

enum class VariableState {
    NOT_DECLARED, DIRTY, USED, UNSAFE
}