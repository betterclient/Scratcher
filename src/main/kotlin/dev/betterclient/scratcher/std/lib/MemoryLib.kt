package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.gc.findGC
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.dsl.*

object MemoryLib {
    val heap = ScratchList(obfuscate("Scratcher Heap"))
    val freeList = ScratchList(obfuscate("FreeList"))
    val allocAddressList = ScratchList(obfuscate("AllocAddressList"))
    val allocNameList = ScratchList(obfuscate("AllocNameList"))
    lateinit var free: StandardLibASTFunction
    lateinit var alloc: StandardLibASTFunction

    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.addList(heap)
        editor.addList(freeList)
        if (!CompilationConstants.MANUAL_MEMORY) {
            editor.addList(allocAddressList)
            editor.addList(allocNameList)
        }

        free = editor.compile(lib, "free") {
            val index = arg("index", Type.int)
            val size = arg("size", Type.int)

            val currentIndex = variable("currentIndex")
            val left = variable("left")
            val right = variable("right")
            val middle = variable("middle")

            if (!CompilationConstants.MANUAL_MEMORY) {
                val searchIndex = variable("searchIndex")
                searchIndex.set(1.sc)
                control.repeatUntil(searchIndex gt allocAddressList.length) {
                    control.ifElse(
                        condition = allocAddressList[searchIndex] equals index,
                        thenBlock = {
                            allocAddressList.remove(searchIndex)
                            allocNameList.remove(searchIndex)
                            searchIndex.set(allocAddressList.length + 1.sc)
                        },
                        elseBlock = {
                            searchIndex.changeBy(1.sc)
                        }
                    )
                }
            }

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
                heap[currentIndex] = (-1).sc
                currentIndex.changeBy(1.sc)
                left.changeBy(1.sc)
            }
        }

        alloc = editor.compile(lib, "alloc") {
            val size = arg("size", Type.int)
            val name = arg("name", Type.str)
            val returnIndex = arg("returnIndex", Type.int)

            val variable = variable("variable")
            val maxSearchIndex = variable("maxSearchIndex")
            val allocatedAddress = variable("allocatedAddress")

            if (!CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
                control.ifThen(size equals 0.sc) {
                    //OH NO!!!
                    call(ExceptionLib.panic, "Scratcher Runtime error: Alloc called with size 0! (this is probably a bug in the compiler)".sc)
                }
            }

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

            if (!CompilationConstants.MANUAL_MEMORY) {
                allocAddressList.add(allocatedAddress)
                allocNameList.add(name)
            }
            heap[returnIndex] = allocatedAddress
        }
    }

    fun initMem(lib: ASTFile, compilationStartAST: ASTFile) {
        val structs = mutableMapOf<ASTFile, List<Struct>>().also { figureOutReachableStructs(it, compilationStartAST) }.flatMap { (_, structs) -> structs }

        for (struct in structs) {
            var name = "new${struct.sourceAST.simplePath}::${struct.name}"
            if (lib.functions.find { it.name == name } != null) {
                println("WARN: Potentially duplicate structs(?) ${struct.sourceAST.simplePath}::${struct.name}")
                name = "$name${getUniqueName()}"
            }

            struct.allocFunc = compileInline(
                lib,
                name,
                returnType = struct.type,
                parameters = struct.parameters,
                useLocal = true,
                userAccessible = false,
                prepend = { args ->
                    val otherArgs = args.subList(0, args.size - 1)
                    val lastArg = args.last()

                    val allocCall = CallFunction(
                        alloc.precompiledCode,
                        listOf(struct.sizeOnHeap.toString().scratch, findGC(struct).toString().scratch, lastArg)
                    )

                    val replaceStatements = otherArgs.mapIndexed { index, arg ->
                        val targetIndexExpr = OperatorExpressions.BinaryExpression(
                            left = ListExpressions.ItemAtIndex(heap, lastArg),
                            operator = OperatorExpressions.BinaryOperator.ADD,
                            right = index.toString().scratch
                        )

                        ListStatements.ReplaceItem(heap, arg, targetIndexExpr)
                    }

                    listOf(allocCall) + replaceStatements
                }
            ) { args ->
                val pointerArg = args[args.size - 1]
                ListExpressions.ItemAtIndex(heap, pointerArg)
            }

            val freeExists = lib.functions.any {
                it.name == "free" &&
                        it.parameters.size == 1 &&
                        it.parameters[0].type == struct.type
            }
            if (!freeExists) {
                compileInline(
                    library = lib,
                    name = "free",
                    parameters = mutableListOf(Parameter("pointer", struct.type)),
                    returnType = Type.void
                ) { args ->
                    val pointerArg = args[0]
                    CallFunction(
                        free.precompiledCode,
                        listOf(pointerArg, struct.sizeOnHeap.toString().scratch)
                    )
                }
            }
        }
    }

    private fun figureOutReachableStructs(out: MutableMap<ASTFile, List<Struct>>, ast: ASTFile) {
        if (out.containsKey(ast)) return
        out[ast] = ast.structs.map { it }
        ast.imports.forEach { (_, ast) -> figureOutReachableStructs(out, ast) }
    }
}