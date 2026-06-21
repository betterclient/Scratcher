package dev.betterclient.codegen.ast

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