package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class ScratchFunctionTranslator(
    val original: Function,
    val scratch: ScratchASTFunction
) {
    fun run() {
        if (StandardLibASTGenerator.isStandardLib(original)) return //these functions will get translated at call site


    }
}