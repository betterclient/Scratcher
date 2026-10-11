package dev.betterclient.scratcher

class Compiler(
    val startFile: String,
    val startPath: String,

    val getFile: (path: String) -> String,

    val options: CompilationOptions
) {

}