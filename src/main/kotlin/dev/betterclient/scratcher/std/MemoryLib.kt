package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList

object MemoryLib {
    val heap = ScratchList(obfuscate("Scratcher Heap"))
    val freeList = ScratchList(obfuscate("FreeList"))
    lateinit var free: StandardLibASTFunction
    lateinit var alloc: StandardLibASTFunction

    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.addList(heap)
        editor.addList(freeList)

        free = editor.compile(lib, "free") {
            val index = arg("index", Type.int)
            val size = arg("size", Type.int)

            val currentIndex = variable("currentIndex")
            val left = variable("left")
            val right = variable("right")
            val middle = variable("middle")

            left.set(1.sc)
            right.set(freeList.length)
            control.repeatUntil(left gt right) {
                middle.set(((left + right) / 2.sc).math(MathOp.FLOOR))
                control.ifElse(
                    condition = freeList[middle] lt index,
                    thenBlock = {
                        left.set(middle + 1.sc)
                    },
                    elseBlock = {
                        right.set(middle - 1.sc)
                    }
                )
            }

            currentIndex.set(index)
            control.repeat(size) {
                freeList.insert(left, currentIndex)
                currentIndex.changeBy(1.sc)
                left.changeBy(1.sc)
            }
        }

        alloc = editor.compile(lib, "alloc") {
            val size = arg("size", Type.int)
            val returnIndex = arg("returnIndex", Type.int)

            val variable = variable("variable")
            val maxSearchIndex = variable("maxSearchIndex")
            val allocatedAddress = variable("allocatedAddress")

            allocatedAddress.set((-1).sc)
            control.ifThen(freeList.length gte size) {
                variable.set(1.sc)
                maxSearchIndex.set((freeList.length - size) + 1.sc)

                control.repeatUntil(
                    (variable gt maxSearchIndex) or (allocatedAddress gt 0.sc)
                ) {
                    control.ifElse(
                        condition = (freeList[(variable + size) - 1.sc] - freeList[variable]) equals (size - 1.sc),
                        thenBlock = {
                            allocatedAddress.set(freeList[variable])
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

            control.ifThen(allocatedAddress equals (-1).sc) {
                allocatedAddress.set(heap.length + 1.sc)
                control.repeat(size) {
                    heap.add("".sc)
                }
            }

            heap[returnIndex] = allocatedAddress
        }
    }
}