package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class WaitOpcode(val duration: ScratchValue) : ScratchOpcode() {
    override val opcode = "control_wait"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(duration.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("DURATION", duration.toOperand())
        })
    }
}

class RepeatTimesOpcode(val amount: ScratchValue, val block: ScratchOpcode?) : ScratchOpcode() {
    override val opcode = "control_repeat"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(amount.value, block))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("TIMES", amount.toOperand())
            block?.let {
                put("SUBSTACK", JSONArray(listOf(2, it.id)))
            }
        })
    }
}


class IfThenOpcode(val condition: ScratchBoolean, val block: ScratchOpcode?) : ScratchOpcode() {
    override val opcode = "control_if"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(condition.value, block))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("CONDITION", JSONArray(listOf(2, condition.value?.id)))
            block?.let {
                put("SUBSTACK", JSONArray(listOf(2, it.id)))
            }
        })
    }
}

class IfElseOpcode(val condition: ScratchBoolean, val block: ScratchOpcode?, val elseBlock: ScratchOpcode?) : ScratchOpcode() {
    override val opcode = "control_if_else"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(condition.value, block, elseBlock))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("CONDITION", JSONArray(listOf(2, condition.value?.id)))
            block?.let {
                put("SUBSTACK", JSONArray(listOf(2, it.id)))
            }
            elseBlock?.let {
                put("SUBSTACK2", JSONArray(listOf(2, it.id)))
            }
        })
    }
}

class WaitUntilOpcode(val condition: ScratchBoolean) : ScratchOpcode() {
    override val opcode = "control_wait_until"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(condition.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("CONDITION", JSONArray(listOf(2, condition.value?.id)))
        })
    }
}

class RepeatUntilOpcode(val condition: ScratchBoolean, val block: ScratchOpcode?) : ScratchOpcode() {
    override val opcode = "control_repeat_until"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(condition.value, block))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("CONDITION", JSONArray(listOf(2, condition.value?.id)))
            block?.let {
                put("SUBSTACK", JSONArray(listOf(2, it.id)))
            }
        })
    }
}

class StopThisScriptOpcode : ScratchOpcode() {
    override val asValue = null
    override val opcode = "control_stop"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().apply {
            put("STOP_OPTION", JSONArray(listOf("this script", JSONObject.NULL)))
        })
        base.put("inputs", JSONObject())
    }
}