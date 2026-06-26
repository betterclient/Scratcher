package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class SensingOfObjectMenuOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_of_object_menu"
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("OBJECT", JSONArray().apply {
                put("_stage_")
                put(JSONObject.NULL)
            })
        })
    }
}

class ReadVariable(val name: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_of"
    private val objectMenu = SensingOfObjectMenuOpcode()

    init {
        takeOwnership(listOfNotNull(name.value, objectMenu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("PROPERTY", name.toOperand())
            put("OBJECT", ScratchString(objectMenu).toOperand())
        })
        base.put("fields", JSONObject())
    }
}