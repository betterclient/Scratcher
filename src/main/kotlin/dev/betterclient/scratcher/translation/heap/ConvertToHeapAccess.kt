package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.GCInfo
import dev.betterclient.scratcher.gc.StackGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit

class ConvertToHeapAccess(
    val functions: List<Function>
) {
    private val temporaryExpression = mutableMapOf<Function, TemporaryHeapGetExpression>() //used as a marker
    private val temporaryNameExpression = mutableMapOf<Function, TemporaryHeapGetExpression>()
    private lateinit var hasLocalsMap: Map<Function, Boolean>

    fun run(): Map<Function, Pair<Int, GCInfo>> {
        hasLocalsMap = HasLocalsMapGenerator(functions).run()

        println("Add stack parameter")
        val stacks = functions.filter { it !is StandardLibASTFunction }.map { func ->
            Parameter(obfuscate("compiler@stack"), Type.int).also { func.parameters.add(0, it) } to func
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
            .associateWith { countLocals(it) }

        println("Convert to heap")
        for (function in newFuncs.keys) {
            HeapConversion(function, function) { newFuncs[it]!! }.run()
        }

        return newFuncs.mapValues { (_, data) ->
            val (list, info) = data
            list.size to info
        }
    }

    private fun countLocals(function: Function): Pair<List<LocalVariable>, GCInfo> {
        val out = countInternalLocals(function)
        return out to StackGCInfo(out.map {
            it.type
        }, function).also { addGC(it) }
    }

    private fun countInternalLocals(func: Function): List<LocalVariable> {
        val vars = mutableListOf<LocalVariable>()
        visit(func, object : ASTVisitor() {
            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                vars.add(variable)
                return super.visitVariableStatement(defaultValue, variable)
            }
        })
        return vars.distinct()
    }
}