package dev.betterclient

import dev.betterclient.ast.parser.ASTReader
import dev.betterclient.ast.parser.CompilationContext
import dev.betterclient.ast.parser.Stage1Parser
import dev.betterclient.ast.parser.TypeAnalysis
import java.io.File

fun main() {
    val source = File("helloworld.sc")
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, source.readText(), source.absolutePath).read()
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    println(ast)
}
