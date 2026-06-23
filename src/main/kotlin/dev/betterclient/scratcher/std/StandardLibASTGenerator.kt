package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type

object StandardLibASTGenerator {
    fun isStandardLib(func: Function): Boolean {
        return lib.map { it.value.functions }.reduce { a, b -> (a + b).toMutableList() }.contains(func)
    }

    val looksLib = ASTFile(
        "looks",
        functions = mutableListOf()
    )

    init {
        looksLib.functions.add(
            Function(
                "say",
                parameters = mutableListOf(
                    Parameter("message", Type.str)
                ),
                returnType = Type.void
            )
        )
    }

    val lib = mapOf(
        "looks" to looksLib,
    )
}