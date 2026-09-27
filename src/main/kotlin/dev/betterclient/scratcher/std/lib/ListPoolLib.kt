package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.TurboWarpBoolExpressions
import dev.betterclient.scratcher.codegen.ast.TurboWarpListExpressions
import dev.betterclient.scratcher.codegen.ast.TurboWarpListStatements
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
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
        if (CompilationConstants.MARK_AND_SWEEP_GC) {
            addGC(StructGCInfo(pooledList.type, pooledList))
        }

        MemoryLib.initMem(StandardLibASTGenerator.memLib, lib)

        val pool = TLStaticList(
            name = "Pool",
            sourceAST = lib,
            private = true
        ).also { lib.staticLists.add(it) }

        val lists = (1..CompilationConstants.LIST_POOL_SIZE).map { index ->
            TLStaticList(
                name = "Pool$index",
                sourceAST = lib,
                private = true,
                scratchList = ScratchList(
                    if (CompilationConstants.TURBOWARP) "list_pool::Pool$index"
                    else obfuscate("list_pool::Pool$index")
                )
            ).also { lib.staticLists.add(it) }
        }
        pool.scratchList.items.addAll(
            (1..CompilationConstants.LIST_POOL_SIZE).map {
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
                        arguments = listOf(StringLiteral("Cannot allocate a list as pool is empty!"))
                    )
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
                add(ReturnStatement(NullExpression))
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
                TemporaryScratchStmt(listOf(dynList, ParameterExpression(pars[0]))) { args ->
                    listOf(TurboWarpListStatements.Add(args[0], args[1]))
                }
            },
            fallbackAction = { list, par ->
                StaticListAddStatement(list, ParameterExpression(par[0]))
            }
        )

        val clearFunc = singleStmtList(
            name = "clear",
            turbowarpAction = { dynList, _ ->
                TemporaryScratchStmt(listOf(dynList)) { args ->
                    listOf(TurboWarpListStatements.Clear(args[0]))
                }
            },
            fallbackAction = { list, _ ->
                StaticListClearStatement(list)
            }
        )

        singleStmtList(
            name = "get",
            Parameter("index", PrimitiveType.Integer),
            operator = true,
            returnType = PrimitiveType.Str,
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList, index1Based)) { args ->
                        TurboWarpListExpressions.GetItem(args[0], args[1])
                    }
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(StaticListItemExpression(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                ))
            }
        )

        singleStmtList(
            name = "length",
            returnType = PrimitiveType.Integer,
            turbowarpAction = { dynList, _ ->
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList)) { args ->
                        TurboWarpListExpressions.Length(args[0])
                    }
                )
            },
            fallbackAction = { list, _ ->
                ReturnStatement(StaticListLengthExpression(list))
            }
        )

        singleStmtList(
            name = "set",
            Parameter("index", PrimitiveType.Integer),
            Parameter("value", PrimitiveType.Str),
            operator = true,
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                TemporaryScratchStmt(listOf(dynList, index1Based, ParameterExpression(pars[1]))) { args ->
                    listOf(TurboWarpListStatements.Replace(args[0], args[1], args[2]))
                }
            },
            fallbackAction = { list, pars ->
                StaticListSetStatement(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE)),
                    value = ParameterExpression(pars[1])
                )
            }
        )

        singleStmtList(
            name = "removeAt",
            Parameter("index", PrimitiveType.Integer),
            turbowarpAction = { dynList, pars ->
                val index1Based = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                TemporaryScratchStmt(listOf(dynList, index1Based)) { args ->
                    listOf(TurboWarpListStatements.Delete(args[0], args[1]))
                }
            },
            fallbackAction = { list, pars ->
                StaticListRemoveStatement(
                    list = list,
                    index = BinaryExpression(ParameterExpression(pars[0]), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
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
                    left = ParameterExpression(pars[0]),
                    right = IntLiteral(1.toBigInteger()),
                    operator = BinaryOperator.ADD
                ),
                value = ParameterExpression(pars[1])
            )
        }

        singleStmtList(
            name = "contains",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Bool,
            turbowarpAction = { dynList, pars ->
                ReturnStatement(
                    TemporaryScratchExpr(listOf(dynList, ParameterExpression(pars[0]))) { args ->
                        TurboWarpBoolExpressions.ListContains(args[0], args[1])
                    }
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(StaticListContainsExpression(list, ParameterExpression(pars[0])))
            }
        )


        singleStmtList(
            name = "indexOf",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Integer,
            turbowarpAction = { dynList, pars ->
                ReturnStatement(
                    BinaryExpression(
                        left = TemporaryScratchExpr(listOf(dynList, ParameterExpression(pars[0]))) { args ->
                            TurboWarpListExpressions.IndexOf(args[0], args[1])
                        },
                        right = IntLiteral(BigInteger.ONE),
                        operator = BinaryOperator.SUBTRACT
                    )
                )
            },
            fallbackAction = { list, pars ->
                ReturnStatement(
                    BinaryExpression(
                        left = StaticListItemIndexExpression(list, ParameterExpression(pars[0])),
                        right = IntLiteral(BigInteger.ONE),
                        operator = BinaryOperator.SUBTRACT
                    )
                )
            }
        )


        val listParam = Parameter("list", pooledList.type)
        lib.functions.add(Function(
            name = "free",
            sourceAST = lib,
            private = CompilationConstants.REFCOUNT_GC, //refcount GC auto frees the list
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
                    arguments = listOf(ParameterExpression(listParam))
                )))

                //pool[list.index] = "0";
                add(StaticListSetStatement(
                    list = pool,
                    index = MemberExpression(ParameterExpression(listParam), member = pooledList.parameters.find { it.name == "index" }!!, pooledList),
                    value = StringLiteral("0")
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
            ParameterExpression(listParam),
            member = pooledList.parameters.first { it.name == "index" },
            struct = pooledList
        )

        if (CompilationConstants.TURBOWARP && turbowarpAction != null) {
            val dynamicListName = ConcatExpression(
                StringLiteral("list_pool::Pool"),
                targetIndexExpr
            )
            out.code.code.addAll(turbowarpAction(dynamicListName))
        } else {
            val cachedIndex = LocalVariable("dispatch_idx", PrimitiveType.Integer)
            out.code.code.add(VariableStatement(targetIndexExpr, cachedIndex))

            val dispatchTree = generateBinarySearch(
                targetIndex = LocalVariableExpression(cachedIndex),
                low = 1,
                high = CompilationConstants.LIST_POOL_SIZE,
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
        add(VariableStatement(IntLiteral(1.toBigInteger()), index))

        //repeat(pool size)
        add(RepeatStatement(
            amount = IntLiteral(CompilationConstants.LIST_POOL_SIZE.toBigInteger()),
            block = CodeBlock().apply {
                //if(pool[index] == "0")
                add(
                    IfStatement(
                        condition = BinaryExpression(
                            left = StaticListItemExpression(pool, LocalVariableExpression(index)),
                            right = StringLiteral("0"),
                            operator = BinaryOperator.EQUAL
                        ),
                        thenBlock = CodeBlock().apply {
                            //pool[index] = "1";
                            add(StaticListSetStatement(
                                list = pool,
                                index = LocalVariableExpression(index),
                                value = StringLiteral("1")
                            ))

                            //return PooledList(index);
                            add(ReturnStatement(
                                CallExpression(
                                    func = pooledList.allocFunc,
                                    arguments = listOf(LocalVariableExpression(index)),
                                )
                            ))
                        }
                    ))

                //index++;
                add(LocalVariableAssignmentStatement(
                    index, BinaryExpression(
                        left = LocalVariableExpression(index),
                        right = IntLiteral(1.toBigInteger()),
                        operator = BinaryOperator.ADD
                    )
                ))
            }
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
            right = IntLiteral(mid.toBigInteger()),
            operator = BinaryOperator.LESS_EQUAL
        )

        return listOf(
            IfElseStatement(
                condition = condition,
                thenBlock = CodeBlock().apply {
                    thenBranch.forEach { this.code.add(it) }
                },
                elseBlock = CodeBlock().apply {
                    elseBranch.forEach { this.code.add(it) }
                }
            )
        )
    }
}