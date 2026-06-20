package dev.betterclient

import dev.betterclient.ast.Stage0Parser
import java.io.File

fun main() {
    val ast = Stage0Parser(File("helloworld.sc")).parse()
}
