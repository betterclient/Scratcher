package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.ErrorReporter
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.ast.parser.code.Stage1Parser
import dev.betterclient.scratcher.cli.CompilationOptions
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.gc.RefCountGC
import dev.betterclient.scratcher.gc.ReleaseDeadTemporaries
import dev.betterclient.scratcher.optimize.Optimizations
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ArrayLib
import dev.betterclient.scratcher.sugar.Desugaring
import dev.betterclient.scratcher.translation.EntrypointReachability
import dev.betterclient.scratcher.translation.EntrypointTranslator
import dev.betterclient.scratcher.translation.FunctionStructureTranslator
import dev.betterclient.scratcher.translation.ScratchFunctionTranslator
import dev.betterclient.scratcher.translation.TopLevelVariableTranslator
import dev.betterclient.scratcher.translation.TranslateExports
import dev.betterclient.scratcher.translation.heap.ConvertToHeapAccess
import dev.betterclient.scratcher.translation.heap.ReParseLocalVariables
import dev.betterclient.scratcher.translation.visitor.CallExpressionLowering
import dev.betterclient.scratcher.translation.visitor.FunctionReachability
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.plus
import kotlin.collections.toMutableList

class Compiler(
    val startFile: File,
    val inputProj: File?,
    val outFile: File,
    val options: CompilationOptions,
    val print: (String) -> Unit,
    val error: (String) -> Unit
) {
    private val context = CompilationContext(options)
    val editor = openScratchEditorFromResource(
        inputProj?.inputStream() ?: Compiler::class.java.getResourceAsStream("/proj.sb3")!!
    )

    init {
        StandardLibASTGenerator.init(editor)
    }

    fun check(): String {
        return try {
            compileFiles(context, startFile)
            "No errors!"
        } catch (e: GeneralCompilerException) {
            e.message!!
        }
    }

    fun compile() {
        val ast = compileFiles(context, startFile)

        print("Reachability")
        val reachableEntrypoints = (EntrypointReachability().run(ast) + GCLib.gcFuncs()).toMutableList()
        val (reachableFunctions, _) = FunctionReachability(reachableEntrypoints).run(ast)

        print("Pre-Optimize")
        Optimizations.apply(reachableFunctions, context, println = print)

        print("Desugaring")
        Desugaring.apply(reachableFunctions, context, runDynamicDispatch = false)
        context.isPreOptimize = false

        val uniqueFunctions0 = reachableFunctions.distinct().toMutableList()
        reachableFunctions.clear()
        reachableFunctions.addAll(uniqueFunctions0)

        if (context.options.gc.refCountGC) {
            RefCountGC.run(context, reachableFunctions)
        }

        print("Post-optimize")
        Optimizations.apply(reachableFunctions, context, println = print)
        if (context.options.gc.markAndSweepGC) Optimizations.apply(StandardLibASTGenerator.gc, context, print = false, print)

        print("Exports")
        val (postReachableFuncs, reachableTopLevelVariables, reachableLists) = FunctionReachability(reachableEntrypoints).run(ast)
        reachableFunctions.clear()
        reachableFunctions.addAll(postReachableFuncs)
        TranslateExports(reachableFunctions, reachableEntrypoints).run()

        print("Top level variables")
        reachableLists.forEach { editor.addList(it.scratchList) }
        reachableTopLevelVariables.forEach { (variable, _) -> variable.defaultValue = null }

        val topLevelTranslator = TopLevelVariableTranslator()
        val scratchTopLevels = mutableMapOf<TLVariable, ScratchVariable>()

        val topLevelInit = topLevelTranslator.createFunction(reachableTopLevelVariables, reachableLists)
        reachableFunctions.add(topLevelInit)

        StandardLibASTGenerator.compilerLib.functions.add(topLevelInit)
        if (context.options.gc.refCountGC) {
            reachableFunctions.addAll(RefCountGC.instrument(context, listOf(topLevelInit)))
        }

        Desugaring.apply(mutableListOf(topLevelInit), context, runDynamicDispatch = false)
        val allDesugaredFunctions = (reachableFunctions + topLevelInit + StandardLibASTGenerator.lambdaLib.functions).toMutableList()
        Desugaring.applyDynamicDispatch(allDesugaredFunctions, context)
        reachableFunctions.addAll(StandardLibASTGenerator.lambdaLib.functions)
        reachableFunctions.addAll(StandardLibASTGenerator.dynamicDispatchLib.functions)
        Optimizations.apply(mutableListOf(topLevelInit), context, print = false, print) //optimize the top level init

        reachableFunctions.addAll(StandardLibASTGenerator.memoryLib.functions)
        reachableFunctions.addAll(StandardLibASTGenerator.exceptLib.functions)
        reachableFunctions.addAll(ArrayLib.arrayFuncs)

        GCLib.generate(reachableTopLevelVariables.keys.toList()) { variable ->
            scratchTopLevels.computeIfAbsent(variable) {
                topLevelTranslator.translate(variable).also { editor.addVariable(it) }
            }
        }
        reachableFunctions.addAll(StandardLibASTGenerator.memoryLib.functions)
        reachableFunctions.addAll(StandardLibASTGenerator.memLib.functions)

        val uniqueFunctions = reachableFunctions.distinct()
        reachableFunctions.clear()
        reachableFunctions.addAll(uniqueFunctions)

        print("Lower expressions")
        reachableFunctions.forEach { CallExpressionLowering(it).run() }
        reachableFunctions.forEach { it.returnType = PrimitiveType.Void }

        print("Re-parse locals")
        reachableFunctions.forEach { ReParseLocalVariables(it).run() }

        print("Release dead temporaries")
        ReleaseDeadTemporaries.run(reachableFunctions, RefCountGC.decFunctions(), RefCountGC.incFunction())

        val reachableFunctionsLocalCountsMap = ConvertToHeapAccess(reachableFunctions).run()

        val (nowReachable) = FunctionReachability(reachableEntrypoints).run(ast)
        reachableFunctions.clear()
        reachableFunctions.addAll(nowReachable)
        reachableFunctions.add(topLevelInit)

        //store it as a pair cause we need the original func for the code itself
        val translator = FunctionStructureTranslator()
        val scratchStubs = reachableFunctions.associateWith { translator.translate(it) }.filterValues { it != null }.mapValues { it.value!! }

        print("Translate code")
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

        print("Compile entrypoints")
        EntrypointTranslator(
            getFunctionLocalSize = { reachableFunctionsLocalCountsMap[it]!! },
            toScratch = { scratchStubs[it]!! },
            toScratchTL = { scratchTopLevels[it]!! },
            topLevelInit = scratchStubs[topLevelInit]!!,
            topLevelInitLocals = reachableFunctionsLocalCountsMap[topLevelInit]!!,
        ).translateAll(
            editor, reachableEntrypoints
        )

        if (context.options.gc.markAndSweepGC) GCLib.populateList(editor)

        print("Compile to scratch")
        scratchStubs.map { it.value }.forEach { editor.addFunction(it) }
        editor.writeTo(outFile)
    }

    private fun compileFiles(context: CompilationContext, sourceFile: File): ASTFile {
        val reporter = ErrorReporter()

        print("Initial parse")
        val ast = ASTReader(reporter, context, sourceFile.readText(), sourceFile.absolutePath).read()
        context.generateGCNames()
        StandardLibASTGenerator.generateFrom(context, ast)
        print("Code parse")
        Stage1Parser(context, ast).parse()
        print("Static Type Checking")
        TypeAnalysis(reporter, context, ast).run()

        reporter.flush()

        return ast
    }
}