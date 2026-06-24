package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.StopMode

object StandardLibASTGenerator {
    fun init(editor: ScratchEditor) {
        MemoryLib.init(memoryLib, editor)
        LooksLib.init(looksLib, editor)
        MathLib.init(mathLib, editor)
        ExceptionLib.init(exceptLib, editor)
        SensingLib.init(sensingLib, editor)
        CastLib.init(castLib, editor)

        typeChecker
    }

    val memoryLib = ASTFile(
        "memory"
    )

    val looksLib = ASTFile(
        "looks"
    )

    val mathLib = ASTFile(
        "math"
    )

    val exceptLib = ASTFile(
        "except"
    )

    val sensingLib = ASTFile(
        "sensing"
    )

    val castLib = ASTFile(
        "cast"
    )

    val lib = mutableMapOf(
        "looks" to looksLib,
        "sensing" to sensingLib,
        "memory" to memoryLib,
        "math" to mathLib,
        "except" to exceptLib,
        "cast" to castLib
    )

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

object LooksLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "say", warp = true) {
            val message = arg("message", Type.str)
            looks.say(message)
        }

        editor.compile(lib, "sayForSeconds", warp = true) {
            val message = arg("message", Type.str)
            val seconds = arg("seconds", Type.float)
            looks.say(message, seconds)
        }
    }
}

object SensingLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "ask", warp = true) {
            val message = arg("message", Type.str)
            sensing.ask(message)
        }
    }
}

object ExceptionLib {
    lateinit var panic: StandardLibASTFunction
    fun init(lib: ASTFile, editor: ScratchEditor) {
        panic = editor.compile(lib, "panic", warp = true) {
            val message = arg("message", Type.str)
            control.stop(StopMode.OTHER_SCRIPTS_IN_SPRITE)
            sensing.ask(message)
            control.stop(StopMode.ALL)
        }
    }
}