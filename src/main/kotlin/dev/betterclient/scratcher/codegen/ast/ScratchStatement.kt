package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.*
import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.autoSetNext

sealed class ScratchStatement {
    abstract fun lower(): List<ScratchOpcode>
}

fun compile(block: List<ScratchStatement>): ScratchOpcode? {
    return block.map { it.lower() }.reduceOrNull { a1, a2 -> a1 + a2 }?.let { autoSetNext(it) }
}

class CallFunction(
    val func: ScratchASTFunction,
    val args: List<ScratchExpression>
) : ScratchStatement() {
    init {
        func.args.zip(args).forEach { (theirs, ours) ->
            if (theirs.type == ScratchType.BOOL && ours !is ScratchBoolExpression) throw UnsupportedOperationException("Trying to pass non bool expression into bool argument while calling ${func.name}")
        }
    }

    override fun lower() = listOf(ProcedureCallOpcode(
        func.internal, args.map { it.lower() }
    ))
}

object ControlStatements {
    class Wait(val amount: ScratchExpression) : ScratchStatement() {
        override fun lower() = listOf(WaitOpcode(amount.lower()))
    }

    class RepeatTimes(
        val amount: ScratchExpression,
        val block: List<ScratchStatement>
    ) : ScratchStatement() {
        override fun lower() = listOf(RepeatTimesOpcode(amount.lower(), compile(block)))
    }

    class IfThen(
        val condition: ScratchBoolExpression,
        val block: List<ScratchStatement>
    ) : ScratchStatement() {
        override fun lower() = listOf(IfThenOpcode(condition.lower() as ScratchBoolean, compile(block)))
    }

    class IfElse(
        val condition: ScratchBoolExpression,
        val thenBlock: List<ScratchStatement>,
        val elseBlock: List<ScratchStatement>
    ) : ScratchStatement() {
        override fun lower() = listOf(IfElseOpcode(condition.lower() as ScratchBoolean, compile(thenBlock), compile(elseBlock)))
    }

    class WaitUntil(
        val condition: ScratchBoolExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(WaitUntilOpcode(condition.lower() as ScratchBoolean))
    }

    class RepeatUntil(
        val condition: ScratchBoolExpression,
        val block: List<ScratchStatement>
    ) : ScratchStatement() {
        override fun lower() = listOf(RepeatUntilOpcode(condition.lower() as ScratchBoolean, compile(block)))
    }

    class Stop(val mode: StopMode) : ScratchStatement() {
        override fun lower() = listOf(StopOpcode(mode))
    }
}

object VariableStatements {
    class SetVariableTo(
        val variable: ScratchVariable,
        val value: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(SetVariableToOpcode(variable, value.lower()))
    }

    class ChangeVariableBy(
        val variable: ScratchVariable,
        val value: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(ChangeVariableOpcode(variable, value.lower()))
    }
}

object ListStatements {
    class AddToList(
        val list: ScratchList,
        val item: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(AddToListOpcode(list, item.lower()))
    }

    class ReplaceItem(
        val list: ScratchList,
        val item: ScratchExpression,
        val index: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(ReplaceItemOfListOpcode(list, index.lower(), item.lower()))
    }

    class InsertItem(
        val list: ScratchList,
        val item: ScratchExpression,
        val index: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(InsertItemAtListOpcode(list, index.lower(), item.lower()))
    }

    class DeleteItem(
        val list: ScratchList,
        val index: ScratchExpression
    ) : ScratchStatement() {
        override fun lower() = listOf(DeleteItemFromListOpcode(list, index.lower()))
    }

    class ClearList(
        val list: ScratchList
    ) : ScratchStatement() {
        override fun lower() = listOf(ClearListOpcode(list))
    }
}

object LooksStatements {
    class Say(val text: ScratchExpression, val secs: ScratchExpression?) : ScratchStatement() {
        override fun lower() = listOf(if (secs == null) {
            SayOpcode(text.lower())
        } else {
            SayForSecsOpcode(text.lower(), secs.lower())
        })
    }

    class Think(val text: ScratchExpression, val secs: ScratchExpression?) : ScratchStatement() {
        override fun lower() = listOf(if (secs == null) {
            ThinkOpcode(text.lower())
        } else {
            ThinkForSecsOpcode(text.lower(), secs.lower())
        })
    }
}

object MotionStatements {
    sealed class GotoPosition {
        class XY(val x: ScratchExpression, val y: ScratchExpression) : GotoPosition()
        class X(val x: ScratchExpression) : GotoPosition()
        class Y(val y: ScratchExpression) : GotoPosition()
        class Mode(val mode: GotoMode) : GotoPosition()
    }

    class Goto(val goto: GotoPosition) : ScratchStatement() {
        override fun lower() = listOf(when(goto) {
            is GotoPosition.Mode -> GotoOpcode(goto.mode)
            is GotoPosition.X -> SetXOpcode(goto.x.lower())
            is GotoPosition.XY -> GotoXYOpcode(goto.x.lower(), goto.y.lower())
            is GotoPosition.Y -> SetYOpcode(goto.y.lower())
        })
    }

    class GlideTo(val goto: GotoPosition, val secs: ScratchExpression) : ScratchStatement() {
        override fun lower() = listOf(when(goto) {
            is GotoPosition.Mode -> GlideToOpcode(secs.lower(), goto.mode)
            is GotoPosition.XY -> GlideSecToXYOpcode(secs.lower(), goto.x.lower(), goto.y.lower())
            is GotoPosition.X -> GlideSecToXYOpcode(secs.lower(), goto.x.lower(), MotionExpressions.YPosition().lower())
            is GotoPosition.Y -> GlideSecToXYOpcode(secs.lower(), MotionExpressions.XPosition().lower(), goto.y.lower())
        })
    }

    class TurnLeft(val degrees: ScratchExpression) : ScratchStatement() {
        override fun lower() = listOf(TurnLeftOpcode(degrees.lower()))
    }

    class TurnRight(val degrees: ScratchExpression) : ScratchStatement() {
        override fun lower() = listOf(TurnRightOpcode(degrees.lower()))
    }

    class SetRotationStyle(val style: RotationStyle) : ScratchStatement() {
        override fun lower() = listOf(SetRotationStyleOpcode(style))
    }
}

object SensingStatements {
    class Ask(val message: ScratchExpression) : ScratchStatement() {
        override fun lower() = listOf(AskAndWaitOpcode(message.lower()))
    }

    class ResetTimer : ScratchStatement() {
        override fun lower() = listOf(ResetTimerOpcode())
    }
}