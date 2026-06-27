package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.impl.*
import dev.betterclient.scratcher.std.StandardLibASTGenerator

typealias TCallGraph = Map<Function, List<Function>>

object Optimizations {
    private val optimizations = listOf(
        InlineSingleUseVariables,
        SimplifyDoubleNegation,
        SimplifyBooleanEquality,
        ConstantFolding,
        RepeatToWhile,
        DeadCodeElimination
        //TODO: Dead Variable Elimination/single use assignment inline
        //TODO: loop unrolling?
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

        //do optimize to globals last because maybe the locals are gonna get inlined, so optimize them to globals at the very end
        val callGraph = CallGraph(CallGraphContext(mutableListOf()), ast).generate()
        callGraph.forEach { (func, _) ->
            if(OptimizeToGlobals.shouldApply(func, callGraph)) {
                OptimizeToGlobals.apply(func, callGraph)
            }
        }
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