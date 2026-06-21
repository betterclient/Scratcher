package dev.betterclient.codegen.opcode

import dev.betterclient.codegen.ast.ScratchAccess
import dev.betterclient.codegen.ast.ScratchBoolean
import dev.betterclient.codegen.ast.ScratchOpcode
import dev.betterclient.codegen.ast.ScratchString
import dev.betterclient.codegen.ast.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

//math

class AddOpcode(val num1: ScratchValue, val num2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_add"

    init {
        takeOwnership(listOfNotNull(num1.value, num2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM1", num1.toOperand())
            put("NUM2", num2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class SubtractOpcode(val num1: ScratchValue, val num2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_subtract"

    init {
        takeOwnership(listOfNotNull(num1.value, num2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM1", num1.toOperand())
            put("NUM2", num2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class MultiplyOpcode(val num1: ScratchValue, val num2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_multiply"

    init {
        takeOwnership(listOfNotNull(num1.value, num2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM1", num1.toOperand())
            put("NUM2", num2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class DivideOpcode(val num1: ScratchValue, val num2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_divide"

    init {
        takeOwnership(listOfNotNull(num1.value, num2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM1", num1.toOperand())
            put("NUM2", num2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class RandomOpcode(val from: ScratchValue, val to: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_random"

    init {
        takeOwnership(listOfNotNull(from.value, to.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("FROM", from.toOperand())
            put("TO", to.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

//comparison

class GTOpcode(val operand1: ScratchValue, val operand2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_gt"

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class LTOpcode(val operand1: ScratchValue, val operand2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_lt"

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class EqualsOpcode(val operand1: ScratchValue, val operand2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_equals"

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

//logical

class AndOpcode(val operand1: ScratchBoolean, val operand2: ScratchBoolean) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_and"

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class OrOpcode(val operand1: ScratchBoolean, val operand2: ScratchBoolean) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_or"

    init {
        takeOwnership(listOfNotNull(operand1.value, operand2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND1", operand1.toOperand())
            put("OPERAND2", operand2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class NotOpcode(val operand: ScratchBoolean) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_not"

    init {
        takeOwnership(listOfNotNull(operand.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("OPERAND", operand.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

//string

class JoinOpcode(val string1: ScratchValue, val string2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_join"

    init {
        takeOwnership(listOfNotNull(string1.value, string2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("STRING1", string1.toOperand())
            put("STRING2", string2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class LetterOfOpcode(val letter: ScratchValue, val string: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_letter_of"

    init {
        takeOwnership(listOfNotNull(letter.value, string.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("LETTER", letter.toOperand())
            put("STRING", string.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class LengthOpcode(val string: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_length"

    init {
        takeOwnership(listOfNotNull(string.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("STRING", string.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class ContainsOpcode(val string1: ScratchValue, val string2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_contains"

    init {
        takeOwnership(listOfNotNull(string1.value, string2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("STRING1", string1.toOperand())
            put("STRING2", string2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

//math
class ModOpcode(val num1: ScratchValue, val num2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_mod"

    init {
        takeOwnership(listOfNotNull(num1.value, num2.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM1", num1.toOperand())
            put("NUM2", num2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class RoundOpcode(val num: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_round"

    init {
        takeOwnership(listOfNotNull(num.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM", num.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

enum class MathOp(val id: String) {
    ABS("abs"),
    FLOOR("floor"),
    CEILING("ceiling"),
    SQRT("sqrt"),
    SIN("sin"),
    COS("cos"),
    TAN("tan"),
    ASIN("asin"),
    ACOS("acos"),
    ATAN("atan"),
    LN("ln"),
    LOG("log"),
    E_POW("e ^"),
    TEN_POW("10 ^");
}

class MathOpOpcode(val operator: MathOp, val num: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "operator_mathop"

    init {
        takeOwnership(listOfNotNull(num.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("NUM", num.toOperand())
        })
        base.put("fields", JSONObject().apply {
            put("OPERATOR", JSONArray(listOf(operator.id, null)))
        })
    }
}