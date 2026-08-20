package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.std.dsl.*

object ExtensionsInternalLib {
    fun init(lib: ASTFile) {
        compileInline(
            lib,
            "concat",
            parameters = mutableListOf(
                Parameter("left", PrimitiveType.Str),
                Parameter("right", PrimitiveType.Str),
            ),
            returnType = PrimitiveType.Str
        ) {
            OperatorExpressions.BinaryExpression(
                it[0], it[1], OperatorExpressions.BinaryOperator.STRING_CONCAT
            )
        }

        compileInline(
            lib,
            "length",
            parameters = mutableListOf(
                Parameter("input", PrimitiveType.Str),
            ),
            returnType = PrimitiveType.Integer
        ) {
            OperatorExpressions.StringLength(it[0])
        }

        compileInline(
            lib,
            "charAt",
            parameters = mutableListOf(
                Parameter("input", PrimitiveType.Str),
                Parameter("index", PrimitiveType.Integer),
            ),
            returnType = PrimitiveType.Char
        ) {
            OperatorExpressions.StringLetterAt(it[0], it[1])
        }

        compileInline(
            lib,
            "contains",
            parameters = mutableListOf(
                Parameter("input", PrimitiveType.Str),
                Parameter("contained", PrimitiveType.Str),
            ),
            returnType = PrimitiveType.Bool
        ) {
            BoolOperatorExpressions.BinaryExpression(
                it[0], it[1], SBinaryOperator.STRING_CONTAINS
            )
        }
    }
}