package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONObject

class PenDownOpcode : ScratchOpcode() {
    override val opcode = "pen_penDown"
    override val asValue = null

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class PenUpOpcode : ScratchOpcode() {
    override val opcode = "pen_penUp"
    override val asValue = null

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class PenClearOpcode : ScratchOpcode() {
    override val opcode = "pen_clear"
    override val asValue = null

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class PenSetColorOpcode(
    val color: ScratchValue
) : ScratchOpcode() {
    override val opcode = "pen_setPenColorToColor"
    override val asValue = null
    init {
        takeOwnership(listOfNotNull(color.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("COLOR", color.toOperand())
        })
        base.put("fields", JSONObject())
        base.put("shadow", false)
    }
}

class PenSetSizeOpcode(val size: ScratchValue) : ScratchOpcode() {
    override val opcode = "pen_setPenSizeTo"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(size.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("SIZE", size.toOperand())
        })
        base.put("fields", JSONObject())
        base.put("shadow", false)
    }
}