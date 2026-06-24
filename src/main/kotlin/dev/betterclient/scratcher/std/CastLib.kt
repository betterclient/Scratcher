package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.MathOp

object CastLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "toFloatOrDefault", warp = true) {
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

        editor.compile(lib, "toFloat", warp = true) {
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

        editor.compile(lib, "toIntOrDefault", warp = true) {
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

        editor.compile(lib, "toInt", warp = true) {
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

        editor.compile(lib, "toBoolOrDefault", warp = true) {
            val str = arg("str", Type.str)
            val fallback = arg("fallback", Type.bool)
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

        editor.compile(lib, "toBool", warp = true) {
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

        editor.compile(lib, "toStr", warp = true) {
            val value = arg("value", Type.float)
            val returnArg = returnArg(Type.str)
            MemoryLib.heap[returnArg] = value
        }

        editor.compile(lib, "toStr", warp = true) {
            val value = arg("value", Type.bool)
            val returnArg = returnArg(Type.str)
            MemoryLib.heap[returnArg] = value
        }
    }
}