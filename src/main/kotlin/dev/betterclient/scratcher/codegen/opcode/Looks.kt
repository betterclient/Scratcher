package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchAccess
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONObject

class SayOpcode(val message: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_say"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(message.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("MESSAGE", message.toOperand())
        })
    }
}

class SayForSecsOpcode(val message: ScratchValue, val seconds: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_sayforsecs"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(message.value, seconds.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("MESSAGE", message.toOperand())
            put("SECS", seconds.toOperand())
        })
    }
}

class ThinkForSecsOpcode(val message: ScratchValue, val seconds: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_thinkforsecs"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(message.value, seconds.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("MESSAGE", message.toOperand())
            put("SECS", seconds.toOperand())
        })
    }
}

class ThinkOpcode(val message: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_think"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(message.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("MESSAGE", message.toOperand())
        })
    }
}

class ChangeSizeByOpcode(val by: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_changesizeby"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(by.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("CHANGE", by.toOperand())
        })
    }
}

class SetSizeToOpcode(val size: ScratchValue) : ScratchOpcode() {
    override val opcode = "looks_setsizeto"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(size.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("SIZE", size.toOperand())
        })
    }
}

class GetSizeOpcode : ScratchOpcode() {
    override val opcode = "looks_size"
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}