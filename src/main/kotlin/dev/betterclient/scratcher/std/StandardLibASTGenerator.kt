package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type

object StandardLibASTGenerator {
    val stdLib = ASTFile(
        "std",
        functions = mutableListOf()
    )

    init {
        stdLib.functions.add(
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
        "std" to stdLib,
    )
}