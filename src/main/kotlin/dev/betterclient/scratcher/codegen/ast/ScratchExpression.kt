package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.AddOpcode
import dev.betterclient.scratcher.codegen.opcode.AnswerOpcode
import dev.betterclient.scratcher.codegen.opcode.CalendarMenu
import dev.betterclient.scratcher.codegen.opcode.CurrentCalendar
import dev.betterclient.scratcher.codegen.opcode.DaysSince2000Opcode
import dev.betterclient.scratcher.codegen.opcode.DistanceToMouseOpcode
import dev.betterclient.scratcher.codegen.opcode.DivideOpcode
import dev.betterclient.scratcher.codegen.opcode.GetDirectionOpcode
import dev.betterclient.scratcher.codegen.opcode.GetMouseXOpcode
import dev.betterclient.scratcher.codegen.opcode.GetMouseYOpcode
import dev.betterclient.scratcher.codegen.opcode.GetSizeOpcode
import dev.betterclient.scratcher.codegen.opcode.GetTimerOpcode
import dev.betterclient.scratcher.codegen.opcode.GetXPositionOpcode
import dev.betterclient.scratcher.codegen.opcode.GetYPositionOpcode
import dev.betterclient.scratcher.codegen.opcode.IndexOfItemInListOpcode
import dev.betterclient.scratcher.codegen.opcode.ItemOfListOpcode
import dev.betterclient.scratcher.codegen.opcode.JoinOpcode
import dev.betterclient.scratcher.codegen.opcode.LengthOfListOpcode
import dev.betterclient.scratcher.codegen.opcode.LengthOpcode
import dev.betterclient.scratcher.codegen.opcode.LetterOfOpcode
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.MathOpOpcode
import dev.betterclient.scratcher.codegen.opcode.ModOpcode
import dev.betterclient.scratcher.codegen.opcode.MultiplyOpcode
import dev.betterclient.scratcher.codegen.opcode.RandomOpcode
import dev.betterclient.scratcher.codegen.opcode.RoundOpcode
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.SubtractOpcode
import dev.betterclient.scratcher.codegen.opcode.UsernameOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchRealString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue

sealed class ScratchExpression {
    abstract fun lower(): ScratchValue
}

class ScratchLiteralStringExpression(val string: String) : ScratchExpression() {
    override fun lower() = ScratchRealString(string)
}

class ScratchStringParameterExpression(val parameter: ScratchFuncArgument) : ScratchExpression() {
    override fun lower() = parameter.internal.asValue!!
}

object OperatorExpressions {
    enum class BinaryOperator(val create: (ScratchValue, ScratchValue) -> ScratchOpcode) {
        ADD(::AddOpcode),
        SUBTRACT(::SubtractOpcode),
        MULTIPLY(::MultiplyOpcode),
        DIVIDE(::DivideOpcode),
        STRING_CONCAT(::JoinOpcode),
        MOD(::ModOpcode)
    }

    class BinaryExpression(
        val left: ScratchExpression,
        val right: ScratchExpression,
        val operator: BinaryOperator,
    ) : ScratchExpression() {
        override fun lower() = operator.create(left.lower(), right.lower()).asValue!!
    }

    //both inclusive
    class Random(
        val from: ScratchExpression,
        val to: ScratchExpression
    ) : ScratchExpression() {
        override fun lower() = RandomOpcode(from.lower(), to.lower()).asValue
    }

    class StringLetterAt(
        val str: ScratchExpression,
        val index: ScratchExpression
    ) : ScratchExpression() {
        override fun lower() = LetterOfOpcode(str.lower(), index.lower()).asValue
    }

    class StringLength(
        val str: ScratchExpression
    ) : ScratchExpression() {
        override fun lower() = LengthOpcode(str.lower()).asValue
    }

    class RoundNumber(
        val num: ScratchExpression
    ) : ScratchExpression() {
        override fun lower() = RoundOpcode(num.lower()).asValue
    }

    class MathOperation(
        val operation: MathOp,
        val num: ScratchExpression
    ) : ScratchExpression() {
        override fun lower() = MathOpOpcode(operation, num.lower()).asValue
    }
}

object MotionExpressions {
    class XPosition : ScratchExpression() {
        override fun lower() = GetXPositionOpcode().asValue
    }

    class YPosition : ScratchExpression() {
        override fun lower() = GetYPositionOpcode().asValue
    }

    class Direction : ScratchExpression() {
        override fun lower() = GetDirectionOpcode().asValue
    }
}

object SensingExpressions {
    sealed class SensingData(val create: () -> ScratchOpcode) {
        object Answer : SensingData(::AnswerOpcode)
        object DistanceToMouse : SensingData(::DistanceToMouseOpcode)
        object MouseX : SensingData(::GetMouseXOpcode)
        object MouseY : SensingData(::GetMouseYOpcode)
        object Timer : SensingData(::GetTimerOpcode)
        class CalendarData(val data: CalendarMenu) : SensingData({ CurrentCalendar(data) })
        object DaysSince2000 : SensingData(::DaysSince2000Opcode)
        object Username : SensingData(::UsernameOpcode)
    }

    class SenseExpression(val data: SensingData) : ScratchExpression() {
        override fun lower(): ScratchValue {
            return data.create().asValue!!
        }
    }
}

object SLooksExpressions {
    class Size : ScratchExpression() {
        override fun lower() = GetSizeOpcode().asValue
    }
}

object ListExpressions {
    class ItemAtIndex(val list: ScratchList, val index: ScratchExpression) : ScratchExpression() {
        override fun lower() = ItemOfListOpcode(list, index.lower()).asValue
    }

    class LengthOfList(val list: ScratchList) : ScratchExpression() {
        override fun lower() = LengthOfListOpcode(list).asValue
    }

    class IndexOfItemInList(val list: ScratchList, val item: ScratchExpression) : ScratchExpression() {
        override fun lower() = IndexOfItemInListOpcode(list, item.lower()).asValue
    }
}