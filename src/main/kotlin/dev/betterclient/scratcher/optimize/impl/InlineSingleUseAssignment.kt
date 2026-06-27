package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph

object InlineSingleUseAssignment : Optimization("Inline single-use assignments") {
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        return false
    }
}