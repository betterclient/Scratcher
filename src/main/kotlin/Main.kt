package dev.betterclient

import dev.betterclient.ast.parser.ASTReader
import dev.betterclient.ast.parser.CompilationContext
import dev.betterclient.ast.parser.Stage1Parser
import java.io.File

fun main() {
    val source = File("helloworld.sc")
    val context = CompilationContext()
    val ast = ASTReader(context, source.readText(), source.absolutePath).read()
    Stage1Parser(context, ast).parse()

    println(ast)
}
