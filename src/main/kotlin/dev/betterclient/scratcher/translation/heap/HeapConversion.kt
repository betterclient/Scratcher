package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.name
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import org.antlr.v4.kotlinruntime.ast.Position

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

    override fun visitLocalVariableAssignmentStatement(
        variable: LocalVariable,
        assignment: Expression,
        position: Position?
    ): Statement {
        val index = indexMap[variable]!!

        return TemporaryHeapSetStatement(
            index = if (index == 0) {
                ParameterExpression(stackPar, null)
            } else {
                BinaryExpression(
                    left = ParameterExpression(stackPar, null),
                    right = IntLiteral(index.toBigInteger(), null),
                    operator = BinaryOperator.ADD,
                    position = null
                )
            },
            data = assignment,
            position = position
        )
    }

    override fun visitVariableAssignmentStatement(
        target: Expression,
        variable: Parameter,
        struct: Struct,
        assignment: Expression,
        position: Position?
    ): Statement {
        val parIndex = struct.getIndex(variable)

        return TemporaryHeapSetStatement(
            index = if (parIndex == 0) {
                target
            } else {
                BinaryExpression(
                    left = target,
                    right = IntLiteral(parIndex.toBigInteger(), null),
                    operator = BinaryOperator.ADD,
                    position = null
                )
            },
            data = assignment,
            position = position
        )
    }

    override fun visitVariableStatement(
        defaultValue: Expression?,
        variable: LocalVariable,
        position: Position?
    ): Statement? {
        val index = indexMap[variable]!!
        if (defaultValue == null) {
            return null
        } else {
            return TemporaryHeapSetStatement(
                index = if (index == 0) {
                    ParameterExpression(stackPar, null)
                } else {
                    BinaryExpression(
                        left = ParameterExpression(stackPar, null),
                        right = IntLiteral(index.toBigInteger(), null),
                        operator = BinaryOperator.ADD,
                        position = null
                    )
                },
                data = defaultValue,
                position = position
            )
        }
    }

    override fun visitTemporaryStackNameExpression(func: Function, position: Position?): Expression {
        return StringLiteral(getFunctionLocals(func).gcInfo.name.toString(), position)
    }

    override fun visitTemporaryStackSizeExpression(
        func: Function,
        includeGCHeader: Boolean,
        position: Position?
    ): Expression {
        val baseSize = getFunctionLocals(func).size
        val totalSize = if (includeGCHeader) baseSize + 1 else baseSize
        return IntLiteral(totalSize.toBigInteger(), position)
    }

    override fun visitLocalVariableExpression(variable: LocalVariable, position: Position?): Expression {
        val index = indexMap[variable] ?: throw UnreachableException(variable.name)
        return TemporaryHeapGetExpression(
            index = if (index == 0) {
                ParameterExpression(stackPar, null)
            } else {
                BinaryExpression(
                    left = ParameterExpression(stackPar, null),
                    right = IntLiteral(index.toBigInteger(), null),
                    operator = BinaryOperator.ADD,
                    position = null
                )
            },
            type = null,
            position = position
        )
    }

    override fun visitTemporaryLocalVariableIndexExpression(
        variable: LocalVariable,
        position: Position?
    ): Expression {
        val index = indexMap[variable]!!
        return if (index == 0) {
            ParameterExpression(stackPar, position)
        } else {
            BinaryExpression(
                left = ParameterExpression(stackPar, null),
                right = IntLiteral(index.toBigInteger(), null),
                operator = BinaryOperator.ADD,
                position = position
            )
        }
    }

    override fun visitMemberExpression(
        expression: Expression,
        member: Parameter,
        struct: Struct,
        position: Position?
    ): Expression {
        val parIndex = struct.getIndex(member)
        return if (parIndex == 0) {
            TemporaryHeapGetExpression(
                index = expression,
                type = null,
                position = position
            )
        } else {
            TemporaryHeapGetExpression(
                index = BinaryExpression(
                    left = expression,
                    right = IntLiteral(parIndex.toBigInteger(), null),
                    operator = BinaryOperator.ADD,
                    position = null
                ),
                type = null,
                position = position
            )
        }
    }
}