package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor

object StandardLibASTGenerator {
    fun init(editor: ScratchEditor) {
        MemoryLibRewrite.init(memoryLib, editor)
        LooksLib.init(looksLib, editor)
    }

    val memoryLib = ASTFile(
        "mem"
    )

    val looksLib = ASTFile(
        "looks"
    )

    val lib = mapOf(
        "looks" to looksLib,
        "mem" to memoryLib
    )

    fun isStandardLib(func: Function): Boolean {
        return lib.map { it.value.functions }.reduce { a, b -> (a + b).toMutableList() }.contains(func)
    }
}

object LooksLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "say", warp = true) {
            val message = arg("message", Type.str)
            looks.say(message)
        }

        editor.compile(lib, "sayForSeconds", warp = true) {
            val message = arg("message", Type.str)
            val seconds = arg("seconds", Type.float)
            looks.say(message, seconds)
        }
    }
}