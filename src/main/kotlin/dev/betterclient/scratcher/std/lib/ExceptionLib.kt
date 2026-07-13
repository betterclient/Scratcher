package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.sc

object ExceptionLib {
    lateinit var panic: StandardLibASTFunction
    lateinit var assertNonNull: StandardLibASTFunction
    fun init(lib: ASTFile, editor: ScratchEditor) {
        panic = editor.compile(lib, "panic") {
            val message = arg("message", PrimitiveType.Str)
            control.stop(StopMode.OTHER_SCRIPTS_IN_SPRITE)
            sensing.ask(message)
            control.stop(StopMode.ALL)
        }

        assertNonNull = editor.compile(lib, "compiler@assertNonNull", userAccessible = false) {
            val value = arg("value", PrimitiveType.Integer)
            val errorMsg = arg("error", PrimitiveType.Str)
            val out = returnArg(PrimitiveType.Integer)

            control.ifElse(
                condition = value equals "-1".sc,
                thenBlock = {
                    call(panic, errorMsg)
                },
                elseBlock = {
                    MemoryLib.heap[out] = value
                }
            )
        }
    }
}