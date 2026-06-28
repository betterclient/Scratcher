package dev.betterclient.scratcher.codegen.opcode

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.nextBlockPosition
import dev.betterclient.scratcher.codegen.wrapper.ScratchObject
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.except.GeneralCompilerException
import dev.betterclient.scratcher.except.NotFoundException
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
        base.put("x", nextBlockPosition())
        base.put("y", nextBlockPosition())
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
        base.put("x", nextBlockPosition())
        base.put("y", nextBlockPosition())
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
    NUM9("9");

    companion object {
        fun from(eventArg: ScratcherLangParser.EventArgContext): Key {
            val text = eventArg.IDENTIFIER()?.text ?:  eventArg.literal()!!.text.removeSurrounding("\"")
            return Key.entries.find { it.id.equals(text, true) }
                ?: throw NotFoundException("Unknown key $text")
        }
    }
}