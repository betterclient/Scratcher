package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function

class FunctionReachability(val ast: ASTFile) {
    val reachableFunctions = getAllReachableFunctions()

    private fun getAllReachableFunctions(): List<Function> {
        val visited = mutableSetOf<Function>()
        val queue = ArrayDeque<Function>()

        queue.addAll(ast.functions)
        visited.addAll(ast.functions)

        for (variable in ast.variables) {
            variable.defaultValue?.let { expr ->
                val called = mutableListOf<Function>()
                addAllReachableFunctions(called, expr)
                for (f in called) {
                    if (visited.add(f)) {
                        queue.addLast(f)
                    }
                }
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val directlyCalled = getAllReachableFunctions(current.code)
            for (f in directlyCalled) {
                if (visited.add(f)) {
                    queue.addLast(f)
                }
            }
        }

        return visited.toList()
    }

    private fun getAllReachableFunctions(fromFunc: CodeBlock): List<Function> {
        val funcs = mutableListOf<Function>()
        for (statement in fromFunc.code) {
            when (statement) {
                is ExpressionStatement -> {
                    addAllReachableFunctions(funcs, statement.expression)
                }
                is IfElseStatement -> {
                    addAllReachableFunctions(funcs, statement.condition)
                    funcs.addAll(getAllReachableFunctions(statement.thenBlock))
                    funcs.addAll(getAllReachableFunctions(statement.elseBlock))
                }
                is IfStatement -> {
                    addAllReachableFunctions(funcs, statement.condition)
                    funcs.addAll(getAllReachableFunctions(statement.thenBlock))
                }
                is LocalVariableAssignmentStatement -> {
                    addAllReachableFunctions(funcs, statement.assignment)
                }
                is RepeatStatement -> {
                    addAllReachableFunctions(funcs, statement.amount)
                    funcs.addAll(getAllReachableFunctions(statement.block))
                }
                is ReturnStatement -> {
                    statement.expression?.let {
                        addAllReachableFunctions(funcs, it)
                    }
                }
                is TLVariableAssignmentStatement -> {
                    addAllReachableFunctions(funcs, statement.assignment)
                }
                is VariableAssignmentStatement -> {
                    addAllReachableFunctions(funcs, statement.assignment)
                }
                is VariableStatement -> {
                    addAllReachableFunctions(funcs, statement.defaultValue)
                }
                is WhileStatement -> {
                    addAllReachableFunctions(funcs, statement.condition)
                    funcs.addAll(getAllReachableFunctions(statement.block))
                }

                is TemporaryCallStatement -> {}
                is TemporaryHeapAccessStatement -> {}
            }
        }
        return funcs
    }

    private fun addAllReachableFunctions(funcs: MutableList<Function>, expr: Expression) {
        when (expr) {
            is CallExpression -> {
                funcs.add(expr.func)
                for (arg in expr.arguments) {
                    addAllReachableFunctions(funcs, arg)
                }
            }
            is MemberExpression -> {
                addAllReachableFunctions(funcs, expr.expression)
            }
            is ConcatExpression -> {
                addAllReachableFunctions(funcs, expr.left)
                addAllReachableFunctions(funcs, expr.right)
            }
            is UnaryExpression -> {
                addAllReachableFunctions(funcs, expr.expression)
            }
            is BinaryExpression -> {
                addAllReachableFunctions(funcs, expr.left)
                addAllReachableFunctions(funcs, expr.right)
            }
            is LocalVariableExpression,
            is ParameterExpression,
            is VariableExpression,
            is NewStructExpression,
            is Literal -> {}

            is TemporaryLocalVariableIndexExpression -> throw UnsupportedOperationException("unreachable")
        }
    }
}