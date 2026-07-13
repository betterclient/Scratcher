package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
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
            parameters = mutableListOf(Parameter("base", PrimitiveType.Float), Parameter("exponent", PrimitiveType.Float)),
            returnType = PrimitiveType.Float
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
            parameters = mutableListOf(Parameter("value", PrimitiveType.Float)),
            returnType = PrimitiveType.Integer
        ) { args ->
            OperatorExpressions.RoundNumber(args[0])
        }

        editor.compile(lib, "isNumber") {
            val number = arg("number", PrimitiveType.Float)
            val returnArg = returnArg(PrimitiveType.Bool)

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
        mapOf(PrimitiveType.Integer to PrimitiveType.Integer, PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.FLOOR, "floor",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.CEILING, "ceil",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.SQRT, "sqrt",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.SIN, "sin",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.COS, "cos",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.TAN, "tan",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.ASIN, "asin",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.ACOS, "acos",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.ATAN, "atan",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.LN, "ln",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.LOG, "log",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.E_POW, "exp",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    ),
    ConvertibleMathOp(
        MathOp.TEN_POW, "pow10",
        mapOf(PrimitiveType.Float to PrimitiveType.Float)
    )
)