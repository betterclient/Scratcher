package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CharLiteral
import dev.betterclient.scratcher.ast.ConcatExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.NotImplementedException
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TypeException
import dev.betterclient.scratcher.ast.parser.ExpressionTypes

class LiteralParser(
    val expressionParser: ExpressionParser,
    val parser: Stage1Parser
) {
    fun parseLiteral(ctx: ScratcherLangParser.LiteralContext): Expression {
        return when {
            ctx.FALSE() != null -> BooleanLiteral(false)
            ctx.TRUE() != null -> BooleanLiteral(true)
            ctx.FLOAT() != null -> FloatLiteral(ctx.FLOAT()!!.text.toBigDecimalOrNull()?: throw TypeException(PrimitiveType.Float, PrimitiveType.Null, "${ctx.FLOAT()?.text} is not a float!"))
            ctx.INT() != null -> IntLiteral(ctx.INT()!!.text.toBigIntegerOrNull()?: throw TypeException(PrimitiveType.Integer, PrimitiveType.Null, "${ctx.INT()?.text} is not an int!"))
            ctx.stringLiteral() != null -> parseStringInterp(ctx.stringLiteral()!!.stringPart())
            ctx.TICK(0) != null -> CharLiteral(ctx.IDENTIFIER()!!.text.also {
                if (it.length != 1) throw GeneralCompilerException("Char too long or too short at ${ctx.position}!")
            }.toCharArray()[0])
            else -> throw NotImplementedException("$ctx is not one of the expected types.")
        }
    }

    private fun parseStringInterp(parts: List<ScratcherLangParser.StringPartContext>): Expression {
        if (parts.isEmpty()) return StringLiteral("")
        val exprs = parts.map { part ->
            when {
                part.STR_TEXT() != null -> {
                    StringLiteral(part.STR_TEXT()!!.text)
                }
                part.STR_ESC() != null -> {
                    StringLiteral(unescapeString(part.STR_ESC()!!.text))
                }
                part.DOLLAR() != null -> {
                    StringLiteral("$")
                }
                part.interpolation() != null -> {
                    StringBoxing.unboxIfNeeded(
                        expressionParser.parseExpression(part.interpolation()!!.expression()),
                        PrimitiveType.Str,
                        parser.ctx
                    )
                }
                else -> StringLiteral("")
            }
        }
        val exprsReduced = if (exprs.size == 1) {
            if (ExpressionTypes.getExpressionType(parser.ctx, exprs[0]) == PrimitiveType.Str) {
                exprs[0]
            } else {
                ConcatExpression(exprs[0], StringLiteral(""))
            }
        } else {
            exprs.reduce { left, right ->
                ConcatExpression(left, right)
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