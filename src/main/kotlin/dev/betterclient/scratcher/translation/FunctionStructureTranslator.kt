package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.obfuscate

class FunctionStructureTranslator {
    fun translate(function: Function): ScratchASTFunction? {
        if (function is StandardLibASTFunction) return function.precompiledCode
        if (function is InlineStandardLibFunction) return null

        return ScratchASTFunction(
            name = obfuscate(scratchName(function)),
            args = function.parameters.map {
                ScratchFuncArgument(
                    name = obfuscate(it.name),
                    type = ScratchType.ANY
                )
            },
            runWithoutScreenRefresh = function.warp
        )
    }

    fun scratchName(function: Function): String {
        val base = "${function.sourceAST.simplePath}::${function.name}"
        val receiver = function.parameters.find { it.name == "this" }?.type
        return if (function.isReceiver && receiver != null) {
            "$base::${receiver.toSafeString()}"
        } else {
            base
        }
    }
}