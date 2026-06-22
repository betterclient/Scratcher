package dev.betterclient.scratcher.codegen.wrapper

import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import org.json.JSONArray

abstract class ScratchValue {
    abstract var value: ScratchOpcode?
    abstract fun toOperand(): JSONArray
}

class ScratchString(override var value: ScratchOpcode?) : ScratchValue() {
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            3, value!!.id, JSONArray(listOf(10, ""))
        ))
    }
}

class ScratchRealString(val real: String) : ScratchValue() {
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            1, JSONArray(listOf(10, real))
        ))
    }

    override var value: ScratchOpcode? = null
}

class ScratchBoolean(override var value: ScratchOpcode?) : ScratchValue() {
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            2, value!!.id
        ))
    }
}

class ScratchVariableValue(val variable: ScratchVariable) : ScratchValue() {
    override var value: ScratchOpcode? = null

    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            3, JSONArray(listOf(12, variable.name, variable.id)),
            JSONArray(listOf(10, ""))
        ))
    }
}