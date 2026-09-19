package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.EventListenerFunction
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentBoolean
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentString
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import org.json.JSONArray
import org.json.JSONObject

class ScratchASTFunction(
    val name: String,
    val args: List<ScratchFuncArgument>,
    val code: MutableList<ScratchStatement> = mutableListOf(),
    val runWithoutScreenRefresh: Boolean = true
) {
    val internal = ScratchFunction(
        name, null,
        runWithoutScreenRefresh = runWithoutScreenRefresh,
        arguments = args.map { it.internal },
    )
}

class ScratchASTEventListener(
    event: EventListener,
    code: List<ScratchStatement> = listOf()
) {
    val internal = EventListenerFunction(
        first = compile(code),
        eventType = event
    )
}

class ScratchFuncArgument(
    val name: String,
    val type: ScratchType
) {
    val internal = type.create(name)
}

enum class ScratchType(val create: (String) -> ScratchOpcode) {
    ANY({
        ProcedureArgumentString(it)
    }),
    BOOL({
        ProcedureArgumentBoolean(it)
    })
}

class ScratchSprite(
    val name: String,
    var x: Number = 150,
    var y: Number = 150,
    var size: Number = 100,
    var direction: Number = 90,
    var visible: Boolean = true,
    var draggable: Boolean = false,
    var rotationStyle: String = "all around"
) {
    fun toJson(layerOrder: Int): JSONObject {
        val emptyCostume = JSONObject().apply {
            put("name", "costume1")
            put("bitmapResolution", 1)
            put("dataFormat", "svg")
            put("assetId", "cd21514d0531fdffb22204e0ec5ed84a")
            put("md5ext", "cd21514d0531fdffb22204e0ec5ed84a.svg")
            put("rotationCenterX", 0)
            put("rotationCenterY", 0)
        }

        return JSONObject().apply {
            put("isStage", false)
            put("name", name)
            put("variables", JSONObject())
            put("lists", JSONObject())
            put("broadcasts", JSONObject())
            put("blocks", JSONObject())
            put("comments", JSONObject())
            put("currentCostume", 0)
            put("costumes", JSONArray(listOf(emptyCostume)))
            put("sounds", JSONArray())
            put("volume", 100)
            put("layerOrder", layerOrder)
            put("visible", visible)
            put("x", x)
            put("y", y)
            put("size", size)
            put("direction", direction)
            put("draggable", draggable)
            put("rotationStyle", rotationStyle)
        }
    }
}