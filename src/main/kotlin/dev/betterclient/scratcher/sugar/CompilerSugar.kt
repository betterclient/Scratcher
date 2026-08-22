package dev.betterclient.scratcher.sugar

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.generateCallGraph
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.sugar.dispatch.DynamicDispatchHandler
import dev.betterclient.scratcher.sugar.lambda.LambdaDesugaring
import dev.betterclient.scratcher.sugar.nullability.SafeNullOperations
import dev.betterclient.scratcher.sugar.`when`.WhenDesugaring

object Desugaring {
    private val sugar = listOf(
        SafeNullOperations,
        WhenDesugaring,
        LambdaDesugaring,
        SealedEnumDesugaring,
        DynamicDispatchHandler,
    )

    fun apply(functions: MutableList<Function>, context: CompilationContext) {
        sugar.forEach { s ->
            val callGraph = generateCallGraph(functions)
            if (s is DynamicDispatchHandler) {
                s.apply(callGraph.keys.first(), callGraph, context)
                functions.addAll(StandardLibASTGenerator.dynamicDispatchLib.functions)
                return@forEach
            }

            callGraph.keys.forEach { func ->
                s.apply(func, callGraph, context)
            }

            if (s is LambdaDesugaring) {
                functions.addAll(StandardLibASTGenerator.lambdaLib.functions)
            }
        }
        context.generateGCNames()
    }
}

abstract class CompilerSugar {
    abstract fun apply(func: Function, graph: TCallGraph, context: CompilationContext)
}