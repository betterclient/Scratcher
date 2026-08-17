package dev.betterclient.scratcher.sugar

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.generateCallGraph
import dev.betterclient.scratcher.sugar.lambda.LambdaDesugaring
import dev.betterclient.scratcher.sugar.`when`.WhenDesugaring

object Desugaring {
    private val sugar = listOf(
        LambdaDesugaring,
        WhenDesugaring
    )

    fun apply(functions: MutableList<Function>, context: CompilationContext) {
        val callGraph = generateCallGraph(functions)
        sugar.forEach { s ->
            functions.toList().forEach { func ->
                s.apply(func, callGraph, context)
            }
        }
        context.generateGCNames()
    }
}

abstract class CompilerSugar {
    abstract fun apply(func: Function, graph: TCallGraph, context: CompilationContext)
}