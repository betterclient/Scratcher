package dev.betterclient.scratcher.sugar.loop

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.simple
import dev.betterclient.scratcher.sugar.CompilerSugar

//extract non-simple while conditions to a helper variable
object WhileConditionSafety : CompilerSugar() {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        visit(func, object : ASTVisitor() {
            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                if (!condition.simple) {
                    //extract to variable
                    val whileCond = LocalVariable("while_condition_${getUniqueName()}", PrimitiveType.Bool)
                    addStatements(listOf(
                        VariableStatement(condition, whileCond)
                    ))

                    block.code.addLast(
                        LocalVariableAssignmentStatement(
                            whileCond, condition
                        )
                    )

                    return WhileStatement(LocalVariableExpression(whileCond), block)
                }

                return super.visitWhileStatement(condition, block)
            }
        })
    }
}