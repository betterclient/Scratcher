package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit

class FunctionReachability(val entrypoints: List<ASTEventListener>) {
    fun run(): MutableList<Function> {
        val visited = mutableSetOf<Function>()
        entrypoints.forEach { entrypoint ->
            for (variable in entrypoint.sourceAST.variables) {
                variable.defaultValue?.let { expr ->
                    (object : ASTVisitor() {
                        override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                            visit(visited, func)
                            return super.visitCallExpression(func, args)
                        }
                    }).visit(expr)
                }
            }

            entrypoint.ctx?.let { func ->
                visit(visited, func)
            }
        }


        return visited.toMutableList()
    }

    private fun visit(
        visited: MutableSet<Function>,
        function: Function
    ){
        if (function is InlineStandardLibFunction) return
        if (visited.contains(function)) return

        visited.add(function)
        visit(function, object : ASTVisitor() {
            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                visit(visited, func)
                return super.visitCallExpression(func, args)
            }
        })
    }
}