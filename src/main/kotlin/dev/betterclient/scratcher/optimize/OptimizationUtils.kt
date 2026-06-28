package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.Statement

object OptimizationUtils {
    fun isRecursive(target: Function, callGraph: TCallGraph): Boolean {
        val visited = mutableSetOf<Function>()

        fun dfs(current: Function): Boolean {
            val calls = callGraph[current] ?: return false
            for (called in calls) {
                if (called == target) {
                    return true
                }
                if (visited.add(called)) {
                    if (dfs(called)) {
                        return true
                    }
                }
            }
            return false
        }

        return dfs(target)
    }

    fun hasCalls(target: Function, from: Function, callGraph: TCallGraph): Boolean {
        val visited = mutableSetOf<Function>()

        fun dfs(current: Function): Boolean {
            val calls = callGraph[current] ?: return false
            for (called in calls) {
                if (called == target) {
                    return true
                }
                if (visited.add(called)) {
                    if (dfs(called)) {
                        return true
                    }
                }
            }
            return false
        }

        return dfs(from)
    }

    fun isOnlyDirectlyRecursive(func: Function, callGraph: TCallGraph): Boolean {
        val calls = callGraph[func] ?: return false

        if (!calls.contains(func)) return false

        val visited = mutableSetOf<Function>()

        fun canReachTarget(current: Function): Boolean {
            if (current == func) return true
            val nextCalls = callGraph[current] ?: return false
            for (called in nextCalls) {
                if (visited.add(called)) {
                    if (canReachTarget(called)) return true
                }
            }
            return false
        }

        for (called in calls) {
            if (called != func) {
                if (visited.add(called)) {
                    if (canReachTarget(called)) { //indirect!!!
                        return false
                    }
                }
            }
        }

        return true
    }

    fun filter(func: Function, callGraph: TCallGraph, filter: (Function) -> Boolean): List<Function> {
        val visited = mutableSetOf<Function>()
        val queue = ArrayDeque<Function>()

        val directCalls = callGraph[func] ?: emptyList()

        queue.addAll(directCalls)
        visited.addAll(directCalls)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val downstreamCalls = callGraph[current] ?: emptyList()
            for (calledFunc in downstreamCalls) {
                if (visited.add(calledFunc)) {
                    queue.add(calledFunc)
                }
            }
        }

        return visited.filter(filter)
    }

    fun countLocals(function: Function): List<LocalVariable> {
        val vars = mutableListOf<LocalVariable>()
        visit(function, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
                return VisitMode.READ_ONLY
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                vars.add(variable)
                return super.visitVariableStatement(defaultValue, variable)
            }
        })

        return vars.distinct()
    }
}