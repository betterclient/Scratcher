package dev.betterclient

import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.parser.ASTReader
import dev.betterclient.ast.parser.CompilationContext
import dev.betterclient.ast.parser.Stage1Parser
import dev.betterclient.ast.parser.TypeAnalysis
import dev.betterclient.codegen.ast.AddToList
import dev.betterclient.codegen.ast.DeleteItemFromList
import dev.betterclient.codegen.ast.InsertItemAtList
import dev.betterclient.codegen.ast.ItemOfList
import dev.betterclient.codegen.ast.LengthOfList
import dev.betterclient.codegen.ast.ProcedureArgumentBoolean
import dev.betterclient.codegen.ast.ProcedureArgumentString
import dev.betterclient.codegen.ast.ProcedureCallOpcode
import dev.betterclient.codegen.ast.ReplaceItemOfList
import dev.betterclient.codegen.ast.ScratchFunction
import dev.betterclient.codegen.ast.ScratchList
import dev.betterclient.codegen.ast.ScratchRealString
import dev.betterclient.codegen.ast.ScratchVariable
import dev.betterclient.codegen.ast.autoSetNext
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
        ProcedureCallOpcode(func, listOf(arg, arg2)),
        AddToList(myList, ScratchRealString("67")),
        AddToList(myList, ScratchRealString("657")),
        AddToList(myList, ScratchRealString("675")),
        ReplaceItemOfList(myList, LengthOfList(myList).asValue, ItemOfList(myList, ScratchRealString("1")).asValue),
        InsertItemAtList(myList, ScratchRealString("1"), ItemOfList(myList, ScratchRealString("2")).asValue),
        DeleteItemFromList(myList, ScratchRealString("2")),
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
