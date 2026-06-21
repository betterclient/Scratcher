package dev.betterclient.codegen.ast

import org.json.JSONArray
import org.json.JSONObject

class GotoXY(
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

class Goto(
    mode: GotoMode
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_goto"
    val gotoMenu = GotoMenu(mode, "motion_goto_menu")
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

class GlideSecToXY(
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

class TurnRight(val degrees: ScratchValue) : ScratchOpcode() {
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

class TurnLeft(val degrees: ScratchValue) : ScratchOpcode() {
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

class GlideTo(
    val seconds: ScratchValue,
    mode: GotoMode
) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "motion_glideto"
    val gotoMenu = GotoMenu(mode, "motion_glideto_menu")

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

class GotoMenu(
    val mode: GotoMode,
    override val opcode: String
) : ScratchOpcode() {
    override val asValue = null
    override val shadow = true
    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject().apply {
            put("TO", JSONArray(listOf(mode.id, null)))
        })
        base.put("inputs", JSONObject())
    }
}

class SetX(val x: ScratchValue) : ScratchOpcode() {
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

class SetY(val y: ScratchValue) : ScratchOpcode() {
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

class SetRotationStyle(val rotationStyle: RotationStyle) : ScratchOpcode() {
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

class GetXPosition : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "motion_xposition"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}

class GetYPosition : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "motion_yposition"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}

class GetDirection : ScratchOpcode() {
    override val asValue = ScratchString(ScratchAccess.VARIABLE, this)
    override val opcode = "motion_direction"

    override fun toJSON(base: JSONObject) {
        base.put("fields", JSONObject())
        base.put("inputs", JSONObject())
    }
}