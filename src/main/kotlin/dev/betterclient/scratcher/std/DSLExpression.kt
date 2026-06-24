package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.codegen.ast.SBoolOperator
import dev.betterclient.scratcher.codegen.ast.SBoolParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchBoolExpression
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.VariableStatements
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable

@StandardLibraryScratchDSL
sealed class DSLExpression {
    abstract fun lower(): ScratchExpression
}

@StandardLibraryScratchDSL
sealed class DSLBoolExpression : DSLExpression() {
    abstract override fun lower(): ScratchBoolExpression
}

class DSLArgumentExpression(
    val argument: ScratchFuncArgument
) : DSLExpression() {
    override fun lower() = ScratchStringParameterExpression(argument)
}

class DSLBoolArgumentExpression(
    val argument: ScratchFuncArgument
) : DSLBoolExpression() {
    override fun lower() = SBoolParameterExpression(argument)
}

class DSLFromCreator(
    val create: () -> ScratchExpression
) : DSLExpression() {
    override fun lower() = create()
}

class DSLBoolFromCreator(
    val create: () -> ScratchBoolExpression
) : DSLBoolExpression() {
    override fun lower() = create()
}

fun DSLBoolExpression.not(): DSLBoolExpression = DSLBoolFromCreator {
    BoolOperatorExpressions.SNotExpression(
        this.lower()
    )
}

infix fun DSLExpression.gt(right: DSLExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.BinaryExpression(
        operand1 = this.lower(),
        operand2 = right.lower(),
        binaryOperator = SBinaryOperator.GT
    )
}

infix fun DSLExpression.gte(right: DSLExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.BinaryExpression(
        operand1 = this.lower(),
        operand2 = right.lower(),
        binaryOperator = SBinaryOperator.GTE
    )
}

infix fun DSLExpression.lt(right: DSLExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.BinaryExpression(
        operand1 = this.lower(),
        operand2 = right.lower(),
        binaryOperator = SBinaryOperator.LT
    )
}


infix fun DSLExpression.lte(right: DSLExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.BinaryExpression(
        operand1 = this.lower(),
        operand2 = right.lower(),
        binaryOperator = SBinaryOperator.LTE
    )
}

infix fun DSLExpression.equals(other: DSLExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.BinaryExpression(
        operand1 = this.lower(),
        operand2 = other.lower(),
        binaryOperator = SBinaryOperator.EQUALS
    )
}

operator fun DSLExpression.plus(right: DSLExpression) = DSLFromCreator {
    OperatorExpressions.BinaryExpression(
        left = this.lower(),
        right = right.lower(),
        operator = OperatorExpressions.BinaryOperator.ADD
    )
}

operator fun DSLExpression.minus(right: DSLExpression) = DSLFromCreator {
    OperatorExpressions.BinaryExpression(
        left = this.lower(),
        right = right.lower(),
        operator = OperatorExpressions.BinaryOperator.SUBTRACT
    )
}

operator fun DSLExpression.times(right: DSLExpression) = DSLFromCreator {
    OperatorExpressions.BinaryExpression(
        left = this.lower(),
        right = right.lower(),
        operator = OperatorExpressions.BinaryOperator.MULTIPLY
    )
}

infix fun DSLExpression.math(operation: MathOp) = DSLFromCreator {
    OperatorExpressions.MathOperation(
        num = this.lower(),
        operation = operation
    )
}

fun DSLExpression.round() = DSLFromCreator {
    OperatorExpressions.RoundNumber(this.lower())
}

operator fun DSLExpression.div(right: DSLExpression) = DSLFromCreator {
    OperatorExpressions.BinaryExpression(
        left = this.lower(),
        right = right.lower(),
        operator = OperatorExpressions.BinaryOperator.DIVIDE
    )
}

infix fun DSLBoolExpression.or(right: DSLBoolExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.SBoolComparisonExpressions(
        operand1 = this.lower(),
        operand2 = right.lower(),
        operator = SBoolOperator.OR
    )
}

infix fun DSLBoolExpression.and(right: DSLBoolExpression) = DSLBoolFromCreator {
    BoolOperatorExpressions.SBoolComparisonExpressions(
        operand1 = this.lower(),
        operand2 = right.lower(),
        operator = SBoolOperator.AND
    )
}

@StandardLibraryScratchDSL
sealed interface DSLListExprs {
    fun ScratchList.contains(element: DSLExpression): DSLBoolExpression {
        return DSLBoolFromCreator {
            ListExpressions.ContainsItemInList(this, element.lower())
        }
    }

    val ScratchList.length
        get() = DSLFromCreator {
            ListExpressions.LengthOfList(this)
        }

    operator fun ScratchList.set(index: DSLExpression, value: DSLExpression) {
        (this@DSLListExprs as CodeBuilder).addStatement(ListStatements.ReplaceItem(this, value.lower(), index.lower()))
    }

    operator fun ScratchList.get(index: DSLExpression): DSLExpression {
        return DSLFromCreator {
            ListExpressions.ItemAtIndex(this, index.lower())
        }
    }

    fun ScratchList.insert(index: DSLExpression, value: DSLExpression) {
        (this@DSLListExprs as CodeBuilder).addStatement(ListStatements.InsertItem(this, value.lower(), index.lower()))
    }

    fun ScratchList.add(item: DSLExpression) {
        (this@DSLListExprs as CodeBuilder).addStatement(
            ListStatements.AddToList(this, item.lower())
        )
    }


    fun ScratchList.remove(index: DSLExpression) {
        (this@DSLListExprs as CodeBuilder).addStatement(
            ListStatements.DeleteItem(this, index.lower())
        )
    }
}

@StandardLibraryScratchDSL
class DSLVariable(val internal: ScratchVariable) : DSLExpression() {
    override fun lower() = ListExpressions.Variable(this.internal)
}

interface DSLVariableExprs {
    fun DSLVariable.set(expr: DSLExpression) {
        (this@DSLVariableExprs as CodeBuilder).addStatement(
            VariableStatements.SetVariableTo(this.internal, expr.lower())
        )
    }

    fun DSLVariable.changeBy(value: DSLExpression) {
        (this@DSLVariableExprs as CodeBuilder).addStatement(
            VariableStatements.ChangeVariableBy(this.internal, value.lower())
        )
    }
}