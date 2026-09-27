package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class IsTurboWarpOpcode : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "argument_reporter_boolean"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("VALUE", JSONArray(listOf("is TurboWarp?", JSONObject.NULL)))
        })
    }
}

//text.js
class StringsIdenticalOpcode(
    val operand1: ScratchValue,
    val operand2: ScratchValue
) : ScratchOpcode() {
    override val opcode = "strings_identical"
    override val asValue = ScratchBoolean(this)

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
    }
}

enum class StringsTextCase(val id: String) {
    UPPERCASE("uppercase"),
}

class StringsMenuTextCaseOpcode(val textCase: StringsTextCase) : ScratchOpcode() {
    override val opcode = "strings_menu_textCase"
    override val asValue = null
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("textCase", JSONArray(listOf(textCase.id, JSONObject.NULL)))
        })
    }
}

class StringsIsCaseOpcode(
    val string: ScratchValue,
    textCase: StringsTextCase
) : ScratchOpcode() {
    override val opcode = "strings_isCase"
    override val asValue = ScratchBoolean(this)
    val menu = StringsMenuTextCaseOpcode(textCase)

    init {
        takeOwnership(listOfNotNull(string.value, menu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("STRING", string.toOperand())
            put("TEXTCASE", JSONArray(listOf(1, menu.id)))
        })
    }
}

//math.js
class TrueFantomMathMoreOrEqualOpcode(
    val a: ScratchValue,
    val b: ScratchValue
) : ScratchOpcode() {
    override val opcode = "truefantommath_more_or_equal_block"
    override val asValue = ScratchBoolean(this)

    init {
        takeOwnership(listOfNotNull(a.value, b.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("A", a.toOperand())
            put("B", b.toOperand())
        })
    }
}

class TrueFantomMathLessOrEqualOpcode(
    val a: ScratchValue,
    val b: ScratchValue
) : ScratchOpcode() {
    override val opcode = "truefantommath_less_or_equal_block"
    override val asValue = ScratchBoolean(this)

    init {
        takeOwnership(listOfNotNull(a.value, b.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("A", a.toOperand())
            put("B", b.toOperand())
        })
    }
}

class TrueFantomMathNotEqualOpcode(
    val a: ScratchValue,
    val b: ScratchValue
) : ScratchOpcode() {
    override val opcode = "truefantommath_not_equal_block"
    override val asValue = ScratchBoolean(this)

    init {
        takeOwnership(listOfNotNull(a.value, b.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("A", a.toOperand())
            put("B", b.toOperand())
        })
    }
}

class TrueFantomMathExponentOpcode(
    val a: ScratchValue,
    val b: ScratchValue
) : ScratchOpcode() {
    override val opcode = "truefantommath_exponent_block"
    override val asValue = ScratchString(this)

    init {
        takeOwnership(listOfNotNull(a.value, b.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("A", a.toOperand())
            put("B", b.toOperand())
        })
    }
}

class TrueFantomMathRootOpcode(
    val a: ScratchValue,
    val b: ScratchValue
) : ScratchOpcode() {
    override val opcode = "truefantommath_root_block"
    override val asValue = ScratchString(this)

    init {
        takeOwnership(listOfNotNull(a.value, b.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("A", a.toOperand())
            put("B", b.toOperand())
        })
    }
}

//var-and-list.js
class ReadVariableTurboWarp(val varName: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_getVar"
    override val asValue = ScratchString(this)
    init { takeOwnership(listOfNotNull(varName.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("VAR", varName.toOperand())
        })
    }
}

class VarAndListGetValueOfListOpcode(val listName: ScratchValue, val index: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_getValueOfList"
    override val asValue = ScratchString(this)
    init { takeOwnership(listOfNotNull(listName.value, index.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("INDEX", index.toOperand())
        })
    }
}

class VarAndListGetIndexOfListOpcode(val listName: ScratchValue, val value: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_getIndexOfList"
    override val asValue = ScratchString(this)
    init { takeOwnership(listOfNotNull(listName.value, value.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("VALUE", value.toOperand())
        })
    }
}

class VarAndListLengthOpcode(val listName: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_length"
    override val asValue = ScratchString(this)
    init { takeOwnership(listOfNotNull(listName.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
        })
    }
}

class VarAndListContainsOpcode(val listName: ScratchValue, val value: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_listContains"
    override val asValue = ScratchBoolean(this)
    init { takeOwnership(listOfNotNull(listName.value, value.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("VALUE", value.toOperand())
        })
    }
}

class VarAndListAddOpcode(val listName: ScratchValue, val value: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_addValueInList"
    override val asValue = null
    init { takeOwnership(listOfNotNull(listName.value, value.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("VALUE", value.toOperand())
        })
    }
}

class VarAndListDeleteOpcode(val listName: ScratchValue, val index: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_deleteOfList"
    override val asValue = null
    init { takeOwnership(listOfNotNull(listName.value, index.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("INDEX", index.toOperand())
        })
    }
}

class VarAndListClearOpcode(val listName: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_clearList"
    override val asValue = null
    init { takeOwnership(listOfNotNull(listName.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
        })
    }
}

class VarAndListReplaceOpcode(val listName: ScratchValue, val index: ScratchValue, val value: ScratchValue) : ScratchOpcode() {
    override val opcode = "qxsckvarandlist_replaceOfList"
    override val asValue = null
    init { takeOwnership(listOfNotNull(listName.value, index.value, value.value)) }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("LIST", listName.toOperand())
            put("INDEX", index.toOperand())
            put("VALUE", value.toOperand())
        })
    }
}