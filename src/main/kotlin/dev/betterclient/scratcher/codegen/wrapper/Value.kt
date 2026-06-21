package dev.betterclient.scratcher.codegen.wrapper

import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import org.json.JSONArray

abstract class ScratchValue {
    abstract var value: ScratchOpcode?
    abstract fun toOperand(): JSONArray
}

enum class ScratchAccess(val id: Int) {
    PARAMETER(1), VARIABLE(3)
}

class ScratchString(val access: ScratchAccess, override var value: ScratchOpcode?) : ScratchValue() {
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            access.id, value!!.id, JSONArray(listOf(10, ""))
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

class ScratchBoolean(val access: ScratchAccess, override var value: ScratchOpcode?) : ScratchValue() {
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            access.id, value!!.id, JSONArray(listOf(10, ""))
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

class ScratchRealNumber(val number: Double) : ScratchValue() {
    override var value: ScratchOpcode? = null
    override fun toOperand(): JSONArray {
        return JSONArray(listOf(
            1, JSONArray(listOf(4, number))
        ))
    }
}