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
        AskAndWaitOpcode(AddOpcode(ScratchRealString("5"), ScratchRealString("10")).asValue),
        AskAndWaitOpcode(SubtractOpcode(ScratchRealString("10"), ScratchRealString("5")).asValue),
        AskAndWaitOpcode(MultiplyOpcode(ScratchRealString("3"), ScratchRealString("4")).asValue),
        AskAndWaitOpcode(DivideOpcode(ScratchRealString("12"), ScratchRealString("4")).asValue),
        AskAndWaitOpcode(RandomOpcode(ScratchRealString("1"), ScratchRealString("10")).asValue),

        AskAndWaitOpcode(GTOpcode(ScratchRealString("15"), ScratchRealString("10")).asValue),
        AskAndWaitOpcode(LTOpcode(ScratchRealString("5"), ScratchRealString("10")).asValue),
        AskAndWaitOpcode(EqualsOpcode(ScratchRealString("apple"), ScratchRealString("apple")).asValue),

        AskAndWaitOpcode(
            AndOpcode(
                GTOpcode(ScratchRealString("15"), ScratchRealString("10")).asValue,
                LTOpcode(ScratchRealString("5"), ScratchRealString("10")).asValue
            ).asValue
        ),
        AskAndWaitOpcode(
            OrOpcode(
                GTOpcode(ScratchRealString("15"), ScratchRealString("10")).asValue,
                LTOpcode(ScratchRealString("10"), ScratchRealString("5")).asValue
            ).asValue
        ),
        AskAndWaitOpcode(
            NotOpcode(
                EqualsOpcode(ScratchRealString("apple"), ScratchRealString("banana")).asValue
            ).asValue
        ),

        AskAndWaitOpcode(JoinOpcode(ScratchRealString("apple "), ScratchRealString("banana")).asValue),
        AskAndWaitOpcode(LetterOfOpcode(ScratchRealString("1"), ScratchRealString("apple")).asValue),
        AskAndWaitOpcode(LengthOpcode(ScratchRealString("apple")).asValue),
        AskAndWaitOpcode(ContainsOpcode(ScratchRealString("apple"), ScratchRealString("a")).asValue),

        AskAndWaitOpcode(ModOpcode(ScratchRealString("10"), ScratchRealString("3")).asValue),
        AskAndWaitOpcode(RoundOpcode(ScratchRealString("4.6")).asValue),
        AskAndWaitOpcode(MathOpOpcode(MathOp.ABS, ScratchRealString("-10")).asValue),
        AskAndWaitOpcode(MathOpOpcode(MathOp.SQRT, ScratchRealString("9")).asValue),
        AskAndWaitOpcode(MathOpOpcode(MathOp.SIN, ScratchRealString("90")).asValue)
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
