package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.std.dsl.*

object ArrayLib {
    lateinit var newArray: StandardLibASTFunction
    lateinit var itemAt: Function
    lateinit var length: Function
    lateinit var replace: Function

    val arrayFuncs by lazy {
        listOf(newArray, itemAt, length, replace)
    }

    //offsets
    val refCountOffset = if (CompilationConstants.REFCOUNT_GC) 0 else -1
    val lengthOffset = refCountOffset + 1
    val dataOffset = lengthOffset + 1
    val headerSize = if (CompilationConstants.REFCOUNT_GC) 2 else 1

    fun init(lib: ASTFile, editor: ScratchEditor) {
        generateFunctions(lib, editor)
    }

    private fun generateFunctions(
        lib: ASTFile,
        editor: ScratchEditor
    ) {
        newArray = editor.compile(
            lib,
            "newArray"
        ) {
            val returnArg = returnArg(PrimitiveType.Integer)
            val name = if (CompilationConstants.MARK_AND_SWEEP_GC) arg("name", PrimitiveType.Str) else null
            val length = arg("length", PrimitiveType.Integer)

            val allocArgs = mutableListOf<DSLExpression>(headerSize.sc + length)
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                allocArgs.add(name!!)
            }
            allocArgs.add(returnArg)
            call(MemoryLib.alloc, *allocArgs.toTypedArray()) //alloc structure

            val out = MemoryLib.heap[returnArg]

            if (CompilationConstants.REFCOUNT_GC) {
                MemoryLib.heap[out + refCountOffset.sc] = 1.sc
            }
            MemoryLib.heap[out + lengthOffset.sc] = length //length
        }

        replace = if (CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
            compileInline(
                lib,
                "replace",
                parameters = mutableListOf(
                    Parameter("list", PrimitiveType.Integer),
                    Parameter("item", PrimitiveType.Integer),
                    Parameter("index", PrimitiveType.Integer)
                )
            ) {
                ListStatements.ReplaceItem(
                    list = MemoryLib.heap,
                    item = it[1],
                    index = OperatorExpressions.BinaryExpression(
                        left = it[0],
                        right = OperatorExpressions.BinaryExpression(
                            left = dataOffset.toString().scratch,
                            right = it[2],
                            operator = OperatorExpressions.BinaryOperator.ADD
                        ),
                        operator = OperatorExpressions.BinaryOperator.ADD
                    )
                )
            }
        } else {
            editor.compile(
                lib,
                "replace",
            ) {
                val list = arg("list", PrimitiveType.Integer)
                val item = arg("item", PrimitiveType.Integer)
                val index = arg("index", PrimitiveType.Integer)
                checkOutOfBounds(list, index, if (CompilationConstants.OBFUSCATION) {
                    "".sc
                } else {
                    "replace: Unable to find item ".sc concat index concat " in list: ".sc concat list concat " length: ".sc concat MemoryLib.heap[list + lengthOffset.sc]
                })

                MemoryLib.heap[list + dataOffset.sc + index] = item
            }
        }

        itemAt = if (CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
            compileInline(
                lib,
                "itemAt",
                parameters = mutableListOf(
                    Parameter("list", PrimitiveType.Integer),
                    Parameter("index", PrimitiveType.Integer)
                ),
                returnType = PrimitiveType.Integer
            ) {
                ListExpressions.ItemAtIndex(
                    MemoryLib.heap,
                    OperatorExpressions.BinaryExpression(
                        left = it[0],
                        right = OperatorExpressions.BinaryExpression(
                            left = dataOffset.toString().scratch,
                            right = it[1],
                            operator = OperatorExpressions.BinaryOperator.ADD
                        ),
                        operator = OperatorExpressions.BinaryOperator.ADD
                    )
                )
            }
        } else {
            editor.compile(
                lib,
                "itemAt",
            ) {
                val list = arg("list", PrimitiveType.Integer)
                val index = arg("index", PrimitiveType.Integer)
                val returnVal = returnArg(PrimitiveType.Integer)
                checkOutOfBounds(list, index, if (CompilationConstants.OBFUSCATION) {
                    "".sc
                } else {
                    "itemAt: Unable to find item ".sc concat index concat " in list: ".sc concat list concat " length: ".sc concat MemoryLib.heap[list + lengthOffset.sc]
                })

                MemoryLib.heap[returnVal] = MemoryLib.heap[list + dataOffset.sc + index]
            }
        }

        length = compileInline(
            lib,
            "length",
            mutableListOf(Parameter("list", PrimitiveType.Integer)),
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.ItemAtIndex(
                MemoryLib.heap,
                if (lengthOffset == 0) it[0] else OperatorExpressions.BinaryExpression(
                    left = it[0],
                    right = lengthOffset.toString().scratch,
                    operator = OperatorExpressions.BinaryOperator.ADD
                )
            )
        }
    }

    fun getActualReturnType(expr: CallExpression, check: (Expression) -> Type): Type {
        return when(expr.func) {
            newArray -> {
                if (expr.arguments.size != 2) {
                    throw GeneralCompilerException("array::newArray requires 2 arguments (type, length), got ${expr.arguments.size}")
                }
                val typeLiteral = expr.arguments.firstOrNull() as? TypeLiteral
                    ?: throw GeneralCompilerException("Expected TypeLiteral for array::newArray")

                val lengthArg = expr.arguments[1]
                if (check(lengthArg) != PrimitiveType.Integer) {
                    throw GeneralCompilerException("Expected integer length when creating a new array.")
                }
                ArrayType(typeLiteral.type)
            }
            itemAt -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on array::itemAt, requires 2 parameters")
                check(expr.arguments[1]).let {
                    if (it != PrimitiveType.Integer) {
                        throw TypeException(PrimitiveType.Integer, it, "array::itemAt called without an index?")
                    }
                }

                (check(expr.arguments[0]) as? ArrayType)?.elementType ?: throw GeneralCompilerException("array::itemAt called without an array")
            }
            length -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on array::length, requires 1 parameter")
                if (check(expr.arguments[0]) !is ArrayType) throw GeneralCompilerException("Not passing an array to array::length")
                PrimitiveType.Integer
            }
            replace -> {
                if (expr.arguments.size != 3) throw GeneralCompilerException("Too many/little arguments on array::replace, requires 3 parameters")
                val list = check(expr.arguments[0])
                val added = check(expr.arguments[1])
                val index = check(expr.arguments[2])

                if(list !is ArrayType || !added.isAssignable(list.elementType)) {
                    throw GeneralCompilerException("Not adding to an array when calling array::replace")
                }
                if (index != PrimitiveType.Integer) {
                    throw TypeException(PrimitiveType.Integer, index, "Wrong type passed to array::replace")
                }

                PrimitiveType.Void
            }
            else -> PrimitiveType.Null
        }
    }

    private fun CodeBuilder.checkOutOfBounds(
        list: DSLExpression,
        index: DSLExpression,
        error: DSLExpression
    ) {
        val length = MemoryLib.heap[list + lengthOffset.sc]
        control.ifThen(index gt (length - 1.sc)) {
            call(ExceptionLib.panic, "Scratcher runtime error: IndexOutOfBounds: ".sc concat error)
        }
        control.ifThen(index lt 0.sc) {
            call(ExceptionLib.panic, "Scratcher runtime error: IndexOutOfBounds: ".sc concat error)
        }
    }
}
