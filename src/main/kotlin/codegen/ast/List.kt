package dev.betterclient.codegen.ast

import org.json.JSONArray
import org.json.JSONObject

class ScratchList(
    val name: String
) : ScratchObject()

class ItemOfList(val list: ScratchList, val index: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode: String = "data_itemoflist"
    init {
        takeOwnership(listOfNotNull(index.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().put("INDEX",
            index.toOperand()
        ))
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

class LengthOfList(val list: ScratchList) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode: String = "data_lengthoflist"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

class AddToList(val list: ScratchList, val data: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode: String = "data_addtolist"
    init {
        takeOwnership(listOfNotNull(data.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().put("ITEM", data.toOperand()))
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

/**
 * IMPORTANT: the index is 1 indexed, index == 0 is invalid, index == 1 is the first item
 */
class ReplaceItemOfList(val list: ScratchList, val index: ScratchValue, val data: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "data_replaceitemoflist"

    init {
        takeOwnership(listOfNotNull(data.value, index.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject()
            .put("ITEM", data.toOperand())
            .put("INDEX", index.toOperand())
        )
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

/**
 * IMPORTANT: the index is 1 indexed, index == 0 is invalid, index == 1 is the first item
 */
class InsertItemAtList(val list: ScratchList, val index: ScratchValue, val data: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "data_insertatlist"

    init {
        takeOwnership(listOfNotNull(data.value, index.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject()
            .put("ITEM", data.toOperand())
            .put("INDEX", index.toOperand())
        )
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

class DeleteItemFromList(val list: ScratchList, val index: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "data_deleteoflist"

    init {
        takeOwnership(listOfNotNull(index.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject()
            .put("INDEX", index.toOperand())
        )
        base.put("fields", JSONObject().put("LIST", JSONArray(listOf(
            list.name,
            list.id
        ))))
    }
}

class IndexOfItemInList(
    val list: ScratchList,
    val item: ScratchValue
) : ScratchOpcode(null) {
    override val opcode = "data_itemnumoflist"

    override val asValue by lazy {
        ScratchString(
            ScratchAccess.VARIABLE,
            IndexOfItemInList(list, item)
        )
    }

    init {
        takeOwnership(listOfNotNull(item.value))
    }

    override fun toJSON(base: JSONObject) {
        base.apply {
            put("fields", JSONObject().apply {
                put("LIST", JSONArray().apply {
                    put(list.name)
                    put(list.id)
                })
            })
            put("inputs", JSONObject().apply {
                put("ITEM", item.toOperand())
            })
        }
    }
}