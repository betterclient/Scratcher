package dev.betterclient.scratcher

import dev.betterclient.scratcher.cli.CompilationOptions
import dev.betterclient.scratcher.cli.ExperimentalOptions
import dev.betterclient.scratcher.cli.GCOptions
import dev.betterclient.scratcher.cli.ObfuscationOptions
import dev.betterclient.scratcher.cli.OptimizationMode
import dev.betterclient.scratcher.cli.PerformanceOptions
import dev.betterclient.scratcher.cli.StandardLibraryOptions
import java.io.File

object DebugMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val startTime = System.currentTimeMillis()
        val options = CompilationOptions(
            ObfuscationOptions(),
            StandardLibraryOptions(),
            GCOptions(),
            PerformanceOptions(
                optimizationMode = OptimizationMode.OFF,
            ),
            ExperimentalOptions()
        )

        val compiler = Compiler(
            File("examples/cube/cube.sc"),
            outFile = File("out.sb3"),
            inputProj = null,
            options = options,

            print = { println(it) },
            error = { System.err.println(it) }
        )

        compiler.compile()
        println("Compilation successful in ${System.currentTimeMillis() - startTime}ms")
    }
}