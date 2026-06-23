package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchType

class FunctionStructureTranslator {
    fun translate(function: Function): ScratchASTFunction {
        return ScratchASTFunction(
            name = function.name,
            args = function.parameters.map {
                ScratchFuncArgument(
                    name = it.name,
                    type = if (it.type == Type.bool) ScratchType.BOOL else ScratchType.ANY
                )
            },
            runWithoutScreenRefresh = true
        )
    }
}