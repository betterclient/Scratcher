package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.std.dsl.*

object StringLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
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
            returnType = PrimitiveType.Str
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

        editor.compile(
            lib,
            "substring"
        ) {
            val input = arg("input", PrimitiveType.Str)
            val start = arg("startIndex", PrimitiveType.Integer)
            val end = arg("endIndex", PrimitiveType.Integer)
            val out = returnArg(PrimitiveType.Str)

            val result = variable("substring::result")
            result.set("".sc)

            val index = variable("substring::index")
            index.set(start)

            control.ifThen(start lte end) {
                control.repeat((end - start) + 1.sc) {
                    result.set(result concat DSLFromCreator {
                        OperatorExpressions.StringLetterAt(input.lower(), index.lower())
                    })
                    index.changeBy(1.sc)
                }
            }

            MemoryLib.heap[out] = result
        }

        editor.compile(
            lib,
            "split"
        ) {
            //this would be so much easier to write if I could just write it in the language
            val input = arg("input", PrimitiveType.Str)
            val delimiter = arg("delimiter", PrimitiveType.Str)
            val out = returnArg(ListType(PrimitiveType.Str))

            if (!CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
                control.ifThen(delimiter.length gt 1.sc) {
                    call(ExceptionLib.panic, "Split delimiter must be length 1".sc)
                }
            }
            call(ListLib.newList, out, "l0".sc) //use l0, primitive list
            val realList = MemoryLib.heap[out] //pointer!!!

            val start = variable("substring::start")
            start.set(0.sc)
            val current = variable("substring::current")
            current.set("".sc)
            val i = variable("substring::i")
            i.set(1.sc)

            control.repeatUntil(i gt input.length) {
                control.ifElse(
                    condition = DSLFromCreator { OperatorExpressions.StringLetterAt(input.lower(), i.lower()) } equals delimiter,
                    thenBlock = {
                        call(ListLib.add, realList, current)
                        current.set("".sc)
                    },
                    elseBlock = {
                        current.set(current concat DSLFromCreator { OperatorExpressions.StringLetterAt(input.lower(), i.lower()) })
                    }
                )
                i.set(i + 1.sc)
            }
            call(ListLib.add, realList, current)
        }
    }
}