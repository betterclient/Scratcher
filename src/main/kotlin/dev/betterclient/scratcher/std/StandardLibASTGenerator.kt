package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.ArrayType
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PlaceholderType
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.SealedEnumType
import dev.betterclient.scratcher.ast.SimpleType
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
        ArrayLib.init(arrayInternalLib, editor)
        array
        list.value
        ExtensionsInternalLib.init(extensionsInternalLib)
        extensionsLib

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
    val arrayInternalLib = ASTFile("array_internal")

    val memoryLib = ASTFile("memory")
    val globalPromotionLib = ASTFile("global_promotions")
    val dynamicDispatchLib = ASTFile("dynamic_dispatch")
    val refCountGC = ASTFile("ref_count_gc")
    val compilerLib = ASTFile("compiler")
    val lambdaLib = ASTFile("lambda")
    val extensionsInternalLib = ASTFile("extensions_internal")
    val extensionsFakeLib = ASTFile("extensions")
    val listFakeLib = ASTFile("list")
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
        "array_internal" to arrayInternalLib,
        "list" to listFakeLib,
        "extensions" to extensionsFakeLib, //this will get removed after first pass

        //not allowed
        "global_promotions" to globalPromotionLib,
        "dynamic_dispatch" to dynamicDispatchLib,
        "extensions_internal" to extensionsInternalLib,
        "compiler" to compilerLib,
        "gc_internal" to gcInternalsLib,
        "ref_count_gc" to refCountGC,
        "gc" to gcLib,
        "lambda" to lambdaLib
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

    val list = lazy {
        compile("/list.sc", "list").also {
            listFakeLib.templates.addAll(it.templates)
            listFakeLib.functions.addAll(it.functions)
            listFakeLib.structs.addAll(it.structs)
            listFakeLib.structTemplates.addAll(it.structTemplates)
        }
    }

    val array by lazy {
        bypassRestrictions = true
        val out = compile("/array.sc", "array").also {
            lib["array"] = it
        }
        bypassRestrictions = false
        out
    }

    val extensionsLib by lazy {
        bypassRestrictions = true //needs extensions_internal
        val out = compile("/extensions.sc", "extensions").also {
            extensionsFakeLib.templates.addAll(it.templates)
            extensionsFakeLib.functions.addAll(it.functions)
        }
        bypassRestrictions = false
        out
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

    val compactIntList by lazy {
        compile("/compact_int_list.sc", "compact_int_list").also {
            lib["compact_list"] = it
        }
    }

    val rawLibs by lazy {
        listOf(typeChecker, triangle, compactIntList)
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
                library == refCountGC ||
                library == lambdaLib ||
                library == extensionsInternalLib ||
                library == arrayInternalLib
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
        MemoryLib.initMem(memoryLib, ast)
        Stage1Parser(context, ast).parse()
        if (path == GC_LIB_NAME) TypeAnalysis(context, ast).run() //yea

        return ast
    }

    fun print() {
        lib.forEach { (name, ast) ->
            if (isRestricted(ast)) return@forEach
            println("Library: $name")
            (ast.functions + ast.templates).forEach { func ->
                if (!func.userAccessible) return@forEach
                if (func.typeBindings.isNotEmpty()) return@forEach
                if (func.private) return@forEach

                println("   Function: ${func.toGoodString()}")
            }
            if (name == "mem") {
                println("   Function: warp free (AnyStruct val)")
            }
        }
        exitProcess(0)
    }

    private fun Function.toGoodString(): String {
        val upToName = "${
            if (this is InlineStandardLibFunction) "inlined " else ""
        }${
            if (this.warp) "warp " else ""
        }${
            if (this.typeParameters.isNotEmpty()) {
                "<${this.typeParameters.joinToString(", ")}> "
            } else ""
        }${this.returnType.toGoodString()} ${
            if(this.isReceiver) {
                "${this.parameters[0].type.toGoodString()}."
            } else ""
        }${this.name}"

        val pars = (if (this.isReceiver) {
            this.parameters.subList(1, this.parameters.size)
        } else this.parameters).joinToString(", ") { "${it.type.toGoodString()} ${it.name}" }

        return "$upToName($pars)"
    }

    private fun Type.toGoodString(): String = when (this) {
        is NullableType -> "${inner.toGoodString()}?"
        is PrimitiveType -> toString()
        is SimpleType -> {
            if (sourceAST == compilerLib && name == "StringBox") "str"
            else {
                val base = name.substringBefore("@")
                if (!name.contains("@")) base
                else {
                    val struct = sourceAST.structs.find { it.name == name }
                    val bindings = struct?.typeBindings
                    if (bindings.isNullOrEmpty()) base
                    else {
                        val template = sourceAST.structTemplates.find { it.name == base }
                        val ordered = template?.typeParameters
                            ?.mapNotNull { bindings[it] }
                            ?.takeIf { it.size == bindings.size }
                            ?: bindings.values.toList()
                        "$base<${ordered.joinToString(", ") { it.toGoodString() }}>"
                    }
                }
            }
        }
        is SealedEnumType -> {
            val base = name.substringBefore("@")
            if (typeBindings.isEmpty()) base
            else {
                val template = sourceAST.sealedEnumTemplates.find { it.name == base }
                val ordered = template?.typeParameters
                    ?.mapNotNull { typeBindings[it] }
                    ?.takeIf { it.size == typeBindings.size }
                    ?: typeBindings.values.toList()
                "$base<${ordered.joinToString(", ") { it.toGoodString() }}>"
            }
        }
        is ArrayType -> "${elementType.toGoodString()}[]"
        is FunctionType -> "(${parameterTypes.joinToString(", ") { it.toGoodString() }}) -> ${returnType.toGoodString()}"
        is PlaceholderType -> name
    }

    fun generateFrom(startAST: ASTFile) {
        MemoryLib.initMem(memLib, startAST) //generate alloc(struct)
        ExtensionsInternalLib.init(extensionsInternalLib)
        extensionsLib

        GCLib.init(gcInternalsLib)
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

        compileInline(lib, "random", parameters = mutableListOf(
            Parameter("from", PrimitiveType.Integer),
            Parameter("to", PrimitiveType.Integer),
        ), returnType = PrimitiveType.Integer) {
            OperatorExpressions.Random(it[0], it[1])
        }

        compileInline(lib, "wait", parameters = mutableListOf(
            Parameter("seconds", PrimitiveType.Float)
        )) {
            ControlStatements.Wait(it[0])
        }
    }
}