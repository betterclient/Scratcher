package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchObject
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class ScratchVariable(
    val name: String
) : ScratchObject()

class SetVariableToOpcode(val variable: ScratchVariable, val data: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "data_setvariableto"
    init {
        takeOwnership(listOfNotNull(data.value))
    }

    override fun toJSON(base: JSONObject) {
        base.apply {
            put("inputs", JSONObject()
                .put("VALUE", data.toOperand())
            )
            put("fields", JSONObject().apply {
                put("VARIABLE", JSONArray().apply {
                    put(variable.name)
                    put(variable.id)
                })
            })
        }
    }
}

class ChangeVariableOpcode(
    val variable: ScratchVariable,
    val value: ScratchValue
) : ScratchOpcode() {
    override val opcode = "data_changevariableby"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(value.value))
    }

    override fun toJSON(base: JSONObject) {
        base.apply {
            put("fields", JSONObject().apply {
                put("VARIABLE", JSONArray().apply {
                    put(variable.name)
                    put(variable.id)
                })
            })
            put("inputs", JSONObject().apply {
                put("VALUE", value.toOperand())
            })
        }
    }
}