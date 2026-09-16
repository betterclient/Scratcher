package dev.betterclient.scratcher.sugar.other

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.simple
import dev.betterclient.scratcher.sugar.CompilerSugar

object ShortCircuit : CompilerSugar() {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        if (!CompilationConstants.SHORT_CIRCUITS) return

        visit(func, object : ASTVisitor() {
            override fun visitBinaryExpression(
                left: Expression,
                right: Expression,
                operator: BinaryOperator
            ): Expression {
                if (operator == BinaryOperator.AND && !right.simple) {
                    val outVar = LocalVariable("short_circuit_and_${getUniqueName()}", PrimitiveType.Bool)
                    return StatementExpression(
                        statements = listOf(
                            VariableStatement(left, outVar),
                            IfStatement(
                                condition = LocalVariableExpression(outVar),
                                thenBlock = CodeBlock().also {
                                    it.code.add(LocalVariableAssignmentStatement(outVar, right))
                                }
                            )
                        ),
                        expression = LocalVariableExpression(outVar)
                    )
                }

                if (operator == BinaryOperator.OR && !right.simple) {
                    val outVar = LocalVariable("short_circuit_or_${getUniqueName()}", PrimitiveType.Bool)
                    return StatementExpression(
                        statements = listOf(
                            VariableStatement(left, outVar),
                            IfStatement(
                                condition = UnaryExpression(
                                    operator = UnaryOperator.NOT,
                                    expression = LocalVariableExpression(outVar)
                                ),
                                thenBlock = CodeBlock().also {
                                    it.code.add(LocalVariableAssignmentStatement(outVar, right))
                                }
                            )
                        ),
                        expression = LocalVariableExpression(outVar)
                    )
                }

                return super.visitBinaryExpression(left, right, operator)
            }
        })
    }
}