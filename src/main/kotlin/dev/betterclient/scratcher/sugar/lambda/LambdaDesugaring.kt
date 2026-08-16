package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar

object LambdaDesugaring : CompilerSugar() {
    lateinit var closureConversion: LambdaClosureConversion
    fun closure(context: CompilationContext): LambdaClosureConversion {
        if (!::closureConversion.isInitialized) {
            closureConversion = LambdaClosureConversion(context)
        }
        return closureConversion
    }

    lateinit var defunctionalization: LambdaDefunctionalization
    fun defunc(context: CompilationContext): LambdaDefunctionalization {
        if (!::defunctionalization.isInitialized) {
            defunctionalization = LambdaDefunctionalization(context, mapOf(), closureConversion)
        }
        return defunctionalization
    }

    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        LambdaParameterCapture.run(func) //move parameters to locals so they can be used from inner lambdas
        LambdaCaptureAnalysis().run(func) //figure out captures
        closure(context).run(func) //replace captures with struct
        visit(func, defunc(context)) //defunc!
    }
}