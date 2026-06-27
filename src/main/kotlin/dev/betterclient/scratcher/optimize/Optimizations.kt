package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.impl.*
import dev.betterclient.scratcher.std.StandardLibASTGenerator

typealias TCallGraph = Map<Function, List<Function>>

object Optimizations {
    private val optimizations = listOf(
        SimplifyDoubleNegation,
        SimplifyBooleanEquality,
        ConstantFolding,
        RepeatToWhile,
        DeadCodeElimination,
        InlineSingleUseAssignment,
        SequentialConstantPropagation,
        DeadStoreElimination
    )

    fun apply(ast: ASTFile) {
        var changed: Boolean
        var iterations = 0
        val maxIterations = 20
        val applyCounts = mutableMapOf<Optimization, Int>()

        do {
            changed = false

            val callGraph = CallGraph(CallGraphContext(mutableListOf()), ast).generate()

            callGraph.forEach { (func, _) ->
                if (applyAll(func, callGraph, applyCounts)) {
                    changed = true
                }
            }

            iterations++
        } while (changed && iterations < maxIterations)

        applyCounts.forEach { (optimization, applyCount) ->
            println("   \"${optimization.name}\" applied $applyCount times")
        }

        //do optimize to globals last because maybe the locals are gonna get inlined, so optimize them to globals at the very end
        val callGraph = CallGraph(CallGraphContext(mutableListOf()), ast).generate()
        callGraph.forEach { (func, _) ->
            if(OptimizeToGlobals.shouldApply(func, callGraph)) {
                OptimizeToGlobals.apply(func, callGraph)
            }
        }
    }

    private fun applyAll(func: Function, graph: TCallGraph, applyCounts: MutableMap<Optimization, Int>): Boolean {
        if (StandardLibASTGenerator.isStandardLib(func)) return false
        var funcModified = false
        optimizations.forEach { optimization ->
            if (optimization.shouldApply(func, graph)) {
                val applied = optimization.apply(func, graph)
                if (applied)
                    applyCounts[optimization] = (applyCounts[optimization]?: 0) + 1
                funcModified = true
            }
        }
        return funcModified
    }
}

abstract class Optimization(val name: String) {
    open fun shouldApply(func: Function, callGraph: TCallGraph): Boolean = true
    abstract fun apply(func: Function, graph: TCallGraph): Boolean
}