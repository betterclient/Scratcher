package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class GotoXYOpcode(
    val x: ScratchValue,
    val y: ScratchValue,
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_gotoxy"

    init {
        takeOwnership(listOfNotNull(x.value, y.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("X", x.toOperand())
            put("Y", y.toOperand())
        })
    }
}

class GotoOpcode(
    mode: GotoMode
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_goto"
    val gotoMenu = GotoMenuOpcode(mode, "motion_goto_menu")
    init {
        takeOwnership(listOf(gotoMenu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("TO", JSONArray(listOf(1, gotoMenu.id)))
        })
    }
}

enum class GotoMode(val id: String) {
    MOUSE("_mouse_"), RANDOM("_random_")
}

class GlideSecToXYOpcode(
    val seconds: ScratchValue,
    val x: ScratchValue,
    val y: ScratchValue
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_glidesecstoxy"

    init {
        takeOwnership(listOfNotNull(seconds.value, x.value, y.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("SECS", seconds.toOperand())
            put("X", x.toOperand())
            put("Y", y.toOperand())
        })
    }
}

class TurnRightOpcode(val degrees: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_turnright"

    init {
        takeOwnership(listOfNotNull(degrees.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("DEGREES", degrees.toOperand())
        })
    }
}

class TurnLeftOpcode(val degrees: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_turnleft"

    init {
        takeOwnership(listOfNotNull(degrees.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("DEGREES", degrees.toOperand())
        })
    }
}

class GlideToOpcode(
    val seconds: ScratchValue,
    mode: GotoMode
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_glideto"
    val gotoMenu = GotoMenuOpcode(mode, "motion_glideto_menu")

    init {
        takeOwnership(listOfNotNull(gotoMenu, seconds.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("SECS", seconds.toOperand())
            put("TO", JSONArray(listOf(1, gotoMenu.id)))
        })
    }
}

class GotoMenuOpcode(
    val mode: GotoMode,
    override val opcode: String
) : ScratchOpcode() {
    override val asValue = null
    override var shadow = true
    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().apply {
            put("TO", JSONArray(listOf(mode.id, null)))
        })
        base.put("inputs", JSONObject())
    }
}

class SetXOpcode(val x: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_setx"
    init {
        takeOwnership(listOfNotNull(x.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("X", x.toOperand())
        })
    }
}

class SetYOpcode(val y: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_sety"
    init {
        takeOwnership(listOfNotNull(y.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject().apply {
            put("Y", y.toOperand())
        })
    }
}

class SetRotationStyleOpcode(val rotationStyle: RotationStyle) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_setrotationstyle"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("STYLE", JSONArray(listOf(
                rotationStyle.id, null
            )))
        })
    }
}

enum class RotationStyle(val id: String) {
    LEFT_RIGHT("left-right"),
    DONT_ROTATE("don't rotate"),
    ALL_AROUND("all around"),
}

class GetXPositionOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "motion_xposition"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}

class GetYPositionOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "motion_yposition"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}

class GetDirectionOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "motion_direction"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}