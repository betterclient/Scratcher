package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class SensingOfObjectMenuOpcode : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_of_object_menu"
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("OBJECT", JSONArray().apply {
                put("Scratcher Worker Sprite")
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
            put("OBJECT", JSONArray(listOf(1, objectMenu.id)))
        })
        base.put("fields", JSONObject())
    }
}

class XPositionOfOpcode(val spriteName: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_of"
    val menu = SensingOfObjectMenuOpcode()

    init {
        takeOwnership(listOfNotNull(menu, spriteName.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().apply {
            put("PROPERTY", JSONArray().let {
                it.put("x position")
                it.put(JSONObject.NULL)
            })
        })
        base.put("inputs", JSONObject().apply {
            put("OBJECT", if (spriteName.value != null) JSONArray().let {
                it.put(3)
                it.put(spriteName.value!!.id)
                it.put(menu.id)
            } else spriteName.toOperand())
        })
    }
}

class IsTurboWarpOpcode : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "argument_reporter_boolean"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        //"fields":{"VALUE":["is TurboWarp?",null]}
        base.put("fields", JSONObject().apply {
            put("VALUE", JSONArray(listOf("is TurboWarp?", JSONObject.NULL)))
        })
    }
}