package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.impl.*
import dev.betterclient.scratcher.optimize.impl.dynamic.DynamicDispatchHandler
import dev.betterclient.scratcher.optimize.impl.TailCallOptimization
import dev.betterclient.scratcher.optimize.impl.control.DeadCodeElimination
import dev.betterclient.scratcher.optimize.impl.control.FunctionInlining
import dev.betterclient.scratcher.optimize.impl.control.RepeatToWhile
import dev.betterclient.scratcher.optimize.impl.control.SafeDotInliner
import dev.betterclient.scratcher.optimize.impl.dynamic.DirectReferenceCallInlining
import dev.betterclient.scratcher.optimize.impl.expr.ConstantFolding
import dev.betterclient.scratcher.optimize.impl.expr.SimplifyBooleanEquality
import dev.betterclient.scratcher.optimize.impl.expr.SimplifyDoubleNegation
import dev.betterclient.scratcher.optimize.impl.variable.DeadStoreElimination
import dev.betterclient.scratcher.optimize.impl.variable.InlineSingleUseAssignment
import dev.betterclient.scratcher.optimize.impl.variable.SequentialConstantPropagation
import dev.betterclient.scratcher.std.StandardLibASTGenerator

typealias TCallGraph = Map<Function, List<Function>>

object Optimizations {
    private val requiredOptimizations = listOf(
        RepeatToWhile,
        DynamicDispatchHandler,
        SafeDotInliner
    )

    private val optimizations = listOf(
        SafeDotInliner,
        SimplifyDoubleNegation,
        SimplifyBooleanEquality,
        ConstantFolding,
        RepeatToWhile,
        DeadCodeElimination,
        InlineSingleUseAssignment,
        SequentialConstantPropagation,
        DeadStoreElimination,
        FunctionInlining,
        TailCallOptimization,
        DirectReferenceCallInlining,
        DynamicDispatchHandler
    )

    val applyLast = listOf<Optimization>(
        PromoteToGlobals
    )

    fun apply(functions: MutableList<Function>, context: CompilationContext, print: Boolean = true) {
        var changed: Boolean
        var iterations = 0
        val maxIterations = 20
        val applyCounts = mutableMapOf<Optimization, Int>()

        do {
            changed = false
            val callGraph = generateCallGraph(functions)

            val currentFunctions = functions.toList()
            currentFunctions.forEach { func ->
                if (applyAll(context, func, callGraph, applyCounts)) {
                    changed = true
                }
            }

            iterations++
        } while (changed && iterations < maxIterations)

        applyLast.forEach {
            val callGraph = generateCallGraph(functions)
            val currentFunctions = functions.toList()
            currentFunctions.forEach { func ->
                if (CompilationConstants.DISABLE_OPTIMIZATIONS) return@forEach

                if (it.shouldApply(func, callGraph)) {
                    val applied = it.apply(func, callGraph, context)
                    if (applied) applyCounts[it] = (applyCounts[it] ?: 0) + 1
                }
            }
        }

        if (!print) return
        applyCounts.forEach { (optimization, applyCount) ->
            println("   \"${optimization.name}\" applied $applyCount times")
        }
    }

    fun apply(ast: ASTFile, context: CompilationContext, print: Boolean = true) {
        val functions = (ast.functions + ast.imports.values.flatMap { it.functions }).toMutableList()
        apply(functions, context, print)
    }

    private fun applyAll(context: CompilationContext, func: Function, graph: TCallGraph, applyCounts: MutableMap<Optimization, Int>): Boolean {
        if (StandardLibASTGenerator.isStandardLib(func)) return false
        var funcModified = false
        val opts = if (CompilationConstants.DISABLE_OPTIMIZATIONS) requiredOptimizations else optimizations
        opts.forEach { optimization ->
            if (optimization.shouldApply(func, graph)) {
                val applied = optimization.apply(func, graph, context)
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
    abstract fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean
}