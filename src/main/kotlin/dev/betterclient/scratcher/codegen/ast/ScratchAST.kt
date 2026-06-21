package dev.betterclient.scratcher.codegen.ast

import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentBoolean
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentString
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode

class ScratchASTFunction(
    val name: String,
    args: List<ScratchFuncArgument>,
    val code: MutableList<ScratchStatement> = mutableListOf(),
    val runWithoutScreenRefresh: Boolean = false
) {
    private val _internal = ScratchFunction(
        name, null,
        runWithoutScreenRefresh = runWithoutScreenRefresh,
        arguments = args.map { it.internal },
    )

    val internal: ScratchFunction
        get() {
            _internal.first = compile(code)
            return _internal
        }
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