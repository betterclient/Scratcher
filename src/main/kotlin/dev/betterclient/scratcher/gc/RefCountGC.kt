package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.std.lib.MemoryLib
import java.math.BigInteger

object RefCountGC {
    private var setupDone = false
    private lateinit var lib: ASTFile
    private lateinit var inc: Function
    private lateinit var structDecs: Map<Struct, Function>

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
                inc = inc,
                generateDecList = { list ->
                    getOrCreateDecList(list, lib, structDecs)
                },
                compilationContext = context,
                currentFunction = func
            ))
        }
        return listOf(inc) + structDecs.values + listDecMap.values
    }

    private fun setup(context: CompilationContext) {
        if (setupDone) return
        setupDone = true

        val reachableStructs = context.asts.flatMap { (_, ast) -> ast.structs }
        reachableStructs.forEach { struct ->
            val refParam = Parameter("compiler@refcount", PrimitiveType.Integer)
            struct.parameters.add(0, refParam)

            val allocFunc = struct.allocFunc
            val ptrVar = allocFunc.code.localVariables.find { it.name == "compiler@ptr" }
            if (ptrVar != null) {
                allocFunc.code.code.add(2, VariableAssignmentStatement(
                    target = LocalVariableExpression(ptrVar),
                    variable = refParam,
                    struct = struct,
                    assignment = IntLiteral(BigInteger.ONE)
                ))
            }
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
    }

    private fun isNotNeg1(left: Expression, then: CodeBlock): Statement {
        return IfStatement(
            condition = BinaryExpression(
                left = left,
                right = IntLiteral(BigInteger.valueOf(-1L)),
                operator = BinaryOperator.NOT_EQUAL
            ),
            thenBlock = then
        )
    }

    private val listDecMap = mutableMapOf<Type, Function>()

    private fun getOrCreateDecList(
        listType: ListType,
        lib: ASTFile,
        structDecs: Map<Struct, Function>
    ): Function {
        val key = listType.elementType.asNonNull()
        return listDecMap.getOrPut(key) {
            val ptrArg = Parameter("ptr", PrimitiveType.Integer)
            val function = Function(
                name = "decList@${listType.toSafeString()}",
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
            val elemType = listType.elementType.asNonNull()

            if (elemType is SimpleType || elemType is ListType) {
                val iVar = LocalVariable("i", PrimitiveType.Integer)
                freeBlock.localVariables.add(iVar)
                freeBlock.code.add(VariableStatement(IntLiteral(BigInteger.ZERO), iVar))

                val lengthExpr = TemporaryHeapGetExpression(
                    if (ListLib.lengthOffset == 0) ParameterExpression(ptrArg)
                    else BinaryExpression(ParameterExpression(ptrArg), BinaryOperator.ADD, IntLiteral(ListLib.lengthOffset.toBigInteger()))
                )
                val dataPtrExpr = TemporaryHeapGetExpression(
                    if (ListLib.dataPtrOffset == 0) ParameterExpression(ptrArg)
                    else BinaryExpression(ParameterExpression(ptrArg), BinaryOperator.ADD, IntLiteral(ListLib.dataPtrOffset.toBigInteger()))
                )

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

            freeBlock.code.add(ExpressionStatement(CallExpression(ListLib.free, listOf(ParameterExpression(ptrArg)))))

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
            is ListType -> {
                val decListFunc = getOrCreateDecList(targetType, lib, structDecs)
                ExpressionStatement(CallExpression(decListFunc, listOf(expr)))
            }
            else -> null
        }
    }
}