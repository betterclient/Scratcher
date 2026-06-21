package dev.betterclient.codegen.ast

import dev.betterclient.codegen.opcode.ProcedureDefinitionOpcode
import dev.betterclient.codegen.opcode.ProcedurePrototypeOpcode
import dev.betterclient.codegen.rand
import org.json.JSONObject

open class ScratchObject {
    val id = rand()
}

class ScratchFunction(
    name: String,
    var first: ScratchOpcode?,
    runWithoutScreenRefresh: Boolean = false,
    arguments: List<ScratchOpcode>
) {
    val parent: ProcedureDefinitionOpcode by lazy {
        ProcedureDefinitionOpcode(
            next = first,
            prototype = ProcedurePrototypeOpcode(
                procedureName = name,
                warp = runWithoutScreenRefresh,
                arguments = arguments,
            )
        )
    }
}

abstract class ScratchOpcode(
    var next: ScratchOpcode? = null
) : ScratchObject() {
    var parent: ScratchOpcode? = null
    abstract val asValue: ScratchValue?
    abstract val opcode: String
    open val shadow: Boolean = false
    open val alsoAdd: MutableList<ScratchOpcode> = mutableListOf() //when you need to add more

    protected abstract fun toJSON(base: JSONObject)

    fun toJson() = JSONObject().apply {
        put("next", next?.id?: JSONObject.NULL)
        put("parent", parent?.id?: JSONObject.NULL)
        put("opcode", opcode)

        put("topLevel", parent == null)
        put("shadow", shadow)
        toJSON(this)
    }

    protected fun takeOwnership(list: List<ScratchOpcode>) {
        alsoAdd.addAll(list)

        list.forEach { opcode ->
            opcode.parent = this
        }
    }
}

fun autoSetNext(list: List<ScratchOpcode>): ScratchOpcode? {
    for (i in 0 until list.size - 1) {
        list[i].next = list[i + 1]
        list[i + 1].parent = list[i]
    }
    return list.firstOrNull()
}

fun autoSetNext(vararg list: ScratchOpcode) = autoSetNext(list.toList())