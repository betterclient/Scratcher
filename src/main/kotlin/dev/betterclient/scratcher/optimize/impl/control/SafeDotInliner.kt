package dev.betterclient.scratcher.optimize.impl.control

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

//this is not an optimization, this just removes safe dots entirely
object SafeDotInliner : Optimization("Safe dot inliner") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitSafeDotExpression(target: Expression, member: Parameter, struct: Struct): Expression {
                modified = true

                //evil hack for storing variables inside expressions
                val variable = LocalVariable("safedot@${getUniqueName()}", struct.type)
                return WhenExpression(
                    subject = VariableStatement(target, variable),
                    branches = listOf(
                        WhenBranch(
                            cond = BinaryExpression(
                                left = NullExpression,
                                right = LocalVariableExpression(variable),
                                operator = BinaryOperator.EQUAL
                            ), //safedot == null
                            block = CodeBlock().also {
                                it.code.add(ExpressionStatement(NullExpression))
                            },
                            isElse = false
                        ),
                        WhenBranch(
                            cond = NullExpression, //else
                            block = CodeBlock().also {
                                it.code.add(ExpressionStatement(MemberExpression(
                                expression = LocalVariableExpression(variable),
                                member = member,
                                struct = struct)))
                            },
                            isElse = true
                        )
                    )
                )
            }
        })

        return modified
    }
}