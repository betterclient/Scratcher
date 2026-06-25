package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.std.ExceptionLib
import dev.betterclient.scratcher.std.dsl.and
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.math
import dev.betterclient.scratcher.std.dsl.or
import dev.betterclient.scratcher.std.dsl.times

object CastLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "toFloatOrDefault") {
            val str = arg("str", Type.str)
            val fallback = arg("fallback", Type.float)
            val returnArg = returnArg(Type.float)

            control.ifElse(
                condition = (str * 1.sc) equals str, //we can't actually use isNumber here because we don't have a stack
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    MemoryLib.heap[returnArg] = fallback
                }
            )
        }

        editor.compile(lib, "toFloat") {
            val str = arg("str", Type.str)
            val returnArg = returnArg(Type.float)

            control.ifElse(
                condition = (str * 1.sc) equals str,
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    call(ExceptionLib.panic, "Not a float!".sc)
                }
            )
        }

        editor.compile(lib, "toIntOrDefault") {
            val str = arg("str", Type.str)
            val fallback = arg("fallback", Type.int)
            val returnArg = returnArg(Type.int)

            control.ifElse(
                condition = ((str * 1.sc) equals str) and (str.math(MathOp.FLOOR) equals str),
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    MemoryLib.heap[returnArg] = fallback
                }
            )
        }

        editor.compile(lib, "toInt") {
            val str = arg("str", Type.str)
            val returnArg = returnArg(Type.int)

            control.ifElse(
                condition = ((str * 1.sc) equals str) and (str.math(MathOp.FLOOR) equals str),
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    call(ExceptionLib.panic, "Not an integer!".sc)
                }
            )
        }

        editor.compile(lib, "toBoolOrDefault") {
            val str = arg("str", Type.str)
            val fallback = boolArg("fallback")
            val returnArg = returnArg(Type.bool)

            control.ifElse(
                condition = (str equals "true".sc) or (str equals "false".sc),
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    MemoryLib.heap[returnArg] = fallback
                }
            )
        }

        editor.compile(lib, "toBool") {
            val str = arg("str", Type.str)
            val returnArg = returnArg(Type.bool)

            control.ifElse(
                condition = (str equals "true".sc) or (str equals "false".sc),
                thenBlock = {
                    MemoryLib.heap[returnArg] = str
                },
                elseBlock = {
                    call(ExceptionLib.panic, "Not a boolean!".sc)
                }
            )
        }

        compileInline(
            lib,
            "toStr",
            parameters = mutableListOf(Parameter("value", Type.float)),
            returnType = Type.str,
        ) { args ->
            args[0] //just return the str
        }

        compileInline(
            lib,
            "toStr",
            parameters = mutableListOf(Parameter("value", Type.bool)),
            returnType = Type.str,
        ) { args ->
            args[0]
        }
    }
}