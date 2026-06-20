package dev.betterclient.std

import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.Function
import dev.betterclient.ast.Parameter
import dev.betterclient.ast.Type

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