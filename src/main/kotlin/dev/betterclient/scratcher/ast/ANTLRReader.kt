package dev.betterclient.scratcher.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangLexer
import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import org.antlr.v4.kotlinruntime.*

fun read(source: String, errorReporter: ErrorReporter): ScratcherLangParser.ProgramContext {
    val input = StringCharStream(source)

    val lexer = ScratcherLangLexer(input)
    lexer.removeErrorListeners()
    lexer.addErrorListener(ScratcherErrorListener(errorReporter, source))

    val tokens = CommonTokenStream(lexer)

    val parser = ScratcherLangParser(tokens)
    parser.removeErrorListeners()
    parser.addErrorListener(ScratcherErrorListener(errorReporter, source))

    return parser.program().also {
        errorReporter.flush()
    }
}

class ScratcherErrorListener(
    val reporter: ErrorReporter,
    val source: String
) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        val lines = source.lines()
        val fullLine = lines.getOrElse(line - 1) { "" }

        val token = offendingSymbol as? Token
        val length = when {
            token == null -> 1
            token.type == Token.EOF -> 1
            else -> token.text?.length ?: (token.stopIndex - token.startIndex + 1).coerceAtLeast(1)
        }

        val end = charPositionInLine + length
        val markerLength = maxOf(1, end - charPositionInLine)

        val position = "$line:$charPositionInLine"

        reporter.errors.add(
            "$position: $msg\n" +
                    "$fullLine\n" +
                    " ".repeat(charPositionInLine) + "^".repeat(markerLength)
        )
    }
}