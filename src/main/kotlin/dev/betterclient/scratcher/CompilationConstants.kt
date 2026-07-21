package dev.betterclient.scratcher

object CompilationConstants {
    //obfuscation
    const val OBFUSCATION = false //obfuscate your code to make it unreadable or just reduce size
    const val OBFUSCATION_MINIFICATION = true //use short sequential names instead of randomized strings
    const val NON_MINIFICATION_LENGTH = 20 //randomized string length, only relevant if OBFUSCATION_MINIFICATION is false

    //standard library functions
    const val PRINT_STDLIB = false //print the stdlib function structures and exit

    //garbage collector settings
    const val MARK_AND_SWEEP_GC = false //mark and sweep garbage collector, only downside is speed
    const val REFCOUNT_GC = false //reference counting garbage collector, cannot detect cycles
    const val AUTOMATIC_GC = true //enable automatic gc::collect every 1 second (this will make it so you have to call gc::collect yourself)
    const val REFLECT_GC = false //use hacked blocks to do top level marking in garbage collection, might be incompatible with turbowarp

    //improve performance by disabling safety, only disable if you already tested your project with them enabled
    const val DISABLE_INDEX_OUT_OF_BOUNDS = true //disable index out of bounds checking
    const val DISABLE_TYPE_CHECKER = true //disable the runtime type checker

    const val DISABLE_OPTIMIZATIONS = false //disable optimizations to make performance worse for debugging
}