package dev.betterclient

import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.parser.ASTReader
import dev.betterclient.ast.parser.CompilationContext
import dev.betterclient.ast.parser.Stage1Parser
import dev.betterclient.ast.parser.TypeAnalysis
import dev.betterclient.codegen.ast.*
import dev.betterclient.codegen.opcode.ChangeSizeByOpcode
import dev.betterclient.codegen.opcode.GetSizeOpcode
import dev.betterclient.codegen.opcode.ProcedureArgumentBoolean
import dev.betterclient.codegen.opcode.ProcedureArgumentString
import dev.betterclient.codegen.opcode.ProcedureCallOpcode
import dev.betterclient.codegen.opcode.SayForSecsOpcode
import dev.betterclient.codegen.opcode.SayOpcode
import dev.betterclient.codegen.opcode.ScratchList
import dev.betterclient.codegen.opcode.ScratchVariable
import dev.betterclient.codegen.opcode.SetSizeToOpcode
import dev.betterclient.codegen.opcode.ThinkForSecsOpcode
import dev.betterclient.codegen.opcode.ThinkOpcode
import dev.betterclient.codegen.openScratchEditorFromResource
import java.io.File

fun main() {
    val editor = openScratchEditorFromResource(::main.javaClass.getResourceAsStream("/proj.sb3")!!)

    val myList = ScratchList("Hello")
    editor.addList(myList)
    val myVariable = ScratchVariable("Hi!")
    editor.addVariable(myVariable)

    val arg = ProcedureArgumentBoolean("hello bool")
    val arg2 = ProcedureArgumentString("hello string")
    val func = ScratchFunction(
        "hello world!",
        first = null,
        runWithoutScreenRefresh = true,
        arguments = listOf(arg, arg2)
    )

    func.first = autoSetNext(
        ProcedureCallOpcode(func, listOf(arg.asValue, arg2.asValue)),
        SayOpcode(ScratchRealString("hello world!")),
        SayForSecsOpcode(ScratchRealString("Hi world!"), ScratchRealString("5")),
        ThinkForSecsOpcode(ScratchRealString("i be thinking"), ScratchRealString("2")),
        ThinkOpcode(GetSizeOpcode().asValue),
        ChangeSizeByOpcode(ScratchRealString("5")),
        SetSizeToOpcode(ScratchRealString("100")),
    )

    editor.addFunction(func)

    editor.writeTo(File("out.sb3"))
}

fun compile(sourceFile: File): ASTFile {
    val source = File("helloworld.sc")
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, source.readText(), source.absolutePath).read()
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    return ast
}
