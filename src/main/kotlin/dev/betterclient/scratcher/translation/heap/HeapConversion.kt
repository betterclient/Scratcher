package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.name
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit

class HeapConversion(
    val func: Function,
    currentFunction: Function,
    val getFunctionLocals: (Function) -> FunctionLocalsInfo
) : ASTVisitor() {
    private val indexMap = getFunctionLocals(currentFunction).indexMap
    private val stackPar = currentFunction.parameters.first()

    fun run() {
        visit(func, this)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement {
        val index = indexMap[variable]!!

        return TemporaryHeapSetStatement(
            index = if (index == 0) {
                ParameterExpression(stackPar)
            } else {
                BinaryExpression(
                    left = ParameterExpression(stackPar),
                    right = IntLiteral(index.toBigInteger()),
                    operator = BinaryOperator.ADD,
                )
            },
            data = assignment
        )
    }

    override fun visitVariableAssignmentStatement(
        target: Expression,
        variable: Parameter,
        struct: Struct,
        assignment: Expression
    ): Statement {
        val parIndex = struct.getIndex(variable)

        return TemporaryHeapSetStatement(
            index = if (parIndex == 0) {
                target
            } else {
                BinaryExpression(
                    left = target,
                    right = IntLiteral(parIndex.toBigInteger()),
                    operator = BinaryOperator.ADD,
                )
            },
            data = assignment
        )
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        val index = indexMap[variable]!!
        if (defaultValue == null) {
            return null
        } else {
            return TemporaryHeapSetStatement(
                index = if (index == 0) {
                    ParameterExpression(stackPar)
                } else {
                    BinaryExpression(
                        left = ParameterExpression(stackPar),
                        right = IntLiteral(index.toBigInteger()),
                        operator = BinaryOperator.ADD,
                    )
                },
                data = defaultValue
            )
        }
    }

    override fun visitTemporaryStackNameExpression(func: Function): Expression {
        return StringLiteral(getFunctionLocals(func).gcInfo.name.toString())
    }

    override fun visitTemporaryStackSizeExpression(func: Function, includeGCHeader: Boolean): Expression {
        val baseSize = getFunctionLocals(func).size
        val totalSize = if (includeGCHeader) baseSize + 1 else baseSize
        return IntLiteral(totalSize.toBigInteger())
    }

    override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
        val index = indexMap[variable]!!
        return TemporaryHeapGetExpression(
            index = if (index == 0) {
                ParameterExpression(stackPar)
            } else {
                BinaryExpression(
                    left = ParameterExpression(stackPar),
                    right = IntLiteral(index.toBigInteger()),
                    operator = BinaryOperator.ADD,
                )
            }
        )
    }

    override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
        val index = indexMap[variable]!!
        return if (index == 0) {
            ParameterExpression(stackPar)
        } else {
            BinaryExpression(
                left = ParameterExpression(stackPar),
                right = IntLiteral(index.toBigInteger()),
                operator = BinaryOperator.ADD,
            )
        }
    }

    override fun visitMemberExpression(expression: Expression, member: Parameter, struct: Struct): Expression {
        val parIndex = struct.getIndex(member)
        return if (parIndex == 0) {
            TemporaryHeapGetExpression(
                expression
            )
        } else {
            TemporaryHeapGetExpression(
                BinaryExpression(
                    left = expression,
                    right = IntLiteral(parIndex.toBigInteger()),
                    operator = BinaryOperator.ADD,
                )
            )
        }
    }
}