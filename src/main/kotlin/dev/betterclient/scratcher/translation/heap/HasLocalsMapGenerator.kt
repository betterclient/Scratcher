package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

class HasLocalsMapGenerator(
    val functions: List<Function>
) {
    fun run(): Map<Function, Boolean> {
        val hasLocals = functions
            .associateWith(::hasLocalVariables)
            .toMutableMap()

        val callGraph = functions
            .associateWith(::calledFunctions)

        do {
            var changed = false

            for (function in functions) {
                if (hasLocals[function] == true) continue

                if (callGraph[function].orEmpty().any { hasLocals[it] == true }) {
                    hasLocals[function] = true
                    changed = true
                }
            }
        } while (changed)

        return hasLocals
    }

    private fun hasLocalVariables(function: Function): Boolean {
        var foundLocals = false
        visit(function, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
                return if (foundLocals) VisitMode.NONE else VisitMode.READ_ONLY
            }

            override fun visitVariableStatement(
                defaultValue: Expression?,
                variable: LocalVariable
            ): Statement? {
                foundLocals = true
                return super.visitVariableStatement(defaultValue, variable)
            }
        })
        return foundLocals
    }

    private fun calledFunctions(function: Function): List<Function> {
        val calls = mutableListOf<Function>()

        visit(function, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
            override fun visitTemporaryCallStatement(
                func: Function,
                args: MutableList<Expression>
            ): Statement? {
                if (func !is StandardLibASTFunction &&
                    func !is InlineStandardLibFunction
                ) {
                    calls += func
                }

                return super.visitTemporaryCallStatement(func, args)
            }
        })

        return calls
    }
}