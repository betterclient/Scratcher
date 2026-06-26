package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.optimize.Optimizations
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib
import dev.betterclient.scratcher.translation.ConvertToHeapAccess
import dev.betterclient.scratcher.translation.EntrypointReachability
import dev.betterclient.scratcher.translation.EntrypointTranslator
import dev.betterclient.scratcher.translation.FunctionExpressionLowering
import dev.betterclient.scratcher.translation.FunctionReachability
import dev.betterclient.scratcher.translation.FunctionStructureTranslator
import dev.betterclient.scratcher.translation.RemoveEmptyAllocations
import dev.betterclient.scratcher.translation.ReParseLocalVariables
import dev.betterclient.scratcher.translation.ScratchFunctionTranslator
import dev.betterclient.scratcher.translation.TopLevelVariableTranslator
import java.io.File

fun main() {
    val startTime = System.currentTimeMillis()
    val editor = openScratchEditorFromResource(
        ::main.javaClass.getResourceAsStream("/proj.sb3")!!
    )
    StandardLibASTGenerator.init(editor)

    val ast = compile(File("helloworld.sc"))

    println("Optimizations")
    Optimizations.apply(ast)

    println("Reachability")
    val reachableEntrypoints = EntrypointReachability().run(ast)
    val reachableTopLevelVariables = (reachableEntrypoints.asSequence()
        .flatMap { it.sourceAST.variables.asSequence() }
        .plus(StandardLibASTGenerator.optimizationsLib.variables.asSequence())
        .associateWith { it.defaultValue })
    reachableTopLevelVariables.forEach { (variable, _) -> variable.defaultValue = null } //clear default values as we already know them
    val reachableFunctions = FunctionReachability(reachableEntrypoints).run()

    println("Top level variables")
    val topLevelTranslator = TopLevelVariableTranslator()
    val scratchTopLevels = reachableTopLevelVariables.map { (variable, _) -> variable to topLevelTranslator.translate(variable) }.toMap().toMutableMap()
    scratchTopLevels.forEach { (_, scratch) -> editor.addVariable(scratch) }
    val topLevelInit = topLevelTranslator.createFunction(reachableTopLevelVariables)
    reachableFunctions.add(topLevelInit)

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
    val scratchStubs = reachableFunctions.associateWith { translator.translate(it) }.filterValues { it != null }.mapValues { it.value!! }

    println("Translate code")
    scratchStubs.forEach { (normalAST, scratchAST) ->
        ScratchFunctionTranslator(
            original = normalAST,
            scratch = scratchAST,
            lookup = {
                scratchStubs[it]!!
            },
            lookupVar = {
                scratchTopLevels[it]!!
            }
        ).run()
    }

    println("Compile entrypoints")
    EntrypointTranslator(
        getFunctionLocalSize = { reachableFunctionsLocalCountsMap[it]!! },
        toScratch = { scratchStubs[it]!! },
        topLevelInit = scratchStubs[topLevelInit]!!,
        topLevelInitLocals = reachableFunctionsLocalCountsMap[topLevelInit]!!,
    ).translateAll(
        editor, reachableEntrypoints
    )

    println("Compile to scratch")
    scratchStubs.map { it.value }.forEach { editor.addFunction(it) }
    editor.writeTo(File("out.sb3"))
    println("Compilation successful in ${System.currentTimeMillis() - startTime}ms")
}

fun compile(sourceFile: File): ASTFile {
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, sourceFile.readText(), sourceFile.absolutePath).read()
    MemoryLib.initMem(StandardLibASTGenerator.memLib, ast) //generate alloc(struct)
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    return ast
}
