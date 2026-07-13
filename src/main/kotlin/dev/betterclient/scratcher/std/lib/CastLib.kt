package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.std.dsl.*

object CastLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "toFloatOrDefault") {
            val str = arg("str", PrimitiveType.Str)
            val fallback = arg("fallback", PrimitiveType.Float)
            val returnArg = returnArg(PrimitiveType.Float)

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
            val str = arg("str", PrimitiveType.Str)
            val returnArg = returnArg(PrimitiveType.Float)

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
            val str = arg("str", PrimitiveType.Str)
            val fallback = arg("fallback", PrimitiveType.Integer)
            val returnArg = returnArg(PrimitiveType.Integer)

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
            val str = arg("str", PrimitiveType.Str)
            val returnArg = returnArg(PrimitiveType.Integer)

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
            val str = arg("str", PrimitiveType.Str)
            val fallback = boolArg("fallback")
            val returnArg = returnArg(PrimitiveType.Bool)

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
            val str = arg("str", PrimitiveType.Str)
            val returnArg = returnArg(PrimitiveType.Bool)

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
            parameters = mutableListOf(Parameter("value", PrimitiveType.Float)),
            returnType = PrimitiveType.Str,
        ) { args ->
            args[0] //just return the str
        }

        compileInline(
            lib,
            "toStr",
            parameters = mutableListOf(Parameter("value", PrimitiveType.Bool)),
            returnType = PrimitiveType.Str,
        ) { args ->
            args[0]
        }
    }
}