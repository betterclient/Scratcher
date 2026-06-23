package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement

class ReParseLocalVariables(val func: Function) {
    fun run() {
        run(func.code, mutableListOf())
    }

    private fun run(block: CodeBlock, parentScope: MutableList<LocalVariable>) {
        val currentScope = parentScope.toMutableList()

        val rebuiltLocals = mutableListOf<LocalVariable>()

        for (stmt in block.code) {
            when (stmt) {
                is VariableStatement -> {
                    rebuiltLocals.add(stmt.variable)
                    currentScope.add(stmt.variable)
                }

                is IfStatement -> run(stmt.thenBlock, currentScope)
                is IfElseStatement -> {
                    run(stmt.thenBlock, currentScope)
                    run(stmt.elseBlock, currentScope)
                }

                is WhileStatement -> run(stmt.block, currentScope)
                is RepeatStatement -> run(stmt.block, currentScope)

                else -> {}
            }
        }

        block.localVariables.clear()
        block.localVariables.addAll(rebuiltLocals)
    }
}