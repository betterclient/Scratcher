package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import org.antlr.v4.kotlinruntime.ast.Position

class LiteralParser(
    val expressionParser: ExpressionParser,
    val parser: Stage1Parser
) {
    fun parseLiteral(ctx: ScratcherLangParser.LiteralContext): Expression {
        return when {
            ctx.FALSE() != null -> BooleanLiteral(false, ctx.position)
            ctx.TRUE() != null -> BooleanLiteral(true, ctx.position)
            ctx.FLOAT() != null -> {
                ctx.FLOAT()!!.text.toBigDecimalOrNull()?.let { FloatLiteral(it, ctx.position) }
                    ?: expressionParser.error(ctx.position!!, parser.ast, "Not a float!")
            }
            ctx.INT() != null -> {
                ctx.INT()!!.text.toBigIntegerOrNull()?.let { IntLiteral(it, ctx.position) }
                    ?: expressionParser.error(ctx.position!!, parser.ast, "Not an int!")
            }
            ctx.stringLiteral() != null -> parseStringInterp(ctx.position, ctx.stringLiteral()!!.stringPart())
            ctx.CHAR_LITERAL() != null -> CharLiteral(parseCharLiteral(ctx.CHAR_LITERAL()!!.text), ctx.position)
            else -> throw NotImplementedException("$ctx is not one of the expected types.")
        }
    }

    fun parseCharLiteral(raw: String): Char {
        val content = raw.removeSurrounding("'")

        if (content.startsWith("\\") && content.length > 1) {
            return when (content) {
                "\\n"  -> '\n'
                "\\r"  -> '\r'
                "\\t"  -> '\t'
                "\\b"  -> '\b'
                "\\\\" -> '\\'
                "\\'"  -> '\''
                "\\\"" -> '\"'
                "\\0"  -> '\u0000'
                else   -> content[1]
            }
        }
        return content.firstOrNull() ?: ' '
    }

    private fun parseStringInterp(position: Position?, parts: List<ScratcherLangParser.StringPartContext>): Expression {
        if (parts.isEmpty()) return StringLiteral("", position)
        val exprs = parts.map { part ->
            when {
                part.STR_TEXT() != null -> {
                    StringLiteral(part.STR_TEXT()!!.text, part.position)
                }
                part.STR_ESC() != null -> {
                    StringLiteral(unescapeString(part.STR_ESC()!!.text), part.position)
                }
                part.DOLLAR() != null -> {
                    StringLiteral("$", part.position)
                }
                part.interpolation() != null -> {
                    StringBoxing.unboxIfNeeded(
                        expressionParser.parseExpression(part.interpolation()!!.expression()),
                        PrimitiveType.Str
                    )
                }
                else -> StringLiteral("", part.position)
            }
        }
        val exprsReduced = if (exprs.size == 1) {
            if (ExpressionTypes.getExpressionType(exprs[0]) == PrimitiveType.Str) {
                exprs[0]
            } else {
                ConcatExpression(exprs[0], StringLiteral("", exprs[0].position), exprs[0].position)
            }
        } else {
            exprs.reduce { left, right ->
                ConcatExpression(left, right, left.position)
            }
        }

        return exprsReduced
    }

    private fun unescapeString(esc: String): String {
        return when (esc) {
            "\\n" -> "\n"
            "\\t" -> "\t"
            "\\r" -> "\r"
            "\\\"" -> "\""
            "\\\\" -> "\\"
            "\\$" -> "$"
            else -> if (esc.startsWith("\\")) esc.substring(1) else esc
        }
    }
}