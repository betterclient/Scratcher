package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.LooksStatements
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline

object LooksLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(
            lib,
            "say",
            parameters = mutableListOf(Parameter("message", Type.str))
        ) { args ->
            LooksStatements.Say(args[0], null)
        }

        compileInline(
            lib,
            "say",
            parameters = mutableListOf(
                Parameter("message", Type.str),
                Parameter("seconds", Type.float)
            )
        ) { args ->
            LooksStatements.Say(args[0], args[1])
        }
    }
}