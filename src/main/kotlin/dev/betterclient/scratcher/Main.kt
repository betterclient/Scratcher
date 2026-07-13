package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.optimize.Optimizations
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.translation.*
import dev.betterclient.scratcher.translation.heap.ConvertToHeapAccess
import dev.betterclient.scratcher.translation.heap.ReParseLocalVariables
import dev.betterclient.scratcher.translation.visitor.CallExpressionLowering
import dev.betterclient.scratcher.translation.visitor.FunctionReachability
import dev.betterclient.scratcher.translation.visitor.RemoveEmptyAllocations
import java.io.File

fun main() {
    val startTime = System.currentTimeMillis()
    val editor = openScratchEditorFromResource(
        ::main.javaClass.getResourceAsStream("/proj.sb3")!!
    )
    StandardLibASTGenerator.init(editor)

    val (ast, context) = compile(File("helloworld.sc"))
    if (CompilationConstants.PRINT_STDLIB) {
        StandardLibASTGenerator.print()
    }

    println("Optimizations")
    Optimizations.apply(ast, context)
    if (!CompilationConstants.MANUAL_MEMORY) Optimizations.apply(StandardLibASTGenerator.gc, context, print = false)

    println("Reachability")
    val reachableEntrypoints = EntrypointReachability().run(ast) + GCLib.gcFuncs()
    val (reachableFunctions, reachableTopLevelVariables) = FunctionReachability(reachableEntrypoints).run()
    reachableTopLevelVariables.forEach { (variable, _) -> variable.defaultValue = null } //clear default values as we already know them

    println("Top level variables")
    val topLevelTranslator = TopLevelVariableTranslator()
    val scratchTopLevels = reachableTopLevelVariables.map { (variable, _) -> variable to topLevelTranslator.translate(variable) }.toMap().toMutableMap()
    scratchTopLevels.forEach { (_, scratch) -> editor.addVariable(scratch) }
    val topLevelInit = topLevelTranslator.createFunction(reachableTopLevelVariables)
    reachableFunctions.add(topLevelInit)
    GCLib.generate(reachableTopLevelVariables.keys.toList()) { scratchTopLevels[it]!! }

    println("Lower expressions")
    reachableFunctions.forEach { CallExpressionLowering(context, it).run() }
    reachableFunctions.forEach { it.returnType = PrimitiveType.Void }

    println("Re-parse locals")
    reachableFunctions.forEach { ReParseLocalVariables(it).run() }

    val reachableFunctionsLocalCountsMap = ConvertToHeapAccess(reachableFunctions).run()

    println("Remove empty allocations")
    reachableFunctions.forEach { RemoveEmptyAllocations(it, reachableFunctionsLocalCountsMap[it]?.first ?: 0).run() }

    reachableFunctions.addAll(StandardLibASTGenerator.memoryLib.functions) //make sure these are here
    reachableFunctions.addAll(StandardLibASTGenerator.exceptLib.functions)
    reachableFunctions.addAll(ListLib.listFuncs)

    val translator = FunctionStructureTranslator()
    //store it as a pair cause we need the original func for the code itself
    val scratchStubs = reachableFunctions.associateWith { translator.translate(it) }.filterValues { it != null }.mapValues { it.value!! }

    println("Translate code")
    scratchStubs.forEach { (normalAST, scratchAST) ->
        ScratchFunctionTranslator(
            compilationContext = context,
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

    GCLib.populateList(editor)

    println("Compile to scratch")
    scratchStubs.map { it.value }.forEach { editor.addFunction(it) }
    editor.writeTo(File("out.sb3"))
    println("Compilation successful in ${System.currentTimeMillis() - startTime}ms")
}

fun compile(sourceFile: File): Pair<ASTFile, CompilationContext> {
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, sourceFile.readText(), sourceFile.absolutePath).read()
    context.generateGCNames()
    StandardLibASTGenerator.generateFrom(ast)
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    return ast to context
}
