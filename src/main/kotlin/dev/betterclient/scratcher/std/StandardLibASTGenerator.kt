package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.lib.*

object StandardLibASTGenerator {
    fun init(editor: ScratchEditor) {
        ExceptionLib.init(exceptLib, editor)
        MemoryLib.init(memoryLib, editor)
        LooksLib.init(looksLib)
        MathLib.init(mathLib, editor)
        SensingLib.init(sensingLib)
        CalendarLib.init(calendarLib)
        CastLib.init(castLib, editor)
        PenLib.init(penLib, editor)
        MotionLib.init(motionLib, editor)
        RandomLib.init(randomLib, editor)

        typeChecker
    }

    val memoryLib = ASTFile("memory")
    val looksLib = ASTFile("looks")
    val mathLib = ASTFile("math")
    val exceptLib = ASTFile("except")
    val sensingLib = ASTFile("sensing")
    val calendarLib = ASTFile("calendar")
    val castLib = ASTFile("cast")
    val penLib = ASTFile("pen")
    val motionLib = ASTFile("motion")
    val randomLib = ASTFile("random")

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
        "random" to randomLib,
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

object RandomLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(lib, "random", parameters = mutableListOf(
            Parameter("from", Type.float),
            Parameter("to", Type.float),
        ), returnType = Type.float) {
            OperatorExpressions.Random(it[0], it[1])
        }
    }
}