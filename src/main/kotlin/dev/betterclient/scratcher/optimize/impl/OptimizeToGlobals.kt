package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.*
import dev.betterclient.scratcher.std.StandardLibASTGenerator

object OptimizeToGlobals : Optimization("Convert locals to global variables") {
    override fun shouldApply(
        func: Function,
        callGraph: TCallGraph
    ): Boolean {
        //make sure we aren't
        //recursive
        //and we're guaranteed to run atomically
        return func.warp && !OptimizationUtils.isRecursive(func, callGraph) && OptimizationUtils.filter(func, callGraph) { !it.warp }.isEmpty()
    }

    override fun apply(func: Function, graph: TCallGraph): Boolean {
        val locals = OptimizationUtils.countLocals(func)
            .filter { it.type.isPrimitive }
            .associateWith {
                val variable = TLVariable(
                    name = obfuscate("compiler@optimizeToGlobal@${func.name}::${it.name}"),
                    mutable = true,
                    type = it.type,
                    defaultValue = null,
                    sourceAST = StandardLibASTGenerator.optimizationsLib
                )
                StandardLibASTGenerator.optimizationsLib.variables.add(variable)
                variable
            }
        if (locals.isEmpty()) return false

        visit(func, object : ASTVisitor() {
            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                locals[variable]?.let {
                    return TLVariableAssignmentStatement(it, it.sourceAST, assignment)
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                locals[variable]?.let {
                    return VariableExpression(it, it.sourceAST)
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                locals[variable]?.let { variable ->
                    defaultValue?.let {
                        return TLVariableAssignmentStatement(variable, variable.sourceAST, it)
                    }
                    return null
                }

                return super.visitVariableStatement(defaultValue, variable)
            }
        })

        return true
    }
}