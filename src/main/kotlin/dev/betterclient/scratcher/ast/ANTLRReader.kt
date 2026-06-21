package dev.betterclient.scratcher.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangLexer
import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.StringCharStream

fun read(source: String): ScratcherLangParser.ProgramContext {
    val input = StringCharStream(source)

    val lexer = ScratcherLangLexer(input)
    val tokens = CommonTokenStream(lexer)

    val parser = ScratcherLangParser(tokens)

    return parser.program()
}