package dev.betterclient.scratcher.sugar.nullability

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StatementExpression
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.code.StringBoxing
import dev.betterclient.scratcher.ast.unifyTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar

object SafeNullOperations : CompilerSugar() {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ) {
        visit(func, object : ASTVisitor() {
            override fun visitSafeDotExpression(target: Expression, member: Parameter, struct: Struct): Expression {
                val variable = LocalVariable("safedot@${getUniqueName()}", member.type.asNullable())
                if (ExpressionTypes.getExpressionType(context, target) !is NullableType)
                    return MemberExpression(target, member, struct)

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
                                it.code.add(
                                    LocalVariableAssignmentStatement(
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
                                    )
                                )
                            }
                        )
                    ),
                    expression = LocalVariableExpression(variable)
                )
            }

            override fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression): Expression {
                if (operand1 == NullExpression) return visit(operand2)
                val op2Visited = visit(operand2)
                if (op2Visited == NullExpression) return operand1

                val op1Type = ExpressionTypes.getExpressionType(context, operand1)
                val op2Type = ExpressionTypes.getExpressionType(context, op2Visited)
                val isLeftNullable = op1Type is NullableType || op1Type == PrimitiveType.Null
                if (!isLeftNullable) {
                    return operand1
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
                                it.code.add(
                                    LocalVariableAssignmentStatement(
                                        variable = lhs,
                                        assignment = StringBoxing.autoConvert(op2Visited, targetType, context)
                                    )
                                )
                            }
                        )
                    ),
                    expression = LocalVariableExpression(lhs)
                )
            }
        })
    }
}