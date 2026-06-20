package dev.betterclient

import dev.betterclient.ast.ASTReader
import java.io.File

fun main() {
    val source = File("helloworld.sc")
    val ast = ASTReader(source.readText(), source.absolutePath).read()

    println(ast)
}
