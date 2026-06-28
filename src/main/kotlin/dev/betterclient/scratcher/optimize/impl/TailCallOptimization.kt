package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph

object TailCallOptimization : Optimization("Tail call optimization") {
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        return false
    }
}