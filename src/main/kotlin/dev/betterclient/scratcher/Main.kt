package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.translation.ConvertToHeapAccess
import dev.betterclient.scratcher.translation.EntrypointReachability
import dev.betterclient.scratcher.translation.EntrypointTranslator
import dev.betterclient.scratcher.translation.FunctionExpressionLowering
import dev.betterclient.scratcher.translation.FunctionReachability
import dev.betterclient.scratcher.translation.FunctionStructureTranslator
import dev.betterclient.scratcher.translation.RemoveEmptyAllocations
import dev.betterclient.scratcher.translation.ReParseLocalVariables
import dev.betterclient.scratcher.translation.ScratchFunctionTranslator
import java.io.File

fun main() {
    val editor = openScratchEditorFromResource(
        ::main.javaClass.getResourceAsStream("/proj.sb3")!!
    )
    StandardLibASTGenerator.init(editor)

    val ast = compile(File("helloworld.sc"))

    println("Reachability")
    val reachableEntrypoints = EntrypointReachability().run(ast)
    val reachableFunctions = FunctionReachability(reachableEntrypoints).run()

    //TODO: Fix while bug

    println("Lower expressions")
    reachableFunctions.forEach { FunctionExpressionLowering(it).run() }
    reachableFunctions.forEach { it.returnType = Type.void }

    println("Re-parse locals")
    reachableFunctions.forEach { ReParseLocalVariables(it).run() }

    val reachableFunctionsLocalCountsMap = ConvertToHeapAccess(reachableFunctions).run()

    println("Remove empty allocations")
    reachableFunctions.forEach { RemoveEmptyAllocations(it, reachableFunctionsLocalCountsMap[it]?: 0).run() }

    reachableFunctions.addAll(StandardLibASTGenerator.memoryLib.functions) //make sure these are here
    reachableFunctions.addAll(StandardLibASTGenerator.exceptLib.functions)

    val translator = FunctionStructureTranslator()
    //store it as a pair cause we need the original func for the code itself
    val scratchStubs = reachableFunctions.associateWith { translator.translate(it) }

    println("Translate code")
    scratchStubs.forEach { (normalAST, scratchAST) -> ScratchFunctionTranslator(normalAST, scratchAST) { scratchStubs[it]!! }.run() }

    println("Compile entrypoints")
    EntrypointTranslator(
        getFunctionLocalSize = { reachableFunctionsLocalCountsMap[it]!! },
        toScratch = { scratchStubs[it]!! }
    ).translateAll(
        editor, reachableEntrypoints
    )

    println("Compile to scratch")
    scratchStubs.map { it.value }.forEach { editor.addFunction(it) }
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
