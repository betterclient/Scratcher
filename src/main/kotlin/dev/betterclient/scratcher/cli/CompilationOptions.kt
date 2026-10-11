package dev.betterclient.scratcher.cli

data class CompilationOptions(
    val obfuscation: ObfuscationOptions = ObfuscationOptions(),
    val std: StandardLibraryOptions = StandardLibraryOptions(),
    val gc: GCOptions = GCOptions(),
    val performance: PerformanceOptions = PerformanceOptions(),
    val experimental: ExperimentalOptions = ExperimentalOptions()
)

data class ObfuscationOptions(
    val enabled: Boolean = false,
    val namingStrategy: NamingStrategy = NamingStrategy.MINIFY
)

enum class NamingStrategy(val length: Int? = null) {
    MINIFY,
    RANDOM(length = 10)
}

data class StandardLibraryOptions(
    val inlineStructInit: Boolean = false, //have the struct initialization as inline, ignored(set to false) if refcount gc is enabled
    val listPoolSize: Int = 10 //size of the list_pool, total amounts of times you can call allocate without having to free
)

//both collectors can be enabled at once
data class GCOptions(
    val refCountGC: Boolean = true, //reference counting garbage collector

    val markAndSweepGC: Boolean = true, //mark and sweep garbage collector
    val automaticGC: Boolean = true, //enable automatic gc::collect every 1 second (set to false: you have to call gc::collect yourself)
    val reflectGC: Boolean = false //use hacked blocks to do top level marking in garbage collection, might be incompatible with turbowarp
)

data class PerformanceOptions(
    val optimizationMode: OptimizationMode = OptimizationMode.AGGRESSIVE,
    //improve performance by disabling safety, only disable if you already tested your project with them enabled
    val disableIndexOutOfBounds: Boolean = false, //disable index out of bounds checking in arrays
    val disableTypeChecker: Boolean = false //disable the runtime type checker
)

enum class OptimizationMode {
    OFF, //no optimization
    MEDIUM, //optimizations enabled
    AGGRESSIVE //optimizations + function inlining
}

data class ExperimentalOptions(
    val shortCircuit: Boolean = true, //experimental short circuit support via desugar pass
    val dummySprite: Boolean = true, //add an empty dummy sprite so the editor opens it instead of the massive worker sprite
    val turbowarp: Boolean = false //use TurboWarp only extensions to improve performance (NOT COMPATIBLE WITH SCRATCH!!)
)