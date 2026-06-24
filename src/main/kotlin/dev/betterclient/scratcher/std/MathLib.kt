package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.MathOp

object MathLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "floor", warp = true) {
            val num = arg("number", Type.float)
            val returnArg = returnArg(Type.int)

            MemoryLib.heap[returnArg] = num.math(MathOp.FLOOR)
        }
    }
}