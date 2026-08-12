package dev.betterclient.scratcher.optimize.impl.control

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.code.StringBoxing
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

//this is not an optimization, this just rewrites safe null operations
object SafeNullOperations : Optimization("Safe null operations") {
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
                            cond = BooleanLiteral(true), //else
                            block = CodeBlock().also {
                                it.code.add(ExpressionStatement(StringBoxing.autoConvert(
                                    expr = MemberExpression(
                                        expression = LocalVariableExpression(variable),
                                        member = member,
                                        struct = struct
                                    ),
                                    expectedType = member.type.asNullable(),
                                    context = context
                                )))
                            },
                            isElse = true
                        )
                    )
                )
            }

            override fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression): Expression {
                //evil hack part: 2
                modified = true

                val op1Type = ExpressionTypes.getExpressionType(context, operand1)
                val op2Type = ExpressionTypes.getExpressionType(context, operand2)
                val targetType = unifyTypes(op1Type.asNonNull(), op2Type.asNonNull()) ?: op2Type

                val lhs = LocalVariable("elvis@lhs@${getUniqueName()}", op1Type)
                return WhenExpression(
                    branches = listOf(
                        WhenBranch(
                            cond = BinaryExpression(
                                left = LocalVariableExpression(lhs),
                                right = NullExpression,
                                operator = BinaryOperator.EQUAL
                            ),
                            block = CodeBlock().also {
                                //evaluate operand2
                                it.code.add(ExpressionStatement(StringBoxing.autoConvert(operand2, targetType, context)))
                            },
                            isElse = false
                        ),
                        WhenBranch(
                            cond = BooleanLiteral(true),
                            block = CodeBlock().also {
                                //not null, just return lhs
                                it.code.add(ExpressionStatement(StringBoxing.autoConvert(LocalVariableExpression(lhs), targetType, context)))
                            },
                            isElse = true
                        )
                    ),
                    subject = VariableStatement(operand1, lhs)
                )
            }
        })

        return modified
    }
}