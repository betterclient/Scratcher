package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph

object RepeatToWhile : Optimization {
    override fun shouldApply(func: Function, callGraph: TCallGraph) = true
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {

        return true
    }
}