package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.codegen.ast.SBoolOperator
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchLiteralStringExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.codegen.ast.VariableStatements
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.ItemOfListOpcode
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.rand

object MemoryLibrary {
    val heap = ScratchList("Scratcher Heap")
    val freeList = ScratchList("FreeList")

    fun generate(editor: ScratchEditor): MutableList<Function> {
        val list = mutableListOf<Function>()

        editor.addList(heap)
        editor.addList(freeList)

        //TODO: maybe a DSL for stdlib functions?
        list.add(generateFree(editor, freeList))

        ScratchFuncArgument(rand(), ScratchType.ANY).also { variable ->
            list.add(generateAlloc(
                editor,
                freeList,
                heap,
                { ListExpressions.ItemAtIndex(heap, ScratchStringParameterExpression(variable)) },
                { ListStatements.ReplaceItem(heap, it, ScratchStringParameterExpression(variable)) },
                variable
            ))
        }

        return list
    }

    private fun generateFree(editor: ScratchEditor, freeList: ScratchList): StandardLibASTFunction {
        val indexPar = ScratchFuncArgument(rand(), ScratchType.ANY)
        val sizePar = ScratchFuncArgument(rand(), ScratchType.ANY)
        val index = ScratchStringParameterExpression(indexPar)
        val size = ScratchStringParameterExpression(sizePar)

        val code = mutableListOf<ScratchStatement>()

        val currentIndexV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val currentIndex = ListExpressions.Variable(currentIndexV)

        val leftV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val rightV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val middleV = ScratchVariable(rand()).also { editor.addVariable(it) }

        val left = ListExpressions.Variable(leftV)
        val right = ListExpressions.Variable(rightV)
        val middle = ListExpressions.Variable(middleV)

        code.add(VariableStatements.SetVariableTo(currentIndexV, index))
        code.add(ControlStatements.RepeatTimes(size, listOf(
            ControlStatements.IfThen(BoolOperatorExpressions.SNotExpression(
                ListExpressions.ContainsItemInList(freeList, currentIndex)
            ), listOf(
                VariableStatements.SetVariableTo(leftV, "1".scratch),
                VariableStatements.SetVariableTo(rightV, ListExpressions.LengthOfList(freeList)),
                ControlStatements.RepeatUntil(
                    condition = BoolOperatorExpressions.BinaryExpression(
                        operand1 = left,
                        operand2 = right,
                        binaryOperator = SBinaryOperator.GT
                    ), listOf(
                        VariableStatements.SetVariableTo(middleV, OperatorExpressions.MathOperation(
                            MathOp.FLOOR, OperatorExpressions.BinaryExpression(
                                left = OperatorExpressions.BinaryExpression(
                                    left, right, OperatorExpressions.BinaryOperator.ADD
                                ),
                                right = "2".scratch,
                                operator = OperatorExpressions.BinaryOperator.DIVIDE
                            )
                        )),
                        ControlStatements.IfElse(
                            condition = BoolOperatorExpressions.BinaryExpression(
                                binaryOperator = SBinaryOperator.LT,
                                operand1 = ListExpressions.ItemAtIndex(freeList, middle),
                                operand2 = currentIndex
                            ),
                            thenBlock = listOf(
                                VariableStatements.SetVariableTo(leftV, OperatorExpressions.BinaryExpression(
                                    left = middle,
                                    right = "1".scratch,
                                    operator = OperatorExpressions.BinaryOperator.ADD
                                ))
                            ),
                            elseBlock = listOf(
                                VariableStatements.SetVariableTo(rightV, OperatorExpressions.BinaryExpression(
                                    left = middle,
                                    right = "1".scratch,
                                    operator = OperatorExpressions.BinaryOperator.SUBTRACT
                                ))
                            )
                        )
                    )
                ),
                ListStatements.InsertItem(freeList, currentIndex, left)
            )),
            VariableStatements.ChangeVariableBy(currentIndexV, "1".scratch)
        )))

        return StandardLibASTFunction(
            name = "free",
            parameters = mutableListOf(
                Parameter("index", Type.int),
                Parameter("size", Type.int)
            ),
            precompiledCode = ScratchASTFunction(
                name = "free",
                args = listOf(indexPar, sizePar),
                runWithoutScreenRefresh = true,
                code = code
            )
        )
    }

    private fun generateAlloc(
        editor: ScratchEditor,
        freeList: ScratchList,
        heap: ScratchList,
        getCurrentIndex: () -> ScratchExpression,
        setCurrentIndex: (ScratchExpression) -> ScratchStatement,
        returnParameter: ScratchFuncArgument?
    ): StandardLibASTFunction {
        val sizePar = ScratchFuncArgument(rand(), ScratchType.ANY)
        val size = ScratchStringParameterExpression(sizePar)

        val code = mutableListOf<ScratchStatement>()
        code.add(setCurrentIndex("-1".scratch))
        val iV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val i = ListExpressions.Variable(iV)

        val maxSearchIndexV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val maxSearchIndex = ListExpressions.Variable(maxSearchIndexV)

        val endIndexV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val endIndex = ListExpressions.Variable(endIndexV)

        val startNumberV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val startNumber = ListExpressions.Variable(startNumberV)
        val endNumberV = ScratchVariable(rand()).also { editor.addVariable(it) }
        val endNumber = ListExpressions.Variable(endNumberV)

        code.add(ControlStatements.IfThen(
            condition = BoolOperatorExpressions.BinaryExpression(
                operand1 = ListExpressions.LengthOfList(freeList),
                operand2 = size,
                binaryOperator = SBinaryOperator.GTE
            ),
            block = listOf(
                VariableStatements.SetVariableTo(iV, "1".scratch),
                VariableStatements.SetVariableTo(maxSearchIndexV, OperatorExpressions.BinaryExpression(
                    left = OperatorExpressions.BinaryExpression(
                        left = ListExpressions.LengthOfList(freeList),
                        right = size,
                        operator = OperatorExpressions.BinaryOperator.SUBTRACT
                    ),
                    right = "1".scratch,
                    operator = OperatorExpressions.BinaryOperator.ADD
                )),
                ControlStatements.RepeatUntil(
                    condition = BoolOperatorExpressions.SBoolComparisonExpressions(
                        operand1 = BoolOperatorExpressions.BinaryExpression(
                            operand1 = i,
                            operand2 = maxSearchIndex,
                            binaryOperator = SBinaryOperator.GT
                        ),
                        operand2 = BoolOperatorExpressions.SNotExpression(
                            BoolOperatorExpressions.BinaryExpression(
                                operand1 = getCurrentIndex(),
                                operand2 = "-1".scratch,
                                binaryOperator = SBinaryOperator.EQUALS
                            )
                        ),
                        operator = SBoolOperator.OR
                    ),
                    block = listOf(
                        VariableStatements.SetVariableTo(endIndexV, OperatorExpressions.BinaryExpression(
                            left = OperatorExpressions.BinaryExpression(
                                left = i,
                                right = size,
                                operator = OperatorExpressions.BinaryOperator.ADD
                            ),
                            right = "1".scratch,
                            operator = OperatorExpressions.BinaryOperator.SUBTRACT
                        )),
                        VariableStatements.SetVariableTo(startNumberV, ListExpressions.ItemAtIndex(freeList, i)),
                        VariableStatements.SetVariableTo(endNumberV, ListExpressions.ItemAtIndex(freeList, endIndex)),
                        ControlStatements.IfElse(
                            condition = BoolOperatorExpressions.BinaryExpression(
                                operand1 = OperatorExpressions.BinaryExpression(
                                    left = endNumber,
                                    right = startNumber,
                                    operator = OperatorExpressions.BinaryOperator.SUBTRACT
                                ),
                                operand2 = OperatorExpressions.BinaryExpression(
                                    left = size,
                                    right = "1".scratch,
                                    operator = OperatorExpressions.BinaryOperator.SUBTRACT
                                ),
                                binaryOperator = SBinaryOperator.EQUALS
                            ),
                            thenBlock = listOf(
                                setCurrentIndex(ListExpressions.ItemAtIndex(freeList, i)),
                                ControlStatements.RepeatTimes(size, block = listOf(
                                    ListStatements.DeleteItem(freeList, i)
                                ))
                            ),
                            elseBlock = listOf(
                                VariableStatements.ChangeVariableBy(iV, "1".scratch)
                            )
                        )
                    )
                )
            )
        ))
        code.add(
            ControlStatements.IfThen(
                condition = BoolOperatorExpressions.BinaryExpression(
                    operand1 = getCurrentIndex(),
                    operand2 = "-1".scratch,
                    binaryOperator = SBinaryOperator.EQUALS
                ),
                listOf(
                    setCurrentIndex(
                        OperatorExpressions.BinaryExpression(
                            operator = OperatorExpressions.BinaryOperator.ADD,
                            left = ListExpressions.LengthOfList(heap),
                            right = "1".scratch,
                        )
                    ),
                    ControlStatements.RepeatTimes(size, listOf(
                        ListStatements.AddToList(heap, "".scratch)
                    ))
                )
            )
        )

        return StandardLibASTFunction(
            name = "alloc",
            parameters = mutableListOf(
                Parameter("size", Type.int)
            ).also {
                if (returnParameter != null) {
                    it.add(Parameter("compiler@returnIndex", Type.int))
                }
            },
            precompiledCode = ScratchASTFunction(
                name = "alloc",
                args = listOfNotNull(sizePar, returnParameter),
                runWithoutScreenRefresh = true,
                code = code
            )
        )
    }
}