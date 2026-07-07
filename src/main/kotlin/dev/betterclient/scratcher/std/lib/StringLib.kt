package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.std.dsl.DSLFromCreator
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline
import dev.betterclient.scratcher.std.dsl.concat
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.gt
import dev.betterclient.scratcher.std.dsl.length
import dev.betterclient.scratcher.std.dsl.lte
import dev.betterclient.scratcher.std.dsl.minus
import dev.betterclient.scratcher.std.dsl.plus
import dev.betterclient.scratcher.std.dsl.sc

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
            val input = arg("input", Type.str)
            val delimiter = arg("delimiter", Type.str)
            val out = returnArg(Type.str.list())

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