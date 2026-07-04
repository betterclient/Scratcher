package dev.betterclient.scratcher

object CompilationConstants {
    const val MANUAL_MEMORY = false
    const val OBFUSCATION = false
    const val OBFUSCATION_MINIFICATION = false
    const val NON_MINIFICATION_LENGTH = 20
    const val PRINT_STDLIB = false //print the stdlib function structures and exit
    const val DISABLE_TYPE_CHECKER = true //disables the runtime type checker
    const val DISABLE_INDEX_OUT_OF_BOUNDS = false //disable index out of bounds checking
    const val AUTOMATIC_GC = false //enable automatic gc::collect every 1 second (this will make it so you have to call collect)
}