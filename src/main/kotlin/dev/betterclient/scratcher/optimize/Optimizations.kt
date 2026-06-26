package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.impl.*
import dev.betterclient.scratcher.std.StandardLibASTGenerator

typealias TCallGraph = Map<Function, List<Function>>

object Optimizations {
    private val optimizations = listOf(
        OptimizeToGlobals,
        InlineSingleUseVariables
    )

    fun apply(ast: ASTFile) {
        var changed: Boolean
        var iterations = 0
        val maxIterations = 20

        do {
            changed = false

            val callGraph = CallGraph(CallGraphContext(mutableListOf()), ast).generate()

            callGraph.forEach { (func, _) ->
                if (applyAll(func, callGraph)) {
                    changed = true
                }
            }

            iterations++
        } while (changed && iterations < maxIterations)
    }

    private fun applyAll(func: Function, graph: TCallGraph): Boolean {
        if (StandardLibASTGenerator.isStandardLib(func)) return false
        var funcModified = false
        optimizations.forEach { optimization ->
            if (optimization.shouldApply(func, graph)) {
                optimization.apply(func, graph)
                funcModified = true
            }
        }
        return funcModified
    }
}

interface Optimization {
    fun shouldApply(func: Function, callGraph: TCallGraph): Boolean
    fun apply(func: Function, graph: TCallGraph): Boolean
}