package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.PenStatements
import dev.betterclient.scratcher.std.dsl.compileInline

object PenLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(lib, "down") {
            PenStatements.PenDown()
        }
        compileInline(lib, "up") {
            PenStatements.PenUp()
        }
        compileInline(lib, "setColor", mutableListOf(Parameter("color", Type.float))) {
            PenStatements.SetColor(it[0])
        }
        compileInline(lib, "setSize", mutableListOf(Parameter("size", Type.float))) {
            PenStatements.SetSize(it[0])
        }
        compileInline(lib, "eraseAll") {
            PenStatements.EraseAll()
        }
    }
}