package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.obfuscate
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList

object MemoryLibRewrite {
    val heap = ScratchList(obfuscate("Scratcher Heap"))
    val freeList = ScratchList(obfuscate("FreeList"))
    lateinit var free: StandardLibASTFunction
    lateinit var alloc: StandardLibASTFunction

    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.addList(heap)
        editor.addList(freeList)

        free = editor.compile(lib, "free", warp = true) {
            val index = arg("index", Type.int)
            val size = arg("size", Type.int)

            val currentIndex = variable("currentIndex")
            val left = variable("left")
            val right = variable("right")
            val middle = variable("middle")

            currentIndex.set(index)
            control.repeat(size) {
                control.ifThen(freeList.contains(currentIndex).not()) {
                    left.set(1.sc)
                    right.set(freeList.length)
                    control.repeatUntil(left gt right) {
                        middle.set(((left + right) / 2.sc).math(MathOp.FLOOR))
                        control.ifElse(
                            condition = freeList[middle] lt currentIndex,
                            thenBlock = {
                                left.set(middle + 1.sc)
                            },
                            elseBlock = {
                                right.set(middle - 1.sc)
                            }
                        )
                    }
                    freeList.insert(left, currentIndex)
                }
                currentIndex.changeBy(1.sc)
            }
        }

        alloc = editor.compile(lib, "alloc", warp = true) {
            val size = arg("size", Type.int)
            val returnIndex = arg("returnIndex", Type.int)

            val variable = variable("variable")
            val maxSearchIndex = variable("maxSearchIndex")
            val endIndex = variable("endIndex")
            val startNumber = variable("startNumber")
            val endNumber = variable("endNumber")

            heap[returnIndex] = (-1).sc
            control.ifThen(freeList.length gte size) {
                variable.set(1.sc)
                maxSearchIndex.set((freeList.length - size) + 1.sc)
                control.repeatUntil(
                    (variable gt maxSearchIndex) or (heap[returnIndex] equals (-1).sc).not()
                ) {
                    endIndex.set((variable + size) - 1.sc)
                    startNumber.set(freeList[variable])
                    endNumber.set(freeList[endIndex])

                    control.ifElse(
                        condition = (endNumber - startNumber) equals (size - 1.sc),
                        thenBlock = {
                            heap[returnIndex] = freeList[variable]
                            control.repeat(size) {
                                freeList.remove(variable)
                            }
                        },
                        elseBlock = {
                            variable.changeBy(1.sc)
                        }
                    )
                }
            }

            control.ifThen(heap[returnIndex] equals (-1).sc) {
                heap[returnIndex] = heap.length + 1.sc
                control.repeat(size) {
                    heap.add("".sc)
                }
            }
        }
    }
}