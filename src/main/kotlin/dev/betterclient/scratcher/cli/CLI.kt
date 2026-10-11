package dev.betterclient.scratcher.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import dev.betterclient.scratcher.Compiler
import dev.betterclient.scratcher.ast.ErrorReporter
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.ast.parser.code.Stage1Parser
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import java.io.File

fun main(args: Array<String>) = ScratcherCLI()
    .subcommands(Check(), Compile(), StandardLibrary())
    .main(args)

class ScratcherCLI : CliktCommand() {
    override fun run() = Unit
    override fun help(context: Context): String {
        return "Scratcher compiler"
    }
}

class StandardLibrary : CliktCommand() {
    override fun help(context: Context): String {
        return "View the standard library of scratcher."
    }

    override fun run() {
        val context = CompilationContext(CompilationOptions())
        val editor = openScratchEditorFromResource(
            Compiler::class.java.getResourceAsStream("/proj.sb3")!!
        )
        StandardLibASTGenerator.init(editor)

        val code = """
            on GreenFlag {}
        """.trimIndent()

        val reporter = ErrorReporter()
        val ast = ASTReader(reporter, context, code, "code.sc").read()
        context.generateGCNames()

        StandardLibASTGenerator.generateFrom(context, ast)
        Stage1Parser(context, ast).parse()
        TypeAnalysis(reporter, context, ast).run()

        StandardLibASTGenerator.print()
    }
}

class Compile : CliktCommand() {
    val file by argument(help="file to compile").file(canBeDir = false, mustExist = true, canBeFile = true)
    val inputProj by option("--input-sb3", "-i").file(canBeDir = false, mustExist = true, canBeFile = true)
    val outProj by option("--output-sb3", "-o").file(canBeDir = false).default(File("out.sb3"))

    val settings by CLICompilationSettings()

    override fun help(context: Context): String {
        return "Compile the project"
    }

    override fun run() {
        try {
            Compiler(
                startFile = file,
                inputProj = inputProj,
                outFile = outProj,

                options = settings.asOptions,

                print = { println(it) },
                error = { System.err.println(it) }
            ).compile()
        } catch (e: GeneralCompilerException) {
            echo(e.message) //error reporter
            throw ProgramResult(1)
        }
    }
}

class Check : CliktCommand() {
    val file by argument(help="file to check").file(canBeDir = false, mustExist = true, canBeFile = true)
    val settings by CLICompilationSettings()

    override fun help(context: Context): String {
        return "Check if your code has any errors without compiling"
    }

    override fun run() {
        echo(
            Compiler(
                startFile = file,
                inputProj = null,
                outFile = File("out.sb3"),
                options = settings.asOptions,
                print = { println(it) },
                error = { System.err.println(it) }
            ).check()
        )
    }
}

class CLICompilationSettings : OptionGroup("Compilation settings") {
    //obfuscation
    val obfuscationEnabled by option("--obfuscate", "--obfuscation", help = "obfuscate the program")
        .flag("--no-obfuscate", default = false)

    val namingStrategy by option("--naming-strategy", help = "what to obfuscate with")
        .enum<NamingStrategy>()
        .default(NamingStrategy.MINIFY)

    //std
    val inlineStructInit by option("--inline-struct-init", help = "have the struct initialization as inline, ignored if refcount gc is enabled")
        .flag("--no-inline-struct-init", default = false)

    val listPoolSize by option("--list-pool-size", help = "size of the list_pool, total amounts of times you can call allocate without having to free")
        .int()
        .default(10)

    //gc
    val refCountGC by option("--refcount", "--ref-count-gc", help = "reference counting garbage collector")
        .flag("--no-refcount", "--no-ref-count-gc", default = true)

    val markAndSweepGC by option("--mark-and-sweep-gc", help = "mark and sweep garbage collector")
        .flag("--no-mark-and-sweep-gc", default = true)

    val automaticGC by option("--automatic-gc", help = "enable automatic gc::collect every 1 second (set to off: you have to call gc::collect yourself)")
        .flag("--no-automatic-gc", default = true)

    val reflectGC by option("--reflect-gc", help = "use hacked blocks to do top level marking in garbage collection (only compatible with vanilla scratch unless --turbowarp is enabled)")
        .flag("--no-reflect-gc", default = false)

    //performance
    val optimizationMode by option("--opt-mode", "--optimization-mode", help = "optimization mode")
        .enum<OptimizationMode>()

    private val optFlag by option(help = "optimization level flag")
        .switch(
            "-o0" to OptimizationMode.OFF, "-O0" to OptimizationMode.OFF,
            "-o1" to OptimizationMode.MEDIUM, "-O1" to OptimizationMode.MEDIUM,
            "-o2" to OptimizationMode.AGGRESSIVE, "-O2" to OptimizationMode.AGGRESSIVE,
        )

    val disableIndexOutOfBounds by option("--disable-index-out-of-bounds", help = "disable index out of bounds")
        .flag(default = false)

    val disableTypeChecker by option("--disable-type-checker", help = "disable the runtime type checker")
        .flag(default = false)

    //experimental
    val shortCircuit by option("--short-circuit", help = "experimental short circuit support via desugar pass")
        .flag("--no-short-circuit", default = true)

    val dummySprite by option("--dummy-sprite", help = "add an empty dummy sprite so the editor opens it instead of the massive worker sprite")
        .flag("--no-dummy-sprite", default = true)

    val turbowarp by option("--turbowarp", help = "turbowarp only compilation mode (not compatible with scratch)")
        .flag("--no-turbowarp", default = false)

    val asOptions: CompilationOptions
        get() = CompilationOptions(
            obfuscation = ObfuscationOptions(
                enabled = obfuscationEnabled,
                namingStrategy = namingStrategy
            ),
            std = StandardLibraryOptions(
                inlineStructInit = inlineStructInit,
                listPoolSize = listPoolSize
            ),
            gc = GCOptions(
                refCountGC = refCountGC,
                markAndSweepGC = markAndSweepGC,
                automaticGC = automaticGC,
                reflectGC = reflectGC
            ),
            performance = PerformanceOptions(
                optimizationMode = optFlag ?: optimizationMode ?: OptimizationMode.MEDIUM,
                disableIndexOutOfBounds = disableIndexOutOfBounds,
                disableTypeChecker = disableTypeChecker
            ),
            experimental = ExperimentalOptions(
                shortCircuit = shortCircuit,
                dummySprite = dummySprite,
                turbowarp = turbowarp
            )
        )
}