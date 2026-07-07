package dev.betterclient.scratcher

object CompilationConstants {
    //obfuscation
    const val OBFUSCATION = false //obfuscate your code to make it unreadable or just reduce size
    const val OBFUSCATION_MINIFICATION = false //use short sequential names instead of randomized strings
    const val NON_MINIFICATION_LENGTH = 20 //randomized string length, only relevant if OBFUSCATION_MINIFICATION is false

    //standard library functions
    const val PRINT_STDLIB = false //print the stdlib function structures and exit

    //garbage collector settings
    const val MANUAL_MEMORY = false //disable garbage collector to use manual memory management
    const val AUTOMATIC_GC = true //enable automatic gc::collect every 1 second (this will make it so you have to call gc::collect yourself)
    const val REFLECT_GC = true //use hacked blocks to do top level marking in garbage collection

    //improve performance by disabling safety, only disable if you already tested your project with them enabled
    const val DISABLE_INDEX_OUT_OF_BOUNDS = true //disable index out of bounds checking
    const val DISABLE_TYPE_CHECKER = true //disable the runtime type checker
}