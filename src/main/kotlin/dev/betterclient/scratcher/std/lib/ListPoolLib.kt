package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.codegen.ast.TurboWarpBoolExpressions
import dev.betterclient.scratcher.codegen.ast.TurboWarpListExpressions
import dev.betterclient.scratcher.codegen.ast.TurboWarpListStatements
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.obfuscate
import java.math.BigInteger

object ListPoolLib {
    fun init(lib: ASTFile) {
        val pooledList = Struct(
            name = "PooledList",
            parameters = mutableListOf(
                Parameter("index", PrimitiveType.Integer, true)
            ),
            sourceAST = lib,
            private = false
        ).also { lib.structs.add(it) }
        if (CompilationContext.options.gc.markAndSweepGC) {
            addGC(StructGCInfo(pooledList.type, pooledList))
        }

        MemoryLib.initMem(CompilationContext.std.memLib, lib)

        val pool = TLStaticList(
            name = "Pool",
            sourceAST = lib,
            private = true
        ).also { lib.staticLists.add(it) }

        val lists = (1..CompilationContext.options.std.listPoolSize).map { index ->
            TLStaticList(
                name = "Pool$index",
                sourceAST = lib,
                private = true,
                scratchList = ScratchList(
                    if (CompilationContext.options.experimental.turbowarp) "list_pool::Pool$index"
                    else obfuscate("list_pool::Pool$index")
                )
            ).also { lib.staticLists.add(it) }
        }
        pool.scratchList.items.addAll(
            (1..CompilationContext.options.std.listPoolSize).map {
                "0"
            }
        )

        lib.functions.add(Function(
            name = "allocate",
            sourceAST = lib,
            private = false,
            userAccessible = true,
            warp = true,
            export = false,
            operator = false,
            parameters = mutableListOf(),
            returnType = pooledList.type,
        ).also {
            it.code.code.apply {
                find(pool, pooledList)

                //panic
                add(ExpressionStatement(
                    CallExpression(
                        func = ExceptionLib.panic,
                        arguments = listOf(StringLiteral("Cannot allocate a list as pool is empty!", null)),
                        position = null
                    ),
                    null
                ))
            }
        })

        lib.functions.add(Function(
            name = "allocateOrNull",
            sourceAST = lib,
            private = false,
            userAccessible = true,
            warp = true,
            export = false,
            operator = false,
            parameters = mutableListOf(),
            returnType = pooledList.type.asNullable(),
        ).also {
            it.code.code.apply {
                find(pool, pooledList)

                //null
                add(ReturnStatement(NullExpression, null))
            }
        })

        fun newList(
            name: String,
            vararg pars: Parameter,
            receiver: Boolean = true,
            operator: Boolean = false,
            returnType: PrimitiveType = PrimitiveType.Void,
            turbowarpAction: ((Expression, List<Parameter>) -> List<Statement>)? = null,
            fallbackAction: (TLStaticList, List<Parameter>) -> List<Statement>
        ): Function {
            val parsList = pars.toList()
            return newListFunc(
                lib = lib,
                name = name,
                receiver = receiver,
                operator = operator,
                parameters = parsList,
                pooledList = pooledList,
                pool = lists,
                returnType = returnType,
                turbowarpAction = turbowarpAction?.let { act -> { dynamicName -> act(dynamicName, parsList) } },
                fallbackAction = { list -> fallbackAction(list, parsList) }
            ).also {
                lib.functions.add(it)
            }
        }

        fun singleStmtList(
            name: String,
            vararg pars: Parameter,
            receiver: Boolean = true,
            operator: Boolean = false,
            returnType: PrimitiveType = PrimitiveType.Void,
            turbowarpAction: ((Expression, List<Parameter>) -> Statement)? = null,
            fallbackAction: (TLStaticList, List<Parameter>) -> Statement
        ) = newList(
            name,
            *pars,
            receiver = receiver,
            operator = operator,
            returnType = returnType,
            turbowarpAction = turbowarpAction?.let { act -> { dyn, p -> listOf(act(dyn, p)) } },
            fallbackAction = { list, parameters -> listOf(fallbackAction(list, parameters)) }
        )

        singleStmtList(
            name = "add",
            Parameter("item", PrimitiveType.Str),
            turbowarpAction = { dynList, pars ->
                TemporaryScratchStmt(listOf(dynList, ParameterExpression(pars[0], null)), { args ->
                    listOf(TurboWarpListStatements.Add(args[0], args[1]))
                }, null)
            },
            fallbackAction = { list, par ->
                StaticListAddStatement(list, ParameterExpression(par[0], null), null)
            }
        )

        val clearFunc = singleStmtList(
            name = "clear",
            turbowarpAction = { dynList, _ ->
                TemporaryScratchStmt(listOf(dynList), { args ->
                    listOf(TurboWarpListStatements.Clear(args[0]))
                }, null)
            },
            fallbackAction = { list, _ ->
                StaticListClearStatement(list, null)
            }
        )

        singleStmtList(
            name = "get",
            Parameter("index", PrimitiveType.Integer),
            operator = true,
            returnType = PrimitiveType.Str,
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null)
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList, index1Based), { args ->
                        TurboWarpListExpressions.GetItem(args[0], args[1])
                    }, null),
                    null
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(StaticListItemExpression(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null),
                    position = null
                ), null)
            }
        )

        singleStmtList(
            name = "length",
            returnType = PrimitiveType.Integer,
            turbowarpAction = { dynList, _ ->
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList), { args ->
                        TurboWarpListExpressions.Length(args[0])
                    }, null),
                    null
                )
            },
            fallbackAction = { list, _ ->
                ReturnStatement(StaticListLengthExpression(list, null), null)
            }
        )

        singleStmtList(
            name = "set",
            Parameter("index", PrimitiveType.Integer),
            Parameter("value", PrimitiveType.Str),
            operator = true,
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null)
                TemporaryScratchStmt(listOf(dynList, index1Based, ParameterExpression(pars[1], null)), { args ->
                    listOf(TurboWarpListStatements.Replace(args[0], args[1], args[2]))
                }, null)
            },
            fallbackAction = { list, pars ->
                StaticListSetStatement(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null),
                    value = ParameterExpression(pars[1], null),
                    position = null
                )
            }
        )

        singleStmtList(
            name = "removeAt",
            Parameter("index", PrimitiveType.Integer),
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null)
                TemporaryScratchStmt(listOf(dynList, index1Based), { args ->
                    listOf(TurboWarpListStatements.Delete(args[0], args[1]))
                }, null)
            },
            fallbackAction = { list, pars ->
                StaticListRemoveStatement(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0], null), BinaryOperator.ADD, IntLiteral(BigInteger.ONE, null), null),
                    position = null
                )
            }
        )

        singleStmtList(
            "insert",
            Parameter("index", PrimitiveType.Integer),
            Parameter("item", PrimitiveType.Str)
        ) { list, pars ->
            StaticListInsertStatement(
                list = list,
                index = BinaryExpression(
                    left = ParameterExpression(pars[0], null),
                    right = IntLiteral(1.toBigInteger(), null),
                    operator = BinaryOperator.ADD,
                    position = null
                ),
                value = ParameterExpression(pars[1], null),
                position = null
            )
        }

        singleStmtList(
            name = "contains",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Bool,
            turbowarpAction = { dynList, pars ->
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList, ParameterExpression(pars[0], null)), { args ->
                        TurboWarpBoolExpressions.ListContains(args[0], args[1])
                    }, null),
                    null
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(StaticListContainsExpression(list, ParameterExpression(pars[0], null), null), null)
            }
        )


        singleStmtList(
            name = "indexOf",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Integer,
            turbowarpAction = { dynList, pars ->
                ReturnStatement(
                    BinaryExpression(
                        left = TemporaryScratchExpr(listOf(dynList, ParameterExpression(pars[0], null)), { args ->
                            TurboWarpListExpressions.IndexOf(args[0], args[1])
                        }, null),
                        right = IntLiteral(BigInteger.ONE, null),
                        operator = BinaryOperator.SUBTRACT,
                        position = null
                    ),
                    null
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(
                    BinaryExpression(
                        left = StaticListItemIndexExpression(list, ParameterExpression(pars[0], null), null),
                        right = IntLiteral(BigInteger.ONE, null),
                        operator = BinaryOperator.SUBTRACT,
                        position = null
                    ),
                    null
                )
            }
        )


        val listParam = Parameter("list", pooledList.type)
        lib.functions.add(Function(
            name = "free",
            sourceAST = lib,
            private = CompilationContext.options.gc.refCountGC, //refcount GC auto frees the list
            userAccessible = true,
            warp = true,
            operator = false,
            isReceiver = true, //allow list.free()
            export = false,
            parameters = mutableListOf(
                listParam
            ),
            returnType = PrimitiveType.Void,
        ).also {
            it.code.code.apply {
                //list.clear();
                add(ExpressionStatement(CallExpression(
                    func = clearFunc,
                    arguments = listOf(ParameterExpression(listParam, null)),
                    position = null
                ), null))

                //pool[list.index] = "0";
                add(StaticListSetStatement(
                    list = pool,
                    index = MemberExpression(ParameterExpression(listParam, null), member = pooledList.parameters.find { it.name == "index" }!!, pooledList, null),
                    value = StringLiteral("0", null),
                    position = null
                ))
            }
        })
    }

    private fun newListFunc(
        lib: ASTFile,
        name: String,
        operator: Boolean = false,
        receiver: Boolean = true,
        parameters: List<Parameter>,
        pooledList: Struct,
        pool: List<TLStaticList>,
        returnType: PrimitiveType = PrimitiveType.Void,
        turbowarpAction: ((Expression) -> List<Statement>)?,
        fallbackAction: (TLStaticList) -> List<Statement>
    ): Function {
        val listParam = Parameter("list", pooledList.type)
        val out = Function(
            name = name,
            sourceAST = lib,
            private = false,
            userAccessible = true,
            warp = true,
            operator = operator,
            isReceiver = receiver,
            export = false,
            parameters = (listOf(listParam) + parameters).toMutableList(),
            returnType = returnType,
        )

        val targetIndexExpr = MemberExpression(
            ParameterExpression(listParam, null),
            member = pooledList.parameters.first { it.name == "index" },
            struct = pooledList,
            position = null
        )

        if (CompilationContext.options.experimental.turbowarp && turbowarpAction != null) {
            val dynamicListName = ConcatExpression(
                StringLiteral("list_pool::Pool", null),
                targetIndexExpr,
                null
            )
            out.code.code.addAll(turbowarpAction(dynamicListName))
        } else {
            val cachedIndex = LocalVariable("dispatch_idx", PrimitiveType.Integer)
            out.code.code.add(VariableStatement(targetIndexExpr, cachedIndex, null))

            val dispatchTree = generateBinarySearch(
                targetIndex = LocalVariableExpression(cachedIndex, null),
                low = 1,
                high = CompilationContext.options.std.listPoolSize,
                pool = pool,
                action = fallbackAction
            )
            out.code.code.addAll(dispatchTree)
        }

        return out
    }

    private fun MutableList<Statement>.find(
        pool: TLStaticList,
        pooledList: Struct
    ) {
        //int index = 1;
        val index = LocalVariable("index", PrimitiveType.Integer)
        add(VariableStatement(IntLiteral(1.toBigInteger(), null), index, null))

        //repeat(pool size)
        add(RepeatStatement(
            amount = IntLiteral(CompilationContext.options.std.listPoolSize.toBigInteger(), null),
            block = CodeBlock().apply {
                //if(pool[index] == "0")
                add(
                    IfStatement(
                        condition = BinaryExpression(
                            left = StaticListItemExpression(pool, LocalVariableExpression(index, null), null),
                            right = StringLiteral("0", null),
                            operator = BinaryOperator.EQUAL,
                            position = null
                        ),
                        thenBlock = CodeBlock().apply {
                            //pool[index] = "1";
                            add(StaticListSetStatement(
                                list = pool,
                                index = LocalVariableExpression(index, null),
                                value = StringLiteral("1", null),
                                position = null
                            ))

                            //return PooledList(index);
                            add(ReturnStatement(
                                CallExpression(
                                    func = pooledList.allocFunc,
                                    arguments = listOf(LocalVariableExpression(index, null)),
                                    position = null
                                ),
                                null
                            ))
                        },
                        position = null
                    ))

                //index++;
                add(LocalVariableAssignmentStatement(
                    index, BinaryExpression(
                        left = LocalVariableExpression(index, null),
                        right = IntLiteral(1.toBigInteger(), null),
                        operator = BinaryOperator.ADD,
                        position = null
                    ),
                    null
                ))
            },
            position = null
        ))
    }

    fun CodeBlock.add(statement: Statement) {
        this.code.add(statement)
    }

    private fun generateBinarySearch(
        targetIndex: Expression,
        low: Int,
        high: Int,
        pool: List<TLStaticList>,
        action: (TLStaticList) -> List<Statement>
    ): List<Statement> {
        if (low >= high) {
            return action(pool[low - 1])
        }

        val mid = (low + high) / 2

        val thenBranch = generateBinarySearch(targetIndex, low, mid, pool, action)
        val elseBranch = generateBinarySearch(targetIndex, mid + 1, high, pool, action)

        val condition = BinaryExpression(
            left = targetIndex,
            right = IntLiteral(mid.toBigInteger(), null),
            operator = BinaryOperator.LESS_EQUAL,
            position = null
        )

        return listOf(
            IfElseStatement(
                condition = condition,
                thenBlock = CodeBlock().apply {
                    thenBranch.forEach { this.code.add(it) }
                },
                elseBlock = CodeBlock().apply {
                    elseBranch.forEach { this.code.add(it) }
                },
                position = null
            )
        )
    }
}