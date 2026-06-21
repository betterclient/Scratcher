package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.SBoolParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchBoolExpression
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchLiteralStringExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchType
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

    val arg1 = ScratchFuncArgument("hello bool", ScratchType.BOOL)
    val arg2 = ScratchFuncArgument("hello string", ScratchType.ANY)
    val func = ScratchASTFunction(
        "hello world!",
        listOf(arg1, arg2),
        runWithoutScreenRefresh = true
    )

    val arg1e = SBoolParameterExpression(arg1)
    val arg2e = ScratchStringParameterExpression(arg2)
    func.code.addAll(listOf(
        CallFunction(func, listOf(
            arg1e,
            arg2e
        )),
        ControlStatements.Wait(ScratchLiteralStringExpression("1")),
        ControlStatements.RepeatTimes(arg2e, listOf(
            ControlStatements.Wait(ScratchLiteralStringExpression("22"))
        ))
    ))

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
