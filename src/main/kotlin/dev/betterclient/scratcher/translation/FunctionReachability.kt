package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.except.UnreachableException

class FunctionReachability(val entrypoints: List<ASTEventListener>) {
    fun run(): MutableList<Function> {
        return entrypoints.flatMap { getAllReachableFunctions(it) }.distinct().filter { it !is InlineStandardLibFunction }.toMutableList()
    }

    private fun getAllReachableFunctions(fromEntrypoint: ASTEventListener): List<Function> {
        val visited = mutableSetOf<Function>()
        val queue = ArrayDeque<Function>()

        queue.add(fromEntrypoint.ctx!!)
        visited.add(fromEntrypoint.ctx!!)

        for (variable in fromEntrypoint.sourceAST.variables) {
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
                    addAllReachableFunctions(funcs, statement.target)
                    addAllReachableFunctions(funcs, statement.assignment)
                }
                is VariableStatement -> {
                    statement.defaultValue?.let {
                        addAllReachableFunctions(funcs, it)
                    }
                }
                is WhileStatement -> {
                    addAllReachableFunctions(funcs, statement.condition)
                    funcs.addAll(getAllReachableFunctions(statement.block))
                }

                is TemporaryStatement -> {}
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
            is NonNullAssertExpression,
            is Literal-> {}

            is TemporaryExpression -> throw UnreachableException()
        }
    }
}