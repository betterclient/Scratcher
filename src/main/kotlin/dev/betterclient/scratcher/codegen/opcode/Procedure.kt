package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.nextBlockPosition
import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import dev.betterclient.scratcher.except.GeneralCompilerException
import org.json.JSONArray
import org.json.JSONObject

class ProcedureDefinitionOpcode(
    val prototype: ProcedurePrototypeOpcode,
    next: ScratchOpcode? = null
) : ScratchOpcode(next) {
    override val opcode = "procedures_definition"
    override val asValue = null
    init {
        takeOwnership(listOf(prototype))
    }

    override fun toJSON(base: JSONObject) {
        base.apply {
            put("x", nextBlockPosition())
            put("y", nextBlockPosition())
            put("fields", JSONObject())
            put("inputs", JSONObject().apply {
                put("custom_block", JSONArray().also {
                    it.put(1)
                    it.put(prototype.id)
                })
            })
        }
    }
}

class ProcedurePrototypeOpcode(
    val procedureName: String,
    val warp: Boolean = false,
    private val arguments: List<ScratchOpcode> = listOf()
) : ScratchOpcode(null) {
    override val opcode = "procedures_prototype"
    override var shadow = true
    override val asValue = null
    val argIDS = arguments.map { getUniqueName() }

    init {
        if (arguments.filter { it is ProcedureArgumentString || it is ProcedureArgumentBoolean }.size != arguments.size) {
            throw GeneralCompilerException("Non arguments on the arguments field...")
        }
        takeOwnership(arguments)
        arguments.forEach { it.shadow = true }
    }

    override fun toJSON(base: JSONObject) {
        val inputsJson = JSONObject()
        for ((i, block) in arguments.withIndex()) {
            inputsJson.put(argIDS[i], JSONArray().apply { put(1); put(block.id) })
        }
        base.put("inputs", inputsJson)
        base.put("fields", JSONObject())

        base.put("mutation", JSONObject().apply {
            put("tagName", "mutation")
            put("children", JSONArray())
            put("proccode", getProcCode())
            put("argumentids", JSONArray(argIDS).toString())
            put("argumentnames", JSONArray(arguments.map {
                when (it) {
                    is ProcedureArgumentString -> it.argName
                    is ProcedureArgumentBoolean -> it.argName
                    else -> ""
                }
            }).toString())
            put("argumentdefaults", JSONArray(arguments.map {
                when (it) {
                    is ProcedureArgumentString -> ""
                    is ProcedureArgumentBoolean -> "false"
                    else -> ""
                }
            }).toString())
            put("warp", warp.toString())
        })
    }

    fun getProcCode(): String {
        var out = procedureName
        for (scratchOpcode in arguments) {
            out += " ${if (scratchOpcode is ProcedureArgumentString) "%s" else "%b"}"
        }
        return out
    }
}

class ProcedureArgumentString(
    val argName: String
) : ScratchOpcode(null) {
    override val opcode = "argument_reporter_string_number"
    override val asValue
        get() = ScratchString(
            ProcedureArgumentString(argName)
        )

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().also {
            it.put("VALUE", JSONArray().apply { put(argName); put(JSONObject.NULL)})
        })
        base.put("inputs", JSONObject())
    }
}

class ProcedureArgumentBoolean(
    val argName: String
) : ScratchOpcode(null) {
    override val opcode = "argument_reporter_boolean"
    override val asValue
        get() = ScratchBoolean(
            ProcedureArgumentBoolean(argName)
        )

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().also {
            it.put("VALUE", JSONArray().apply { put(argName); put(JSONObject.NULL)})
        })
        base.put("inputs", JSONObject())
    }
}

class ProcedureCallOpcode(
    val func: ScratchFunction,
    val args: List<ScratchValue>,
    next: ScratchOpcode? = null
) : ScratchOpcode(next) {
    override val opcode = "procedures_call"
    override val asValue = null
    init {
        takeOwnership(args.mapNotNull { it.value })
    }

    override fun toJSON(base: JSONObject) {
        val ids = func.parent.prototype.argIDS

        base.apply {
            put("shadow", false)
            put("fields", JSONObject())
            put("mutation", JSONObject().also {
                it.put("tagName", "mutation")
                it.put("children", JSONArray())
                it.put("proccode", func.parent.prototype.getProcCode())
                it.put("argumentids", JSONArray(ids).toString())
                it.put("warp", "${func.parent.prototype.warp}")
            })
            put("inputs", JSONObject().also {
                for ((id, value) in ids.zip(args)) {
                    it.put(id, value.toOperand())
                }
            })
        }
    }
}

class ProcedureCallReturningOpcode(
    val func: ScratchFunction,
    val args: List<ScratchValue>
) : ScratchOpcode(null) {
    override val opcode = "procedures_call"
    override val asValue = ScratchString(this)
    init {
        takeOwnership(args.mapNotNull { it.value })
    }

    override fun toJSON(base: JSONObject) {
        val ids = func.parent.prototype.argIDS

        base.apply {
            put("fields", JSONObject())
            put("mutation", JSONObject().also {
                it.put("tagName", "mutation")
                it.put("children", JSONArray())
                it.put("proccode", func.parent.prototype.getProcCode())
                it.put("argumentids", JSONArray(ids).toString())
                it.put("warp", "${func.parent.prototype.warp}")
                it.put("return", "1")
            })
            put("inputs", JSONObject().also {
                for ((id, value) in ids.zip(args)) {
                    it.put(id, value.toOperand())
                }
            })
        }
    }
}

class ProcedureReturn(
    val value: ScratchValue
) : ScratchOpcode(null) {
    override val opcode = "procedures_return"
    override val asValue = null

    init {
        takeOwnership(listOfNotNull(value.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("VALUE", value.toOperand())
        })
    }
}