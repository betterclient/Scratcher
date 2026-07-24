package dev.betterclient.scratcher.codegen.wrapper

import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.codegen.opcode.ProcedureDefinitionOpcode
import dev.betterclient.scratcher.codegen.opcode.ProcedurePrototypeOpcode
import org.json.JSONObject

open class ScratchObject {
    val id = getUniqueName()
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
    open var shadow: Boolean = false
    open val alsoAdd: MutableList<ScratchOpcode> = mutableListOf() //when you need to add more

    open val isCapBlock: Boolean = false

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
        if (list[i].isCapBlock) {
            continue
        }
        list[i].next = list[i + 1]
        list[i + 1].parent = list[i]
    }
    return list.firstOrNull()
}

fun autoSetNext(vararg list: ScratchOpcode) = autoSetNext(list.toList())