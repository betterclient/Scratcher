package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.std.StandardLibASTGenerator

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
                private = true
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
            action: (TLStaticList, List<Parameter>) -> List<Statement>
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
                returnType = returnType
            ) { list ->
                action(list, parsList)
            }.also {
                lib.functions.add(it)
            }
        }

        fun singleStmtList(
            name: String,
            vararg pars: Parameter,
            receiver: Boolean = true,
            operator: Boolean = false,
            returnType: PrimitiveType = PrimitiveType.Void,
            action: (TLStaticList, List<Parameter>) -> Statement
        ) = newList(
            name,
            *pars,
            receiver = receiver,
            operator = operator,
            returnType = returnType,
            action = { list, parameters -> listOf(action(list, parameters)) }
        )

        singleStmtList(
            name = "add",
            Parameter("item", PrimitiveType.Str)
        ) { list, par ->
            StaticListAddStatement(list, ParameterExpression(par[0]))
        }

        val clearFunc = singleStmtList(
            "clear"
        ) { list, _ ->
            StaticListClearStatement(list)
        }

        singleStmtList(
            "get",
            Parameter("index", PrimitiveType.Integer),
            operator = true,
            returnType = PrimitiveType.Str
        ) { list, pars ->
            ReturnStatement(StaticListItemExpression(
                list = list,
                index = BinaryExpression(
                    left = ParameterExpression(pars[0]),
                    right = IntLiteral(1.toBigInteger()),
                    operator = BinaryOperator.ADD
                ) //compensate for 0-indexing
            ))
        }

        singleStmtList("length", returnType = PrimitiveType.Integer) { list, _ ->
            ReturnStatement(StaticListLengthExpression(list))
        }

        singleStmtList(
            "set",
            Parameter("index", PrimitiveType.Integer),
            Parameter("value", PrimitiveType.Str),
            operator = true
        ) { list, pars ->
            StaticListSetStatement(
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
            "removeAt",
            Parameter("index", PrimitiveType.Integer)
        ) { list, pars ->
            StaticListRemoveStatement(
                list = list,
                index = BinaryExpression(
                    left = ParameterExpression(pars[0]),
                    right = IntLiteral(1.toBigInteger()),
                    operator = BinaryOperator.ADD
                )
            )
        }

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
            "contains",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Bool
        ) { list, pars ->
            ReturnStatement(
                StaticListContainsExpression(
                    list = list,
                    item = ParameterExpression(pars[0])
                )
            )
        }

        singleStmtList(
            "indexOf",
            Parameter("item", PrimitiveType.Str),
            returnType = PrimitiveType.Integer
        ) { list, pars ->
            ReturnStatement(
                BinaryExpression(
                    left = StaticListItemIndexExpression(
                        list = list,
                        item = ParameterExpression(pars[0])
                    ),
                    right = IntLiteral(1.toBigInteger()),
                    operator = BinaryOperator.SUBTRACT
                )
            )
        }

        val listParam = Parameter("list", pooledList.type)
        lib.functions.add(Function(
            name = "free",
            sourceAST = lib,
            private = false,
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
        action: (TLStaticList) -> List<Statement>
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

        val cachedIndex = LocalVariable("dispatch_idx", PrimitiveType.Integer)
        out.code.code.add(VariableStatement(targetIndexExpr, cachedIndex))

        val dispatchTree = generateBinarySearch(
            targetIndex = LocalVariableExpression(cachedIndex),
            low = 1,
            high = CompilationConstants.LIST_POOL_SIZE,
            pool = pool,
            action = action
        )
        out.code.code.addAll(dispatchTree)

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