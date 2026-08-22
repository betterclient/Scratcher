package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.MathOp
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.gc.findGC
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
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
                heap[currentIndex] = "null".sc
                currentIndex.changeBy(1.sc)
                left.changeBy(1.sc)
            }
        }

        alloc = editor.compile(lib, "alloc") {
            val size = arg("size", PrimitiveType.Integer)
            val name = if(CompilationConstants.MARK_AND_SWEEP_GC) arg("name", PrimitiveType.Str) else null
            val returnIndex = arg("returnIndex", PrimitiveType.Integer)

            val variable = variable("alloc::variable")
            val maxSearchIndex = variable("alloc::maxSearchIndex")
            val allocatedAddress = variable("alloc::allocatedAddress")
            val actualSize = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                val variable = variable("alloc::actualSize")
                variable.set(size + 1.sc)
                variable
            } else {
                size
            }

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
                    heap.add("null".sc)
                }
            }

            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                heap[allocatedAddress] = name!!
                heap[returnIndex] = allocatedAddress + 1.sc
            } else {
                heap[returnIndex] = allocatedAddress
            }
        }
    }

    fun initMem(lib: ASTFile, compilationStartAST: ASTFile) {
        val structs = (mutableMapOf<ASTFile, List<Struct>>().also { figureOutReachableStructs(it, compilationStartAST) }.flatMap { (_, structs) -> structs } + StandardLibASTGenerator.compilerLib.structs)

        for (struct in structs) {
            val name = "new${struct.sourceAST.simplePath}::${struct.name}"
            val existingFunc = lib.functions.find { it.name == name }
            if (existingFunc != null) {
                if (existingFunc.parameters.size == struct.parameters.size) {
                    struct.allocFunc = existingFunc
                    continue
                } else {
                    lib.functions.remove(existingFunc)
                }
            }

            struct.allocFunc = if (CompilationConstants.REFCOUNT_GC || !CompilationConstants.INLINE_STRUCT_INIT) {
                Function(
                    name = name,
                    parameters = struct.parameters.map { Parameter(it.name, it.type) }.toMutableList(),
                    returnType = allocReturnType(struct),
                    export = false,
                    warp = true,
                    sourceAST = lib,
                    userAccessible = false
                ).also { func ->
                    val ptrVar = LocalVariable("compiler@ptr", struct.type)
                    func.code.localVariables.add(ptrVar)

                    val allocArgs = mutableListOf<Expression>(IntLiteral(java.math.BigInteger.valueOf(struct.sizeOnHeap.toLong())))
                    if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        allocArgs.add(StringLiteral(findGC(struct).toString()))
                    }
                    allocArgs.add(TemporaryLocalVariableIndexExpression(ptrVar))

                    func.code.code.add(VariableStatement(null, ptrVar))
                    func.code.code.add(TemporaryCallStatement(alloc, allocArgs))
                    if (CompilationConstants.REFCOUNT_GC) {
                        func.code.code.add(TemporaryHeapSetStatement(
                            index = TemporaryHeapGetExpression(TemporaryLocalVariableIndexExpression(ptrVar)),
                            data = IntLiteral(java.math.BigInteger.ONE)
                        ))
                    }

                    struct.parameters.forEach { param ->
                        val argExpr = ParameterExpression(func.parameters.find { it.name == param.name }!!)
                        func.code.code.add(VariableAssignmentStatement(
                            target = LocalVariableExpression(ptrVar),
                            variable = param,
                            struct = struct,
                            assignment = argExpr
                        ))
                    }

                    func.code.code.add(ReturnStatement(LocalVariableExpression(ptrVar)))
                    lib.functions.add(func)
                    struct.allocFunc = func
                }
            } else {
                compileInline(
                    lib,
                    name,
                    returnType = allocReturnType(struct),
                    parameters = struct.parameters,
                    useLocal = true,
                    userAccessible = false,
                    prepend = { args ->
                        val otherArgs = if (CompilationConstants.REFCOUNT_GC) {
                            listOf(0.toString().scratch) + args.subList(0, args.size - 1) //the 0 refcount
                        } else {
                            args.subList(0, args.size - 1)
                        }
                        val lastArg = args.last()

                        val allocCall = CallFunction(
                            alloc.precompiledCode,
                            mutableListOf(struct.sizeOnHeap.toString().scratch, lastArg).also {
                                if(CompilationConstants.MARK_AND_SWEEP_GC) {
                                    it.add(1, findGC(struct).toString().scratch)
                                }
                            }
                        )

                        val replaceStatements = otherArgs.mapIndexed { index, arg ->
                            val targetIndexExpr = if (index == 0) {
                                ListExpressions.ItemAtIndex(heap, lastArg)
                            } else {
                                OperatorExpressions.BinaryExpression(
                                    left = ListExpressions.ItemAtIndex(heap, lastArg),
                                    operator = OperatorExpressions.BinaryOperator.ADD,
                                    right = index.toString().scratch
                                )
                            }

                            ListStatements.ReplaceItem(heap, arg, targetIndexExpr)
                        }

                        listOf(allocCall) + replaceStatements
                    }
                ) { args ->
                    val pointerArg = args[args.size - 1]
                    ListExpressions.ItemAtIndex(heap, pointerArg)
                }
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
                    val freePtr = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        OperatorExpressions.BinaryExpression(
                            left = args[0],
                            right = "1".scratch,
                            operator = OperatorExpressions.BinaryOperator.SUBTRACT
                        )
                    } else args[0]

                    val freeSize = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        (struct.sizeOnHeap + 1).toString().scratch
                    } else struct.sizeOnHeap.toString().scratch

                    CallFunction(
                        free.precompiledCode,
                        listOf(freePtr, freeSize)
                    )
                }
            }
        }

        val reachableSealedEnums = mutableMapOf<ASTFile, List<SealedEnum>>().also { figureOutReachableSealedEnums(it, compilationStartAST) }.flatMap { (_, enums) -> enums }

        for (sealedEnum in reachableSealedEnums) {
            sealedEnum.types.forEachIndexed { tag, variantStruct ->
                ensureVariantAllocFunc(lib, sealedEnum, variantStruct)
            }
        }
    }

    fun ensureVariantAllocFunc(lib: ASTFile, sealedEnum: SealedEnum, variantStruct: Struct): Function {
        sealedEnum.allocFuncs[variantStruct]?.let { return it }

        val tag = sealedEnum.types.indexOf(variantStruct)
        val name = "new${sealedEnum.sourceAST.simplePath}::${sealedEnum.name}.${variantStruct.name.substringAfter(".")}"

        val func = Function(
            name = name,
            parameters = variantStruct.parameters.map { Parameter(it.name, it.type) }.toMutableList(),
            returnType = sealedEnum.type,
            export = false,
            warp = true,
            sourceAST = lib,
            userAccessible = false
        ).also { fn ->
            val enumPtrVar = LocalVariable("compiler@enumPtr", sealedEnum.type)
            fn.code.localVariables.add(enumPtrVar)

            val isEmpty = variantStruct.parameters.isEmpty()
            val payloadExpr = if (!isEmpty) {
                CallExpression(variantStruct.allocFunc, fn.parameters.map { ParameterExpression(it) })
            } else {
                IntLiteral((-tag-1).toBigInteger())
            }
            val payloadVar = LocalVariable("compiler@payloadPtr", PrimitiveType.Integer)
            fn.code.localVariables.add(payloadVar)
            fn.code.code.add(VariableStatement(payloadExpr, payloadVar))

            val enumSize = if (CompilationConstants.REFCOUNT_GC) 3 else 2
            val allocArgs = mutableListOf<Expression>(IntLiteral(enumSize.toBigInteger()))
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                allocArgs.add(StringLiteral(findGC(sealedEnum.type).toString()))
            }
            allocArgs.add(TemporaryLocalVariableIndexExpression(enumPtrVar))

            fn.code.code.add(VariableStatement(null, enumPtrVar))
            fn.code.code.add(TemporaryCallStatement(MemoryLib.alloc, allocArgs))

            val enumAddr = TemporaryHeapGetExpression(TemporaryLocalVariableIndexExpression(enumPtrVar))

            if (CompilationConstants.REFCOUNT_GC) {
                fn.code.code.add(TemporaryHeapSetStatement(enumAddr, IntLiteral(1.toBigInteger())))
            }

            val tagOffset = if (CompilationConstants.REFCOUNT_GC) 1 else 0
            val ptrOffset = tagOffset + 1

            val effectiveTag = if (isEmpty) -tag-1 else tag

            fn.code.code.add(TemporaryHeapSetStatement(
                index = BinaryExpression(enumAddr, BinaryOperator.ADD, IntLiteral(tagOffset.toBigInteger())),
                data = IntLiteral(effectiveTag.toBigInteger())
            ))
            fn.code.code.add(TemporaryHeapSetStatement(
                index = BinaryExpression(enumAddr, BinaryOperator.ADD, IntLiteral(ptrOffset.toBigInteger())),
                data = LocalVariableExpression(payloadVar)
            ))

            fn.code.code.add(ReturnStatement(LocalVariableExpression(enumPtrVar)))
            lib.functions.add(fn)
        }
        sealedEnum.allocFuncs[variantStruct] = func
        return func
    }

    private fun allocReturnType(struct: Struct): Type {
        return if (struct.sourceAST == StandardLibASTGenerator.compilerLib) struct.type.asNullable() else struct.type
    }

    private fun figureOutReachableStructs(out: MutableMap<ASTFile, List<Struct>>, ast: ASTFile) {
        if (out.containsKey(ast)) return
        out[ast] = ast.structs.map { it }
        ast.imports.forEach { (_, ast) -> figureOutReachableStructs(out, ast) }
    }

    private fun figureOutReachableSealedEnums(out: MutableMap<ASTFile, List<SealedEnum>>, ast: ASTFile) {
        if (out.containsKey(ast)) return
        out[ast] = ast.sealedEnums.toList()
        ast.imports.forEach { (_, imported) -> figureOutReachableSealedEnums(out, imported) }
    }
}