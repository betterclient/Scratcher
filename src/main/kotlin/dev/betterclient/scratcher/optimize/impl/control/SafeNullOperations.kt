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

                val variable = LocalVariable("safedot@${getUniqueName()}", struct.type.asNullable())
                return StatementExpression(
                    statements = listOf(
                        VariableStatement(target, variable),
                        IfStatement(
                            condition = BinaryExpression(
                                left = NullExpression,
                                right = LocalVariableExpression(variable),
                                operator = BinaryOperator.NOT_EQUAL
                            ),
                            thenBlock = CodeBlock().also {
                                //target != null
                                it.code.add(LocalVariableAssignmentStatement(
                                    variable = variable,
                                    assignment = StringBoxing.autoConvert(
                                        expr = MemberExpression(
                                            expression = LocalVariableExpression(variable),
                                            member = member,
                                            struct = struct
                                        ),
                                        expectedType = member.type.asNullable(),
                                        context = context
                                    )
                                ))
                            }
                        )
                    ),
                    expression = LocalVariableExpression(variable)
                )
            }

            override fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression): Expression {
                modified = true

                if (operand1 == NullExpression) return operand2
                if (operand2 == NullExpression) return operand1

                val op1Type = ExpressionTypes.getExpressionType(context, operand1)
                val op2Type = ExpressionTypes.getExpressionType(context, operand2)
                val isLeftNullable = op1Type is NullableType || op1Type == PrimitiveType.Null
                if (!isLeftNullable) {
                    throw GeneralCompilerException("Left operand of the elvis operator must be nullable, but got: $op1Type")
                }
                val op1NonNull = op1Type.asNonNull()
                val targetType = if (op1NonNull == PrimitiveType.Null) {
                    op2Type
                } else {
                    unifyTypes(op1NonNull, op2Type)
                        ?: throw GeneralCompilerException("Type mismatch: cannot unify types $op1NonNull and $op2Type for elvis operator")
                }

                val lhs = LocalVariable("elvis@lhs@${getUniqueName()}", targetType)
                return StatementExpression(
                    statements = listOf(
                        VariableStatement(operand1, lhs),
                        IfStatement(
                            condition = BinaryExpression(
                                left = LocalVariableExpression(lhs),
                                right = NullExpression,
                                operator = BinaryOperator.EQUAL
                            ),
                            thenBlock = CodeBlock().also {
                                //operand1 == null
                                it.code.add(LocalVariableAssignmentStatement(
                                    variable = lhs,
                                    assignment = StringBoxing.autoConvert(operand2, targetType, context)
                                ))
                            }
                        )
                    ),
                    expression = LocalVariableExpression(lhs)
                )
            }
        })

        return modified
    }
}