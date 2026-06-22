package dev.betterclient.scratcher.codegen.opcode

import dev.betterclient.scratcher.codegen.wrapper.ScratchBoolean
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.wrapper.ScratchString
import dev.betterclient.scratcher.codegen.wrapper.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

class AskAndWaitOpcode(val question: ScratchValue) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_askandwait"
    init {
        takeOwnership(listOfNotNull(question.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("QUESTION", question.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class AnswerOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_answer"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class TouchingObjectOpcode(mode: TouchingObjectMode) : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_touchingobject"
    val menu = TouchingObjectMenuOpcode(mode)
    init {
        takeOwnership(listOf(menu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("TOUCHINGOBJECTMENU", JSONArray(listOf(1, menu.id)))
        })
        base.put("fields", JSONObject())
    }
}

enum class TouchingObjectMode(val id: String) {
    MOUSE("_mouse_"),
    EDGE("_edge_")
}

class TouchingObjectMenuOpcode(val mode: TouchingObjectMode) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_touchingobjectmenu"
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("TOUCHINGOBJECTMENU", JSONArray(listOf(mode.id, null)))
        })
    }
}

class TouchingColorOpcode(val color: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_touchingcolor"

    init {
        takeOwnership(listOfNotNull(color.value))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("COLOR", color.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class ColorIsTouchingColorOpcode(val color1: ScratchValue, val color2: ScratchValue) : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_coloristouchingcolor"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("COLOR", color1.toOperand())
            put("COLOR2", color2.toOperand())
        })
        base.put("fields", JSONObject())
    }
}

class DistanceToMouseOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_distanceto"
    val menu = DistanceToMenu()
    init {
        takeOwnership(listOf(menu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("DISTANCETOMENU", JSONArray(listOf(1, menu.id)))
        })
        base.put("fields", JSONObject())
    }
}

class DistanceToMenu : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_distancetomenu"
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("DISTANCETOMENU", JSONArray(listOf("_mouse_", null)))
        })
    }
}

class KeyPressedOpcode(key: Key) : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_keypressed"
    val menu = KeyPressedMenu(key)

    init {
        takeOwnership(listOf(menu))
    }

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject().apply {
            put("KEY_OPTION", JSONArray(listOf(1, menu.id)))
        })
        base.put("fields", JSONObject())
    }
}

class KeyPressedMenu(val key: Key) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_keyoptions"
    override var shadow = true

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("KEY_OPTION", JSONArray(listOf(key.id, null)))
        })
    }
}

class MousePressedOpcode : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_mousedown"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class GetMouseXOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_mousex"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class GetMouseYOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_mousey"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class GetTimerOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_timer"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class ResetTimerOpcode : ScratchOpcode() {
    override val asValue = null
    override val opcode = "sensing_resettimer"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

enum class CalendarMenu(val id: String) {
    YEAR("YEAR"),
    MONTH("MONTH"),
    DAY("DATE"),
    DAYOFWEEK("DAYOFWEEK"),
    HOUR("HOUR"),
    MINUTE("MINUTE"),
    SECOND("SECOND")
}

class CurrentCalendar(val name: CalendarMenu) : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_current"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("CURRENTMENU", JSONArray(listOf(name.id, null)))
        })
    }
}

class DaysSince2000Opcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_dayssince2000"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class IsOnlineOpcode : ScratchOpcode() {
    override val asValue = ScratchBoolean(this)
    override val opcode = "sensing_online"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}

class UsernameOpcode : ScratchOpcode() {
    override val asValue = ScratchString(this)
    override val opcode = "sensing_username"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
    }
}