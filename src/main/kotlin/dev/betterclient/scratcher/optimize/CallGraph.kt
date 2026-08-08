package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function

@JvmInline
value class CallGraphContext(
    val visitedASTS: MutableList<ASTFile>
)

fun generateCallGraph(functions: List<Function>): TCallGraph {
    val map = mutableMapOf<Function, List<Function>>()
    val dummyContext = CallGraphContext(mutableListOf())
    functions.forEach { func ->
        val calls = mutableListOf<Function>()
        CallGraph(dummyContext, func.sourceAST).generate(func.code, calls)
        map[func] = calls.distinct()
    }
    return map
}

class CallGraph(val context: CallGraphContext, val ast: ASTFile) {

    fun generate(): TCallGraph {
        val map = mutableMapOf<Function, List<Function>>()

        if (context.visitedASTS.contains(ast)) {
            return map
        }
        context.visitedASTS.add(ast)

        map.putAll(ast.functions.associateWith { generate(it).distinct() })
        ast.imports.forEach { (_, ast) ->
            map.putAll(CallGraph(context, ast).generate())
        }

        return map
    }

    private fun generate(function: Function): List<Function> {
        val out = mutableListOf<Function>()
        generate(function.code, out)
        return out
    }

    fun generate(code: CodeBlock, out: MutableList<Function>) {
        code.code.forEach { stmt ->
            when(stmt) {
                is WhileStatement -> {
                    generate(stmt.condition, out)
                    generate(stmt.block, out)
                }
                is IfStatement -> {
                    generate(stmt.condition, out)
                    generate(stmt.thenBlock, out)
                }
                is IfElseStatement -> {
                    generate(stmt.condition, out)
                    generate(stmt.thenBlock, out)
                    generate(stmt.elseBlock, out)
                }
                is RepeatStatement -> {
                    generate(stmt.amount, out)
                    generate(stmt.block, out)
                }
                is ExpressionStatement -> generate(stmt.expression, out)
                is LocalVariableAssignmentStatement -> generate(stmt.assignment, out)
                is ReturnStatement -> stmt.expression?.let { generate(it, out) }
                is TLVariableAssignmentStatement -> generate(stmt.assignment, out)
                is VariableAssignmentStatement -> generate(stmt.assignment, out)
                is VariableStatement -> stmt.defaultValue?.let { generate(it, out) }

                is TemporaryStatement -> {}
            }
        }
    }

    private fun generate(expr: Expression, out: MutableList<Function>) {
        when(expr) {
            is WhenExpression -> {
                if (expr.subject is VariableStatement) {
                    expr.subject.defaultValue?.let { generate(it, out) }
                }
                expr.branches.forEach {
                    generate(it.block, out)
                    generate(it.cond, out)
                }
            }
            is BinaryExpression -> {
                generate(expr.left, out)
                generate(expr.right, out)
            }
            is CallExpression -> {
                out.add(expr.func)
                expr.arguments.forEach { generate(it, out) }
            }
            is DynamicCallExpression -> {
                generate(expr.function, out)
                expr.arguments.forEach { generate(it, out) }
            }
            is ConcatExpression -> {
                generate(expr.left, out)
                generate(expr.right, out)
            }
            is UnaryExpression -> {
                generate(expr.expression, out)
            }
            is MemberExpression -> {
                generate(expr.expression, out)
            }
            is NonNullAssertExpression -> {
                generate(expr.expression, out)
            }
            is NonNullOrElseExpression -> {
                generate(expr.operand1, out)
                generate(expr.operand2, out)
            }
            is Literal -> {}
            is LocalVariableExpression -> {}
            is ParameterExpression -> {}
            is VariableExpression -> {}
            is TemporaryExpression -> {}
        }
    }
}