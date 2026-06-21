package dev.betterclient

import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.parser.ASTReader
import dev.betterclient.ast.parser.CompilationContext
import dev.betterclient.ast.parser.Stage1Parser
import dev.betterclient.ast.parser.TypeAnalysis
import dev.betterclient.codegen.ast.ScratchFunction
import dev.betterclient.codegen.ast.ScratchRealString
import dev.betterclient.codegen.ast.autoSetNext
import dev.betterclient.codegen.opcode.*
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
        AskAndWaitOpcode(AnswerOpcode().asValue),
        AskAndWaitOpcode(TouchingObjectOpcode(TouchingObjectMode.EDGE).asValue),
        AskAndWaitOpcode(TouchingColorOpcode(ScratchRealString("#000000")).asValue),
        AskAndWaitOpcode(ColorIsTouchingColorOpcode(ScratchRealString("#000000"), ScratchRealString("#FFFFFF")).asValue),
        AskAndWaitOpcode(DistanceToMouseOpcode().asValue),
        AskAndWaitOpcode(KeyPressedOpcode(Key.A).asValue),
        AskAndWaitOpcode(MousePressedOpcode().asValue),
        AskAndWaitOpcode(GetMouseXOpcode().asValue),
        AskAndWaitOpcode(GetMouseYOpcode().asValue),
        AskAndWaitOpcode(GetTimerOpcode().asValue),
        ResetTimerOpcode(),
        AskAndWaitOpcode(CurrentCalendar(CalendarMenu.MONTH).asValue),
        AskAndWaitOpcode(DaysSince2000Opcode().asValue),
        AskAndWaitOpcode(IsOnlineOpcode().asValue),
        AskAndWaitOpcode(UsernameOpcode().asValue),
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
