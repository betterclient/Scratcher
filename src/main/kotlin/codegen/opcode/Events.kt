package dev.betterclient.codegen.opcode

import dev.betterclient.codegen.ast.ScratchObject
import dev.betterclient.codegen.ast.ScratchOpcode
import dev.betterclient.codegen.ast.ScratchValue
import org.json.JSONArray
import org.json.JSONObject

sealed class EventListener {
    object GreenFlag : EventListener()
    class KeyPressed(val key: Key) : EventListener()
}

class EventListenerFunction(
    val first: ScratchOpcode?,
    eventType: EventListener
) : ScratchObject() {
    val parent = when(eventType) {
        is EventListener.KeyPressed -> WhenKeyPressed(eventType.key)
        is EventListener.GreenFlag -> WhenGreenFlagClicked()
    }
    init {
        parent.next = first
    }
}

class WhenGreenFlagClicked : ScratchOpcode() {
    override val asValue = null
    override val opcode = "event_whenflagclicked"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject())
        base.put("x", 500)
        base.put("y", 500)
    }
}

class WhenKeyPressed(val key: Key) : ScratchOpcode() {
    override val asValue = null
    override val opcode = "event_whenkeypressed"

    override fun toJSON(base: JSONObject) {
        base.put("inputs", JSONObject())
        base.put("fields", JSONObject().apply {
            put("KEY_OPTION", JSONArray(listOf(key.id, null)))
        })
        base.put("x", 500)
        base.put("y", 500)
    }
}

enum class Key(val id: String) {
    SPACE("space"),
    UP_ARROW("up arrow"),
    DOWN_ARROW("down arrow"),
    LEFT_ARROW("left arrow"),
    RIGHT_ARROW("right arrow"),
    ANY("any"),
    A("a"),
    B("b"),
    C("c"),
    D("d"),
    E("e"),
    F("f"),
    G("g"),
    H("h"),
    I("i"),
    J("j"),
    K("k"),
    L("l"),
    M("m"),
    N("n"),
    O("o"),
    P("p"),
    Q("q"),
    R("r"),
    S("s"),
    T("t"),
    U("u"),
    V("v"),
    W("w"),
    X("x"),
    Y("y"),
    Z("z"),
    NUM0("0"),
    NUM1("1"),
    NUM2("2"),
    NUM3("3"),
    NUM4("4"),
    NUM5("5"),
    NUM6("6"),
    NUM7("7"),
    NUM8("8"),
    NUM9("9")
}