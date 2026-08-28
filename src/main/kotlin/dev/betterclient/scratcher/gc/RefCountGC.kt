package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ArrayLib
import dev.betterclient.scratcher.std.lib.MemoryLib
import java.math.BigInteger

object RefCountGC {
    private var setupDone = false
    private lateinit var lib: ASTFile
    private lateinit var inc: Function
    private lateinit var structDecs: Map<Struct, Function>
    private lateinit var sealedDecs: Map<SealedEnum, Function>

    fun run(
        context: CompilationContext,
        reachableFunctions: MutableList<Function>
    ) {
        reachableFunctions.addAll(instrument(context, reachableFunctions))
    }

    fun instrument(
        context: CompilationContext,
        functions: Collection<Function>
    ): List<Function> {
        setup(context)
        functions.forEach { func ->
            visit(func, RefCountVisitor(
                structDecs = structDecs,
                sealedDecs = sealedDecs,
                inc = inc,
                generateDecList = { list ->
                    getOrCreateDecArray(list, lib, structDecs)
                },
                compilationContext = context,
                currentFunction = func
            ))
        }
        return listOf(inc) + structDecs.values + sealedDecs.values + listDecMap.values
    }

    private fun setup(context: CompilationContext) {
        if (setupDone) return
        setupDone = true

        val reachableStructs = context.asts.flatMap { (_, ast) -> ast.structs } +
                StandardLibASTGenerator.compilerLib.structs +
                StandardLibASTGenerator.lambdaLib.structs
        reachableStructs.forEach { struct ->
            val refParam = Parameter("compiler@refcount", PrimitiveType.Integer)
            struct.parameters.add(0, refParam)
        }

        lib = StandardLibASTGenerator.refCountGC

        val ptrArg = Parameter("ptr", PrimitiveType.Integer)
        inc = Function(
            name = "inc",
            parameters = mutableListOf(ptrArg),
            returnType = PrimitiveType.Void,
            export = false,
            warp = true,
            sourceAST = lib,
            userAccessible = false,
            code = CodeBlock().also { code ->
                code.code.add(isNotNeg1(
                    left = ParameterExpression(ptrArg),
                    then = CodeBlock().also { thenBlock ->
                        thenBlock.code.add(TemporaryHeapSetStatement(
                            index = ParameterExpression(ptrArg),
                            data = BinaryExpression(
                                left = TemporaryHeapGetExpression(ParameterExpression(ptrArg)),
                                right = IntLiteral(BigInteger.ONE),
                                operator = BinaryOperator.ADD
                            )
                        ))
                    }
                ))
            }
        ).also { lib.functions.add(it) }

        structDecs = reachableStructs.associateWith { struct ->
            Function(
                name = "dec@${struct.sourceAST.simplePath}::${struct.name}",
                parameters = mutableListOf(Parameter("ptr", PrimitiveType.Integer)),
                returnType = PrimitiveType.Void,
                export = false,
                warp = true,
                sourceAST = lib,
                userAccessible = false,
            ).also {
                lib.functions.add(it)
            }
        }

        val reachableSealedEnums = context.asts.values.flatMap { it.sealedEnums } +
                StandardLibASTGenerator.compilerLib.sealedEnums +
                StandardLibASTGenerator.lambdaLib.sealedEnums
        sealedDecs = reachableSealedEnums.distinct().associateWith { sealed ->
            Function(
                name = "dec@${sealed.sourceAST.simplePath}::${sealed.name}",
                parameters = mutableListOf(Parameter("ptr", PrimitiveType.Integer)),
                returnType = PrimitiveType.Void,
                export = false,
                warp = true,
                sourceAST = lib,
                userAccessible = false,
            ).also {
                lib.functions.add(it)
            }
        }

        structDecs.forEach { (struct, function) ->
            val ptrArg = function.parameters[0]
            val thenBlock0 = CodeBlock().also {
                struct.parameters.drop(1).forEach { par ->
                    val fieldExpr = MemberExpression(
                        expression = ParameterExpression(ptrArg),
                        member = par,
                        struct = struct
                    )
                    buildDecCall(par.type, fieldExpr, structDecs, lib)?.let { stmt ->
                        it.code.add(stmt)
                    }
                }

                val freePtr: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                    BinaryExpression(
                        left = ParameterExpression(ptrArg),
                        right = IntLiteral(BigInteger.ONE),
                        operator = BinaryOperator.SUBTRACT
                    )
                } else {
                    ParameterExpression(ptrArg)
                }

                val freeSize: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                    IntLiteral((struct.sizeOnHeap + 1).toBigInteger())
                } else {
                    IntLiteral(struct.sizeOnHeap.toBigInteger())
                }

                it.code.add(ExpressionStatement(
                    CallExpression(
                        func = MemoryLib.free,
                        arguments = listOf(freePtr, freeSize)
                    )
                ))
            }

            function.code.code.add(
                isNotNeg1(ParameterExpression(ptrArg), CodeBlock().also { thenBlock ->
                    thenBlock.code.add(VariableAssignmentStatement(
                        target = ParameterExpression(ptrArg),
                        variable = struct.parameters[0],
                        struct = struct,
                        assignment = BinaryExpression(
                            left = MemberExpression(
                                expression = ParameterExpression(ptrArg),
                                member = struct.parameters[0],
                                struct = struct,
                            ),
                            right = IntLiteral(BigInteger.ONE),
                            operator = BinaryOperator.SUBTRACT
                        )
                    ))
                    thenBlock.code.add(IfStatement(
                        condition = BinaryExpression(
                            left = MemberExpression(
                                expression = ParameterExpression(ptrArg),
                                member = struct.parameters[0],
                                struct = struct,
                            ),
                            right = IntLiteral(BigInteger.ZERO),
                            operator = BinaryOperator.EQUAL
                        ),
                        thenBlock = thenBlock0
                    ))
                })
            )
        }

        sealedDecs.forEach { (enumDef, function) ->
            val ptrArg = function.parameters[0]
            val ptr = ParameterExpression(ptrArg)

            fun heapGet(index: Expression) = TemporaryHeapGetExpression(index)
            fun heapGetAt(offset: Long) = heapGet(BinaryExpression(ptr, BinaryOperator.ADD, IntLiteral(offset.toBigInteger())))

            val freePtr: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                BinaryExpression(ptr, BinaryOperator.SUBTRACT, IntLiteral(BigInteger.ONE))
            } else {
                ptr
            }
            val enumSize: Long = if (CompilationConstants.REFCOUNT_GC) 3 else 2
            val freeSize: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                IntLiteral((enumSize + 1).toBigInteger())
            } else {
                IntLiteral(enumSize.toBigInteger())
            }

            function.code.code.add(isNotNeg1(ptr, CodeBlock().also { body ->
                body.code.add(TemporaryHeapSetStatement(
                    index = ptr,
                    data = BinaryExpression(heapGet(ptr), BinaryOperator.SUBTRACT, IntLiteral(BigInteger.ONE))
                ))
                body.code.add(IfStatement(
                    condition = BinaryExpression(heapGet(ptr), BinaryOperator.EQUAL, IntLiteral(BigInteger.ZERO)),
                    thenBlock = CodeBlock().also { freeBlock ->
                        val taggedVariants = enumDef.types.withIndex().filter { it.value.parameters.isNotEmpty() }
                        if (taggedVariants.isNotEmpty()) {
                            fun dispatch(lo: Int, hi: Int): Statement {
                                val mid = (lo + hi) / 2
                                val variant = taggedVariants[mid].value
                                return IfElseStatement(
                                    condition = BinaryExpression(heapGetAt(1), BinaryOperator.LESS_THAN, IntLiteral(mid.toBigInteger())),
                                    thenBlock = if (lo <= mid - 1) CodeBlock().also { it.code.add(dispatch(lo, mid - 1)) } else CodeBlock(),
                                    elseBlock = CodeBlock().also { eqOrGreater ->
                                        eqOrGreater.code.add(IfElseStatement(
                                            condition = BinaryExpression(heapGetAt(1), BinaryOperator.EQUAL, IntLiteral(mid.toBigInteger())),
                                            thenBlock = CodeBlock().also { decBlock ->
                                                buildDecCall(variant.type, heapGetAt(2), structDecs, lib)?.let { decBlock.code.add(it) }
                                            },
                                            elseBlock = if (mid + 1 <= hi) CodeBlock().also { it.code.add(dispatch(mid + 1, hi)) } else CodeBlock()
                                        ))
                                    }
                                )
                            }
                            freeBlock.code.add(dispatch(0, taggedVariants.lastIndex))
                        }
                        freeBlock.code.add(ExpressionStatement(CallExpression(MemoryLib.free, listOf(freePtr, freeSize))))
                    }
                ))
            }))
        }
    }

    private fun isNotNeg1(left: Expression, then: CodeBlock): Statement {
        return IfStatement(
            condition = BinaryExpression(
                left = left,
                right = StringLiteral("null"),
                operator = BinaryOperator.NOT_EQUAL
            ),
            thenBlock = then
        )
    }

    private val listDecMap = mutableMapOf<Type, Function>()

    private fun getOrCreateDecArray(
        arrayType: ArrayType,
        lib: ASTFile,
        structDecs: Map<Struct, Function>
    ): Function {
        val key = arrayType.elementType.asNonNull()
        return listDecMap.getOrPut(key) {
            val ptrArg = Parameter("ptr", PrimitiveType.Integer)
            val function = Function(
                name = "decList@${arrayType.toSafeString()}",
                parameters = mutableListOf(ptrArg),
                returnType = PrimitiveType.Void,
                export = false,
                warp = true,
                sourceAST = lib,
                userAccessible = false
            )
            lib.functions.add(function)

            val thenBlock = CodeBlock()

            thenBlock.code.add(TemporaryHeapSetStatement(
                index = ParameterExpression(ptrArg),
                data = BinaryExpression(
                    left = TemporaryHeapGetExpression(ParameterExpression(ptrArg)),
                    right = IntLiteral(BigInteger.ONE),
                    operator = BinaryOperator.SUBTRACT
                )
            ))

            val freeBlock = CodeBlock()
            val elemType = arrayType.elementType.asNonNull()

            if (elemType is SimpleType || elemType is ArrayType) {
                val iVar = LocalVariable("i", PrimitiveType.Integer)
                freeBlock.localVariables.add(iVar)
                freeBlock.code.add(VariableStatement(IntLiteral(BigInteger.ZERO), iVar))

                val lengthExpr = TemporaryHeapGetExpression(
                    if (ArrayLib.lengthOffset == 0) ParameterExpression(ptrArg)
                    else BinaryExpression(ParameterExpression(ptrArg), BinaryOperator.ADD, IntLiteral(ArrayLib.lengthOffset.toBigInteger()))
                )
                val dataPtrExpr = if (ArrayLib.dataOffset == 0) ParameterExpression(ptrArg)
                    else BinaryExpression(ParameterExpression(ptrArg), BinaryOperator.ADD, IntLiteral(ArrayLib.dataOffset.toBigInteger()))

                val itemExpr = TemporaryHeapGetExpression(
                    BinaryExpression(dataPtrExpr, BinaryOperator.ADD, LocalVariableExpression(iVar))
                )

                val loopBlock = CodeBlock()
                buildDecCall(elemType, itemExpr, structDecs, lib)?.let { decStmt ->
                    loopBlock.code.add(decStmt)
                }
                loopBlock.code.add(LocalVariableAssignmentStatement(
                    iVar,
                    BinaryExpression(LocalVariableExpression(iVar), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                ))

                freeBlock.code.add(WhileStatement(
                    condition = BinaryExpression(LocalVariableExpression(iVar), BinaryOperator.LESS_THAN, lengthExpr),
                    block = loopBlock
                ))
            }

            freeBlock.code.add(ExpressionStatement(CallExpression(ArrayLib.free, listOf(ParameterExpression(ptrArg)))))

            thenBlock.code.add(IfStatement(
                condition = BinaryExpression(
                    left = TemporaryHeapGetExpression(ParameterExpression(ptrArg)),
                    right = IntLiteral(BigInteger.ZERO),
                    operator = BinaryOperator.EQUAL
                ),
                thenBlock = freeBlock
            ))

            function.code.code.add(isNotNeg1(ParameterExpression(ptrArg), thenBlock))
            function
        }
    }

    private fun buildDecCall(
        type: Type,
        expr: Expression,
        structDecs: Map<Struct, Function>,
        lib: ASTFile
    ): Statement? {
        val targetType = type.asNonNull()
        return when (targetType) {
            is SimpleType -> {
                val targetStruct = targetType.sourceAST.structs.find { it.name == targetType.name } ?: return null
                val decFunc = structDecs[targetStruct] ?: return null
                ExpressionStatement(CallExpression(decFunc, listOf(expr)))
            }
            is ArrayType -> {
                val decListFunc = getOrCreateDecArray(targetType, lib, structDecs)
                ExpressionStatement(CallExpression(decListFunc, listOf(expr)))
            }
            is SealedEnumType -> {
                val decFunc = sealedDecs.entries.find { it.key.type == targetType }?.value ?: return null
                ExpressionStatement(CallExpression(decFunc, listOf(expr)))
            }
            else -> null
        }
    }
}