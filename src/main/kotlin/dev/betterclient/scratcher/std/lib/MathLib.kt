package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.std.dsl.*

object MathLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        mathOps.forEach { func ->
            func.types.forEach { (inputType, returnType) ->
                compileInline(
                    lib,
                    func.functionName,
                    parameters = mutableListOf(Parameter("number", inputType)),
                    returnType = returnType
                ) { args ->
                    OperatorExpressions.MathOperation(func.internal, args[0])
                }
            }
        }

        compileInline(
            lib, "pow",
            parameters = mutableListOf(Parameter("base", Type.float), Parameter("exponent", Type.float)),
            returnType = Type.float
        ) {
            OperatorExpressions.MathOperation(
                MathOp.E_POW,
                OperatorExpressions.BinaryExpression(
                    left = it[1],
                    right = OperatorExpressions.MathOperation(
                        MathOp.LN,
                        it[0]
                    ),
                    operator = OperatorExpressions.BinaryOperator.MULTIPLY
                )
            )
        }

        compileInline(
            lib,
            "round",
            parameters = mutableListOf(Parameter("value", Type.float)),
            returnType = Type.int
        ) { args ->
            OperatorExpressions.RoundNumber(args[0])
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