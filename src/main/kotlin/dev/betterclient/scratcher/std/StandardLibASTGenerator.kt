package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.lib.CalendarLib
import dev.betterclient.scratcher.std.lib.CastLib
import dev.betterclient.scratcher.std.lib.LooksLib
import dev.betterclient.scratcher.std.lib.MathLib
import dev.betterclient.scratcher.std.lib.MemoryLib
import dev.betterclient.scratcher.std.lib.SensingLib

object StandardLibASTGenerator {
    fun init(editor: ScratchEditor) {
        ExceptionLib.init(exceptLib, editor)
        MemoryLib.init(memoryLib, editor)
        LooksLib.init(looksLib)
        MathLib.init(mathLib, editor)
        SensingLib.init(sensingLib)
        CalendarLib.init(calendarLib)
        CastLib.init(castLib, editor)

        typeChecker
    }

    val memoryLib = ASTFile("memory")
    val looksLib = ASTFile("looks")
    val mathLib = ASTFile("math")
    val exceptLib = ASTFile("except")
    val sensingLib = ASTFile("sensing")
    val calendarLib = ASTFile("calendar")
    val castLib = ASTFile("cast")

    val lib = mutableMapOf(
        "looks" to looksLib,
        "sensing" to sensingLib,
        "memory" to memoryLib,
        "math" to mathLib,
        "except" to exceptLib,
        "cast" to castLib,
        "calendar" to calendarLib,
    )

    val memLib = ASTFile("mem").also {
        if (CompilationConstants.MANUAL_MEMORY) lib["mem"] = it
    }

    val typeChecker by lazy {
        compile(
            "/TypeChecker.sc",
            "typecheck"
        ).also { lib["typecheck"] = it }
    }

    val rawLibs by lazy {
        listOf(typeChecker)
    }

    fun isStandardLib(func: Function): Boolean {
        return lib.map { it.value.functions }.reduce { a, b -> (a + b).toMutableList() }.contains(func)
    }

    fun isRestricted(library: ASTFile): Boolean {
        return library.path == "typecheck" || library == memoryLib
    }

    private fun compile(file: String, path: String): ASTFile {
        val context = CompilationContext()
        val ast = ASTReader(
            ctx = context,
            source = String(StandardLibASTGenerator::class.java.getResourceAsStream(file).use { it!!.readBytes() }),
            fullPath = path
        ).read()
        Stage1Parser(context, ast).parse()

        return ast
    }
}

object ExceptionLib {
    lateinit var panic: StandardLibASTFunction
    fun init(lib: ASTFile, editor: ScratchEditor) {
        panic = editor.compile(lib, "panic") {
            val message = arg("message", Type.str)
            control.stop(StopMode.OTHER_SCRIPTS_IN_SPRITE)
            sensing.ask(message)
            control.stop(StopMode.ALL)
        }
    }
}