package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.ast.parser.CompilationContext
import org.antlr.v4.kotlinruntime.ast.Position

class ErrorReporter {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    fun error(position: Position, ast: ASTFile, text: String): ErrorExpression {
        this.logError(ast, position, text)
        return ErrorExpression
    }

    fun logError(ast: ASTFile, position: Position, text: String) {
        errors.add(formatDiagnostic(ast, position, "error: $text"))
    }

    fun warn(ast: ASTFile, position: Position, text: String) {
        warnings.add(formatDiagnostic(ast, position, "warning: $text"))
    }

    fun flush() {
        if (errors.isNotEmpty()) {
            val before = warnings.joinToString("\n")
            throw GeneralCompilerException(
                "Encountered errors while compiling! \n$before\n${errors.joinToString("\n")}"
            )
        }
        for (warning in warnings) {
            CompilationContext.print(warning, err = true)
        }
        warnings.clear()
    }

    private fun formatDiagnostic(ast: ASTFile, position: Position, message: String): String {
        val source = ast.sourceCode.source
        val lines = source.lines()
        val fullLine = lines[position.start.line - 1]

        val start = position.start.column
        val end = position.end.column
        val markerLength = maxOf(1, end - start)

        val prefix = fullLine.take(start).map { if (it == '\t') '\t' else ' ' }.joinToString("")
        return "${ast.simplePath}.sc:${position.prettyString()}: $message\n" +
                "$fullLine\n" +
                "$prefix${"^".repeat(markerLength)}"
    }


    private fun Position.prettyString(): String {
        return "${this.start.line}:${this.start.column}"
    }

    inline fun <T> catching(action: () -> T): T? {
        val errorSnapshot = errors.size
        val warningSnapshot = warnings.size

        return try {
            val result = action()
            val hasNewErrors = errors.size > errorSnapshot

            if (hasNewErrors) {
                while (errors.size > errorSnapshot) errors.removeAt(errors.lastIndex)
                while (warnings.size > warningSnapshot) warnings.removeAt(warnings.lastIndex)
                null
            } else {
                result
            }
        } catch (e: Throwable) {
            while (errors.size > errorSnapshot) errors.removeAt(errors.lastIndex)
            while (warnings.size > warningSnapshot) warnings.removeAt(warnings.lastIndex)
            CompilationContext.print(e.stackTraceToString(), err = true)
            null
        }
    }
}