package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.EventListenerFunction
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentBoolean
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentString
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode

class ScratchASTFunction(
    val name: String,
    val args: List<ScratchFuncArgument>,
    val code: MutableList<ScratchStatement> = mutableListOf(),
    val runWithoutScreenRefresh: Boolean = false
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