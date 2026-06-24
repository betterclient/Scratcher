package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.std.dsl.compile

object LooksLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "say") {
            val message = arg("message", Type.str)
            looks.say(message)
        }

        editor.compile(lib, "sayForSeconds") {
            val message = arg("message", Type.str)
            val seconds = arg("seconds", Type.float)
            looks.say(message, seconds)
        }
    }
}