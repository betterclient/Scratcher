package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.opcode.MathOp

object MathLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        mathOps.forEach { func ->
            func.types.forEach { (inputType, returnType) ->
                editor.compile(lib, func.functionName) {
                    val num = arg("number", inputType)
                    val returnArg = returnArg(returnType)

                    MemoryLib.heap[returnArg] = num.math(func.internal)
                }
            }
        }

        editor.compile(lib, "pow") {
            val base = arg("base", Type.float)
            val exponent = arg("exponent", Type.float)
            val returnArg = returnArg(Type.float)

            MemoryLib.heap[returnArg] = (exponent * base.math(MathOp.LN)).math(MathOp.E_POW)
        }

        editor.compile(lib, "round") {
            val base = arg("value", Type.float)
            val returnArg = returnArg(Type.int)

            MemoryLib.heap[returnArg] = base.round()
        }

        editor.compile(lib, "isNumber") {
            val number = arg("number", Type.float)
            val returnArg = returnArg(Type.bool)

            MemoryLib.heap[returnArg] = (number * 1.sc) equals number
        }
    }
}

class ConvertibleMathOp(
    val internal: MathOp,
    val functionName: String,
    val types: Map<Type, Type>
)

val mathOps = listOf(
    ConvertibleMathOp(
        MathOp.ABS, "abs",
        mapOf(Type.int to Type.int, Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.FLOOR, "floor",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.CEILING, "ceil",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.SQRT, "sqrt",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.SIN, "sin",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.COS, "cos",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.TAN, "tan",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.ASIN, "asin",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.ACOS, "acos",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.ATAN, "atan",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.LN, "ln",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.LOG, "log",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.E_POW, "exp",
        mapOf(Type.float to Type.float)
    ),
    ConvertibleMathOp(
        MathOp.TEN_POW, "pow10",
        mapOf(Type.float to Type.float)
    )
)