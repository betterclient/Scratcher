package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.gc.findGC
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.dsl.*

object MemoryLib {
    val heap = ScratchList(obfuscate("Scratcher Heap"))
    val freeList = ScratchList(obfuscate("FreeList"))
    lateinit var free: StandardLibASTFunction
    lateinit var alloc: StandardLibASTFunction

    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.addList(heap)
        editor.addList(freeList)

        free = editor.compile(lib, "free") {
            val index = arg("index", PrimitiveType.Integer)
            val size = arg("size", PrimitiveType.Integer)

            val currentIndex = variable("free::currentIndex")
            val left = variable("free::left")
            val right = variable("free::right")
            val middle = variable("free::middle")

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
            val size = arg("size", PrimitiveType.Integer)
            val name = arg("name", PrimitiveType.Str)
            val returnIndex = arg("returnIndex", PrimitiveType.Integer)

            val variable = variable("alloc::variable")
            val maxSearchIndex = variable("alloc::maxSearchIndex")
            val allocatedAddress = variable("alloc::allocatedAddress")
            val actualSize = variable("alloc::actualSize")

            actualSize.set(size + 1.sc)

            if (!CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
                control.ifThen(size equals 0.sc) {
                    call(ExceptionLib.panic, "Scratcher Runtime error: Alloc called with size 0!".sc)
                }
            }

            allocatedAddress.set((-1).sc)
            control.ifThen(freeList.length gte actualSize) {
                variable.set(1.sc)
                maxSearchIndex.set((freeList.length - actualSize) + 1.sc)

                control.repeatUntil(
                    (variable gt maxSearchIndex) or (allocatedAddress gt 0.sc)
                ) {
                    control.ifElse(
                        condition = (freeList[(variable + actualSize) - 1.sc] - freeList[variable]) equals (actualSize - 1.sc),
                        thenBlock = {
                            allocatedAddress.set(freeList[variable])
                            control.repeat(actualSize) {
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
                control.repeat(actualSize) {
                    heap.add("".sc)
                }
            }

            heap[allocatedAddress] = name
            heap[returnIndex] = allocatedAddress + 1.sc
        }
    }

    fun initMem(lib: ASTFile, compilationStartAST: ASTFile) {
        val structs = mutableMapOf<ASTFile, List<Struct>>().also { figureOutReachableStructs(it, compilationStartAST) }.flatMap { (_, structs) -> structs }

        for (struct in structs) {
            val name = "new${struct.sourceAST.simplePath}::${struct.name}"
            val existingFunc = lib.functions.find { it.name == name }
            if (existingFunc != null) {
                struct.allocFunc = existingFunc
                continue
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
            if (!freeExists && !(CompilationConstants.MARK_AND_SWEEP_GC || CompilationConstants.REFCOUNT_GC)) {
                compileInline(
                    library = lib,
                    name = "free",
                    parameters = mutableListOf(Parameter("pointer", struct.type)),
                    returnType = PrimitiveType.Void
                ) { args ->
                    val pointerArg = args[0]
                    CallFunction(
                        free.precompiledCode,
                        listOf(
                            OperatorExpressions.BinaryExpression(pointerArg, "1".scratch, OperatorExpressions.BinaryOperator.SUBTRACT),
                            (struct.sizeOnHeap + 1).toString().scratch
                        )
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