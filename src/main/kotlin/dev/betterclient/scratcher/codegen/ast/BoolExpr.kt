package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.*
import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue

sealed class ScratchBoolExpression : ScratchExpression()
private val ScratchValue?.boolean: ScratchBoolean
    get() = this!! as ScratchBoolean

class SBoolParameterExpression(val parameter: ScratchFuncArgument) : ScratchBoolExpression() {
    init {
        if (parameter.type != ScratchType.BOOL) throw UnsupportedOperationException("${parameter.name} is not of bool type")
    }

    override fun lower() = parameter.internal.asValue!!
}

object BoolOperatorExpressions {
    class BinaryExpression(
        val operand1: ScratchExpression,
        val operand2: ScratchExpression,
        val binaryOperator: SBinaryOperator
    ) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return when(binaryOperator) {
                SBinaryOperator.EQUALS -> EqualsOpcode(operand1.lower(), operand2.lower())
                SBinaryOperator.GT -> GTOpcode(operand1.lower(), operand2.lower())
                SBinaryOperator.LT -> LTOpcode(operand1.lower(), operand2.lower())
                SBinaryOperator.STRING_CONTAINS -> ContainsOpcode(operand1.lower(), operand2.lower())

                SBinaryOperator.GTE -> NotOpcode(
                    LTOpcode(operand1.lower(), operand2.lower()).asValue
                )

                SBinaryOperator.LTE -> NotOpcode(
                    GTOpcode(operand1.lower(), operand2.lower()).asValue
                )
            }.asValue!!
        }
    }

    class SBoolComparisonExpressions(
        val operand1: ScratchBoolExpression,
        val operand2: ScratchBoolExpression,
        val operator: SBoolOperator
    ) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return when(operator) {
                SBoolOperator.AND -> AndOpcode(operand1.lower().boolean, operand2.lower().boolean)
                SBoolOperator.OR -> OrOpcode(operand1.lower().boolean, operand2.lower().boolean)
            }.asValue!!
        }
    }

    class SNotExpression(
        val operand1: ScratchBoolExpression
    ) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            if (operand1 is SNotExpression) {
                return operand1.operand1.lower()
            }

            return NotOpcode(operand1.lower().boolean).asValue
        }
    }
}

enum class SBinaryOperator {
    EQUALS, GT, LT, STRING_CONTAINS, GTE, LTE
}

enum class SBoolOperator {
    AND, OR
}

object SensingBoolExpressions {
   /* class TouchingObjectExpression(val mode: TouchingObjectMode) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return TouchingObjectOpcode(mode).asValue
        }
    }

    class TouchingColorExpression(val color: ScratchExpression) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return TouchingColorOpcode(color.lower()).asValue
        }
    }

    class ColorIsTouchingColorExpression(
        val color1: ScratchExpression,
        val color2: ScratchExpression
    ) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return ColorIsTouchingColorOpcode(color1.lower(), color2.lower()).asValue
        }
    }*/

    class KeyPressedExpression(val key: ScratchExpression) : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return KeyPressedOpcode(key.lower()).asValue
        }
    }

    class MousePressedExpression : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return MousePressedOpcode().asValue
        }
    }

    class IsOnlineExpression : ScratchBoolExpression() {
        override fun lower(): ScratchValue {
            return IsOnlineOpcode().asValue
        }
    }
}