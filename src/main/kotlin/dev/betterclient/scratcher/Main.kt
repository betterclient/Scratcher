package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchRealString
import dev.betterclient.scratcher.codegen.wrapper.autoSetNext
import dev.betterclient.scratcher.codegen.opcode.*
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import java.io.File

fun main() {
    val editor = openScratchEditorFromResource(
        ::main.javaClass.getResourceAsStream("/proj.sb3")!!
    )

    val arg = ProcedureArgumentBoolean("hello bool")
    val arg2 = ProcedureArgumentString("hello string")
    val func = ScratchFunction(
        "hello world!",
        first = null,
        runWithoutScreenRefresh = true,
        arguments = listOf(arg, arg2)
    )

    func.first = autoSetNext(
        ProcedureCallOpcode(
            func,
            listOf(arg.asValue, arg2.asValue)
        ),
        WaitOpcode(
            ScratchRealString(
                "1"
            )
        ),
        RepeatTimesOpcode(
            arg2.asValue, autoSetNext(
                WaitOpcode(
                    ScratchRealString(
                        "22"
                    )
                )
            )
        )
    )

    editor.addFunction(func)

    editor.writeTo(File("out.sb3"))
}

fun compile(sourceFile: File): ASTFile {
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, sourceFile.readText(), sourceFile.absolutePath).read()
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    return ast
}
