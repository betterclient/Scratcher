package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.obfuscate

class FunctionStructureTranslator {
    fun translate(function: Function): ScratchASTFunction {
        if (function is StandardLibASTFunction) return function.precompiledCode

        return ScratchASTFunction(
            name = obfuscate(function.name),
            args = function.parameters.map {
                ScratchFuncArgument(
                    name = obfuscate(it.name),
                    type = if (it.type == Type.bool) ScratchType.BOOL else ScratchType.ANY
                )
            },
            runWithoutScreenRefresh = true
        )
    }
}