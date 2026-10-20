package dev.betterclient.scratcher

import dev.betterclient.scratcher.cli.CompilationOptions
import dev.betterclient.scratcher.cli.ExperimentalOptions
import dev.betterclient.scratcher.cli.GCOptions
import dev.betterclient.scratcher.cli.ObfuscationOptions
import dev.betterclient.scratcher.cli.OptimizationMode
import dev.betterclient.scratcher.cli.PerformanceOptions
import dev.betterclient.scratcher.cli.StandardLibraryOptions
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import org.json.JSONObject
import java.io.File
import kotlin.test.assertEquals

fun compile(
    code: String,
    options: CompilationOptions = CompilationOptions(
        ObfuscationOptions(),
        StandardLibraryOptions(),
        GCOptions(),
        PerformanceOptions(),
        ExperimentalOptions()
    )
): File {
    val out = File.createTempFile("meow", ".sb3")
    out.deleteOnExit()
    val compiler = Compiler(
        File.createTempFile("hello", ".sc").also {
            it.deleteOnExit()
            it.writeText(code)
        },
        outFile = out,
        inputProj = null,
        options = options,

        print = {  }, //TODO: make empty print actually work
        error = { System.err.println(it) }
    )

    compiler.compile()
    return out
}

fun compileAndRun(
    code: String,
    options: CompilationOptions = CompilationOptions(
        ObfuscationOptions(),
        StandardLibraryOptions(),
        GCOptions(),
        PerformanceOptions(),
        ExperimentalOptions()
    ),
    timeoutMs: Long = 1000,
    expects: ScratchTestScope.() -> Unit = {}
) {
    run(
        compile(code, options),
        timeoutMs,
        expects
    )
}