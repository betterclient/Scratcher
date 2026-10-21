package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar

object LambdaDesugaring : CompilerSugar() {
    private var cachedContext: CompilationContext? = null
    private lateinit var cachedClosureConversion: LambdaClosureConversion
    private lateinit var cachedDefunc: LambdaDefunctionalization

    private fun getPerContext(context: CompilationContext) {
        if (cachedContext !== context) {
            cachedContext = context
            val conversion = LambdaClosureConversion(context)
            cachedClosureConversion = conversion
            cachedDefunc = LambdaDefunctionalization(context, closureConversion = conversion)
        }
    }

    fun closure(context: CompilationContext): LambdaClosureConversion {
        getPerContext(context)
        return cachedClosureConversion
    }

    fun defunc(context: CompilationContext): LambdaDefunctionalization {
        getPerContext(context)
        return cachedDefunc
    }

    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        LambdaParameterCapture.run(func) //move parameters to locals so they can be used from inner lambdas
        LambdaCaptureAnalysis().run(func) //figure out captures
        closure(context).run(func) //replace captures with struct
        visit(func, defunc(context)) //defunc!
    }
}