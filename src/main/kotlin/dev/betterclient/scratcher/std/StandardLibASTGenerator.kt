package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.code.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.ast.parser.code.StringBoxing
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.std.dsl.compileInline
import dev.betterclient.scratcher.std.lib.*
import kotlin.system.exitProcess

object StandardLibASTGenerator {
    const val GC_LIB_NAME = "gc_impl"
    private var editor: ScratchEditor? = null
    fun init(editor: ScratchEditor) {
        ExceptionLib.init(exceptLib, editor)
        MemoryLib.init(memoryLib, editor)
        LooksLib.init(looksLib)
        MathLib.init(mathLib, editor)
        SensingLib.init(sensingLib)
        CalendarLib.init(calendarLib)
        CastLib.init(castLib, editor)
        PenLib.init(penLib)
        MotionLib.init(motionLib)
        UtilsLib.init(utilsLib)
        StringBoxing.init()
        this.editor = editor

        rawLibs
    }

    val looksLib = ASTFile("looks")
    val mathLib = ASTFile("math")
    val sensingLib = ASTFile("sensing")
    val calendarLib = ASTFile("calendar")
    val castLib = ASTFile("cast")
    val penLib = ASTFile("pen")
    val motionLib = ASTFile("motion")
    val utilsLib = ASTFile("utils")
    val listLib = ASTFile("list")
    val strLib = ASTFile("string")

    val memoryLib = ASTFile("memory")
    val globalPromotionLib = ASTFile("global_promotions")
    val dynamicDispatchLib = ASTFile("dynamic_dispatch")
    val refCountGC = ASTFile("ref_count_gc")
    val compilerLib = ASTFile("compiler")
    val gcInternalsLib = ASTFile("gc_internal")
    val gcLib = ASTFile("gc")
    val exceptLib = ASTFile("except")

    val lib = mutableMapOf(
        "looks" to looksLib,
        "sensing" to sensingLib,
        "memory" to memoryLib,
        "math" to mathLib,
        "except" to exceptLib,
        "cast" to castLib,
        "calendar" to calendarLib,
        "pen" to penLib,
        "motion" to motionLib,
        "utils" to utilsLib,
        "list" to listLib,
        "string" to strLib,
        "global_promotions" to globalPromotionLib,
        "dynamic_dispatch" to dynamicDispatchLib,
        "compiler" to compilerLib,
        "gc_internal" to gcInternalsLib,
        "ref_count_gc" to refCountGC,
        "gc" to gcLib
    )

    val memLib = ASTFile("mem").also {
        if (!CompilationConstants.MARK_AND_SWEEP_GC && !CompilationConstants.REFCOUNT_GC) lib["mem"] = it
    }

    val typeChecker by lazy {
        compile("/TypeChecker.sc", "typecheck").also {
            lib["typecheck"] = it
        }
    }

    val triangle by lazy {
        compile("/triangle.sc", "triangle").also {
            lib["triangle"] = it
        }
    }

    var bypassRestrictions = false
    val gc by lazy {
        bypassRestrictions = true //gc needs gc_internal
        val out = compile("/gc.sc", GC_LIB_NAME).also {
            if (CompilationConstants.MARK_AND_SWEEP_GC) lib[GC_LIB_NAME] = it
        }
        bypassRestrictions = false
        out
    }

    val rawLibs by lazy {
        listOf(typeChecker, triangle)
    }

    fun isRestricted(library: ASTFile): Boolean {
        if (bypassRestrictions) return false
        if(!CompilationConstants.MARK_AND_SWEEP_GC && library == gcLib) return true
        return library.path == "typecheck" ||
                library == memoryLib ||
                library == globalPromotionLib ||
                library == compilerLib ||
                library.path == GC_LIB_NAME ||
                library == gcInternalsLib ||
                library == dynamicDispatchLib ||
                library == refCountGC
    }

    fun isStandardLib(function: Function): Boolean {
        return function is StandardLibASTFunction || function is InlineStandardLibFunction
    }

    private fun compile(file: String, path: String): ASTFile {
        val context = CompilationContext()
        val ast = ASTReader(
            ctx = context,
            source = String(StandardLibASTGenerator::class.java.getResourceAsStream(file).use { it!!.readBytes() }),
            fullPath = path
        ).read()
        Stage1Parser(context, ast).parse()
        if (path == GC_LIB_NAME) TypeAnalysis(context, ast).run() //yea

        return ast
    }

    fun print() {
        lib.forEach { (name, ast) ->
            if (isRestricted(ast)) return@forEach
            println("Library: $name")
            ast.functions.forEach { func ->
                println("   Function: ${if (func is InlineStandardLibFunction) "inlined " else ""}${if(func.warp) "warp " else ""}${func.returnType} ${func.name} (${func.parameters.joinToString { "${it.type} ${it.name}" }})")
            }
            if (name == "mem") {
                println("   Function: warp free (AnyStruct val)")
            }
        }
        exitProcess(0)
    }

    fun generateFrom(startAST: ASTFile) {
        MemoryLib.initMem(memLib, startAST) //generate alloc(struct)
        ListLib.init(listLib, editor!!)
        GCLib.init(gcInternalsLib)
        StringLib.init(strLib, editor!!)
        gc
        GCLib.initCaller(gc, gcLib)
    }
}

object UtilsLib {
    fun init(lib: ASTFile) {
        compileInline(lib, "random", parameters = mutableListOf(
            Parameter("from", PrimitiveType.Float),
            Parameter("to", PrimitiveType.Float),
        ), returnType = PrimitiveType.Float) {
            OperatorExpressions.Random(it[0], it[1])
        }

        compileInline(lib, "wait", parameters = mutableListOf(
            Parameter("seconds", PrimitiveType.Float)
        )) {
            ControlStatements.Wait(it[0])
        }
    }
}