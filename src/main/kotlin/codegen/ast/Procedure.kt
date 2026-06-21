package dev.betterclient.codegen.ast

import dev.betterclient.codegen.rand
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
            put("x", 99)
            put("y", 99)
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
    override val asValue = null

    init {
        if (arguments.filter { it is ProcedureArgumentString || it is ProcedureArgumentBoolean }.size != arguments.size) {
            throw UnsupportedOperationException("Non arguments on the arguments field...")
        }
        takeOwnership(arguments)
    }

    override fun toJSON(base: JSONObject) {
        val inputsJson = JSONObject()
        val ids = arguments.map { rand() }
        for ((i, block) in arguments.withIndex()) {
            inputsJson.put(ids[i], JSONArray().apply { put(1); put(block.id) })
        }
        base.put("inputs", inputsJson)
        base.put("fields", JSONObject())

        base.put("mutation", JSONObject().apply {
            put("tagName", "mutation")
            put("children", JSONArray())
            put("proccode", getProcCode())
            put("argumentids", JSONArray(ids).toString())
            put("argumentnames", JSONArray(arguments.map {
                when (it) {
                    is ProcedureArgumentString -> it.argName
                    is ProcedureArgumentBoolean -> it.argName
                    else -> ""
                }
            }).toString())
            put("argumentdefaults", JSONArray(arguments.map {
                when (it) {
                    is ProcedureArgumentString -> it.argName
                    is ProcedureArgumentBoolean -> it.argName
                    else -> ""
                }
            }).toString())
            put("warp", warp)
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
    override val asValue by lazy { ScratchString(
        ScratchAccess.PARAMETER, ProcedureArgumentString(argName)
    ) }

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
    override val asValue by lazy { ScratchBoolean(
        ScratchAccess.PARAMETER, ProcedureArgumentBoolean(argName)
    ) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().also {
            it.put("VALUE", JSONArray().apply { put(argName); put(JSONObject.NULL)})
        })
        base.put("inputs", JSONObject())
    }
}

class ProcedureCallOpcode(
    val func: ScratchFunction,
    val args: List<ScratchOpcode>,
    next: ScratchOpcode? = null
) : ScratchOpcode(next) {
    override val opcode = "procedures_call"
    override val asValue = null
    init {
        takeOwnership(args.mapNotNull { it.asValue?.value })
    }

    override fun toJSON(base: JSONObject) {
        val ids = args.map { rand() }

        base.apply {
            put("shadow", false)
            put("fields", JSONObject())
            put("mutation", JSONObject().also {
                it.put("tagName", "mutation")
                it.put("children", JSONArray())
                it.put("proccode", func.parent.prototype.getProcCode())
                it.put("argumentids", JSONArray(ids).toString())
                it.put("warp", "false")
            })
            put("inputs", JSONObject().also {
                for ((id, value) in ids.zip(args)) {
                    it.put(id, value.asValue!!.toOperand())
                }
            })
        }
    }
}