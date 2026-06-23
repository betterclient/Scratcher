package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.LooksStatements
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.codegen.rand

object StandardLibASTGenerator {
    fun init(editor: ScratchEditor) {
        memoryLib.functions.addAll(MemoryLibrary.generate(editor))
    }

    fun isStandardLib(func: Function): Boolean {
        return lib.map { it.value.functions }.reduce { a, b -> (a + b).toMutableList() }.contains(func)
    }

    val memoryLib = ASTFile(
        "mem",
        functions = mutableListOf()
    )

    val looksLib = ASTFile(
        "looks",
        functions = mutableListOf(
            ScratchFuncArgument(rand(), ScratchType.ANY).let {
                StandardLibASTFunction(
                    "say",
                    parameters = mutableListOf(
                        Parameter("message", Type.str)
                    ),
                    precompiledCode = ScratchASTFunction(
                        name = rand(),
                        args = listOf(it),
                        code = mutableListOf(
                            LooksStatements.Say(ScratchStringParameterExpression(it), null)
                        )
                    )
                )
            }
        )
    )

    val lib = mapOf(
        "looks" to looksLib,
        "mem" to memoryLib
    )
}