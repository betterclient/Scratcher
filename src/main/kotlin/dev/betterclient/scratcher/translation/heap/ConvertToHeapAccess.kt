package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.GCInfo
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.translation.visitor.RemoveEmptyAllocations

class ConvertToHeapAccess(
    val functions: List<Function>
) {
    private lateinit var hasLocalsMap: Map<Function, Boolean>

    fun run(): Map<Function, Pair<Int, GCInfo>> {
        hasLocalsMap = HasLocalsMapGenerator(functions).run()

        println("Add stack parameter")
        val stacks = functions.filter { it !is StandardLibASTFunction }.map { func ->
            Parameter(obfuscate("compiler@stack"), PrimitiveType.Integer).also { func.parameters.add(0, it) } to func
        }

        println("Add free(stack) and alloc(stack)")
        stacks.forEach { (par, func) ->
            AllocAndFreeStackAdder(
                par, func, hasLocalsMap
            ).run()
        }

        println("Re-parse locals again")
        functions.forEach { ReParseLocalVariables(it).run() }

        println("Count locals")
        val newFuncs = functions
            .filter { it !is StandardLibASTFunction }
            .associateWith { LocalAllocationCalculator(it).calculate() }

        println("Convert to heap")
        for (function in newFuncs.keys) {
            HeapConversion(function, function) { newFuncs[it]!! }.run()
        }

        newFuncs.forEach { (function, locals) ->
            RemoveEmptyAllocations(function, locals.size).run()
        }

        for (function in newFuncs.keys) {
            HeapConversion(function, function) { newFuncs[it]!! }.run()
        }
        newFuncs.keys.forEach { function ->
            visit(function, object : ASTVisitor() {
                override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                    return if (defaultValue == null) null else super.visitVariableStatement(defaultValue, variable)
                }
            })
        }

        return newFuncs.mapValues { (_, data) ->
            data.size to data.gcInfo
        }
    }
}