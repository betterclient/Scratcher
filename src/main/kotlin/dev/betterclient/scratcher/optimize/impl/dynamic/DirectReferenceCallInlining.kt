package dev.betterclient.scratcher.optimize.impl.dynamic

import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionLiteral
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object DirectReferenceCallInlining : Optimization("Direct reference call inlining") { //could also call this devirtualization?
    //this will never apply in a normal codebase, unless you are trying to deliberately waste CPU cycles,
    //or you're passing a function reference to a function and then that function gets inlined whatever
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        var modified = false

        visit(func, object : ASTVisitor() {
            override fun visitDynamicCallExpression(
                function: Expression,
                args: List<Expression>,
                type: FunctionType
            ): Expression {
                if (function is FunctionLiteral) {
                    modified = true
                    return CallExpression(
                        function.function,
                        args
                    )
                }

                return super.visitDynamicCallExpression(function, args, type)
            }
        })

        return modified
    }

}