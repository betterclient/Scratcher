package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.code.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.gc.RefCountGC
import dev.betterclient.scratcher.optimize.Optimizations
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.sugar.Desugaring
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

    val (ast, context) = compile(File("test/helloworld.sc"))
    if (CompilationConstants.PRINT_STDLIB) {
        StandardLibASTGenerator.print()
    }

    println("Reachability")
    val reachableEntrypoints = EntrypointReachability().run(ast) + GCLib.gcFuncs()
    val (reachableFunctions, _) = FunctionReachability(reachableEntrypoints).run()

    println("Pre-Optimize")
    Optimizations.apply(reachableFunctions, context)

    println("Desugaring")
    Desugaring.apply(reachableFunctions, context)
    context.isPreOptimize = false

    val uniqueFunctions0 = reachableFunctions.distinct().toMutableList()
    reachableFunctions.clear()
    reachableFunctions.addAll(uniqueFunctions0)

    if (CompilationConstants.REFCOUNT_GC) {
        RefCountGC.run(context, reachableFunctions)
    }

    println("Post-optimize")
    Optimizations.apply(reachableFunctions, context)
    if (CompilationConstants.MARK_AND_SWEEP_GC) Optimizations.apply(StandardLibASTGenerator.gc, context, print = false)

    println("Top level variables")
    val (postReachableFuncs, reachableTopLevelVariables) = FunctionReachability(reachableEntrypoints).run()
    reachableFunctions.clear()
    reachableFunctions.addAll(postReachableFuncs)

    reachableTopLevelVariables.forEach { (variable, _) -> variable.defaultValue = null }

    val topLevelTranslator = TopLevelVariableTranslator()
    val scratchTopLevels = mutableMapOf<TLVariable, ScratchVariable>()

    val topLevelInit = topLevelTranslator.createFunction(reachableTopLevelVariables)
    reachableFunctions.add(topLevelInit)

    StandardLibASTGenerator.compilerLib.functions.add(topLevelInit)
    if (CompilationConstants.REFCOUNT_GC) {
        reachableFunctions.addAll(RefCountGC.instrument(context, listOf(topLevelInit)))
    }
    Optimizations.apply(mutableListOf(topLevelInit), context, print = false) //optimize the top level init

    GCLib.generate(reachableTopLevelVariables.keys.toList()) { variable ->
        scratchTopLevels.computeIfAbsent(variable) {
            topLevelTranslator.translate(variable).also { editor.addVariable(it) }
        }
    }

    val uniqueFunctions = reachableFunctions.distinct()
    reachableFunctions.clear()
    reachableFunctions.addAll(uniqueFunctions)

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
            original = normalAST,
            scratch = scratchAST,
            lookup = {
                scratchStubs[it]!!
            },
            lookupVar = { variable ->
                scratchTopLevels.computeIfAbsent(variable) {
                    topLevelTranslator.translate(variable).also { editor.addVariable(it) }
                }
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

    if (CompilationConstants.MARK_AND_SWEEP_GC) GCLib.populateList(editor)

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
