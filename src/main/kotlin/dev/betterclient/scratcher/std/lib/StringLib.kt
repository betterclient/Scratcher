package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline

object StringLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(
            lib,
            "concat",
            parameters = mutableListOf(
                Parameter("left", Type.str),
                Parameter("right", Type.str),
            ),
            returnType = Type.str
        ) {
            OperatorExpressions.BinaryExpression(
                it[0], it[1], OperatorExpressions.BinaryOperator.STRING_CONCAT
            )
        }

        compileInline(
            lib,
            "length",
            parameters = mutableListOf(
                Parameter("input", Type.str),
            ),
            returnType = Type.int
        ) {
            OperatorExpressions.StringLength(it[0])
        }

        compileInline(
            lib,
            "charAt",
            parameters = mutableListOf(
                Parameter("input", Type.str),
                Parameter("index", Type.int),
            ),
            returnType = Type.str
        ) {
            OperatorExpressions.StringLetterAt(it[0], it[1])
        }

        compileInline(
            lib,
            "contains",
            parameters = mutableListOf(
                Parameter("input", Type.str),
                Parameter("contained", Type.str),
            ),
            returnType = Type.bool
        ) {
            BoolOperatorExpressions.BinaryExpression(
                it[0], it[1], SBinaryOperator.STRING_CONTAINS
            )
        }

        editor.compile(
            lib,
            "substring"
        ) {
            val input = arg("input", Type.str)
            val start = arg("startIndex", Type.int)
            val end = arg("endIndex", Type.int)
            val out = returnArg(Type.str)


        }
    }
}