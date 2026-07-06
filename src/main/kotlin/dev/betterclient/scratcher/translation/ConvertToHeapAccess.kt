package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.gc.GCInfo
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.gc.StackGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.gc.name
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib
import kotlin.collections.plus
import kotlin.math.exp

class ConvertToHeapAccess(
    val functions: List<Function>
) {
    private val temporaryExpression = mutableMapOf<Function, TemporaryHeapGetExpression>() //used as a marker
    private val temporaryNameExpression = mutableMapOf<Function, TemporaryHeapGetExpression>()
    private lateinit var hasLocalsMap: Map<Function, Boolean>

    fun run(): Map<Function, Pair<Int, GCInfo>> {
        val mutableHasLocalsMap = functions.associateWith { func ->
            countInternalLocals(func.code).isNotEmpty()
        }.toMutableMap()

        val callsMap = functions.associateWith { func ->
            getCalls(func.code)
        }

        var changed = true
        while (changed) {
            changed = false
            for (func in functions) {
                if (mutableHasLocalsMap[func] != true) {
                    val calls = callsMap[func] ?: emptyList()
                    if (calls.any { mutableHasLocalsMap[it] == true }) {
                        mutableHasLocalsMap[func] = true
                        changed = true
                    }
                }
            }
        }
        hasLocalsMap = mutableHasLocalsMap

        println("Add stack parameter")
        val stacks = functions.filter { it !is StandardLibASTFunction }.map { func ->
            Parameter(obfuscate("compiler@stack"), Type.int).also { func.parameters.add(0, it) } to func
        }

        println("Add free(stack) and alloc(stack)")
        stacks.forEach { (par, func) ->
            addAllocAndFreeStack(func, func.code, par)
        }

        println("Re-parse locals again")
        functions.forEach { ReParseLocalVariables(it).run() }

        println("Count locals")
        val newFuncs = functions
            .filter { it !is StandardLibASTFunction }
            .associateWith { countLocals(it, it.code) }

        println("Convert to heap")
        for (function in newFuncs.keys) {
            convert(function.code, function) { newFuncs[it]!! }
        }

        return newFuncs.mapValues { (_, data) ->
            val (list, info) = data
            list.size to info
        }
    }

    private fun convert(
        block: CodeBlock,
        currentFunction: Function,
        getFunctionLocals: (Function) -> Pair<List<LocalVariable>, GCInfo>,
    ) {
        block.code.map { convertStatement(it, currentFunction, getFunctionLocals) }.reduceOrNull { a, b -> a + b }?.let {
            block.code.clear()
            block.code.addAll(it)
        }
    }

    private fun convertStatement(
        statement: Statement,
        currentFunction: Function,
        getFunctionLocals: (Function) -> Pair<List<LocalVariable>, GCInfo>
    ): List<Statement> {
        val curFunc = getFunctionLocals(currentFunction).first
        val stackPar = currentFunction.parameters.first()
        return when (statement) {
            is ExpressionStatement -> listOf(
                ExpressionStatement(
                    convertExpression(statement.expression, currentFunction, getFunctionLocals),
                )
            )
            is IfElseStatement -> {
                listOf(IfElseStatement(
                    convertExpression(statement.condition, currentFunction, getFunctionLocals),
                    statement.thenBlock.also { convert(it, currentFunction, getFunctionLocals) },
                    statement.elseBlock.also { convert(it, currentFunction, getFunctionLocals) }
                ))
            }
            is IfStatement -> {
                listOf(IfStatement(
                    convertExpression(statement.condition, currentFunction, getFunctionLocals),
                    statement.thenBlock.also { convert(it, currentFunction, getFunctionLocals) }
                ))
            }
            is LocalVariableAssignmentStatement -> {
                val index = curFunc.indexOf(statement.variable)
                listOf(TemporaryHeapSetStatement(
                    index = if (index == 0) {
                        ParameterExpression(stackPar)
                    } else {
                        BinaryExpression(
                            left = ParameterExpression(stackPar),
                            right = IntLiteral(index.toBigInteger()),
                            operator = BinaryOperator.ADD,
                        )
                    },
                    data = convertExpression(statement.assignment, currentFunction, getFunctionLocals)
                ))
            }
            is RepeatStatement -> {
                listOf(RepeatStatement(
                    amount = convertExpression(statement.amount, currentFunction, getFunctionLocals),
                    block = statement.block.also { convert(it, currentFunction, getFunctionLocals) }
                ))
            }
            is ReturnStatement -> {
                listOf(statement) //return value was deleted a long time ago...
            }
            is TLVariableAssignmentStatement -> {
                listOf(TLVariableAssignmentStatement(
                    variable = statement.variable,
                    sourceAST = statement.sourceAST,
                    assignment = convertExpression(statement.assignment, currentFunction, getFunctionLocals)
                ))
            }
            is TemporaryCallStatement -> {
                listOf(TemporaryCallStatement(
                    statement.func,
                    statement.args.map { convertExpression(it, currentFunction, getFunctionLocals) }.toMutableList()
                ))
            }
            is TemporaryHeapSetStatement -> {
                listOf(TemporaryHeapSetStatement(
                    index = statement.index,
                    data = convertExpression(statement.data, currentFunction, getFunctionLocals)
                ))
            }
            is VariableAssignmentStatement -> {
                val parIndex = statement.struct.getIndex(statement.variable)
                val convertedTarget = convertExpression(statement.target, currentFunction, getFunctionLocals)
                val convertedData = convertExpression(statement.assignment, currentFunction, getFunctionLocals)

                listOf(TemporaryHeapSetStatement(
                    index = if (parIndex == 0) {
                        convertedTarget
                    } else {
                        BinaryExpression(
                            left = convertedTarget,
                            right = IntLiteral(parIndex.toBigInteger()),
                            operator = BinaryOperator.ADD,
                        )
                    },
                    data = convertedData
                ))
            }
            is VariableStatement -> {
                val index = curFunc.indexOf(statement.variable)
                if (statement.defaultValue == null) {
                    listOf()
                } else {
                    listOf(TemporaryHeapSetStatement(
                        index = if (index == 0) {
                            ParameterExpression(stackPar)
                        } else {
                            BinaryExpression(
                                left = ParameterExpression(stackPar),
                                right = IntLiteral(index.toBigInteger()),
                                operator = BinaryOperator.ADD,
                            )
                        },
                        data = convertExpression(statement.defaultValue, currentFunction, getFunctionLocals)
                    ))
                }
            }
            is WhileStatement -> {
                listOf(WhileStatement(
                    condition = convertExpression(statement.condition, currentFunction, getFunctionLocals),
                    block = statement.block.also { convert(it, currentFunction, getFunctionLocals) }
                ))
            }
            is TemporaryScratchStmt -> listOf(TemporaryScratchStmt(
                inputExprs = statement.inputExprs.map { convertExpression(it, currentFunction, getFunctionLocals) },
                stmt = statement.stmt
            ))
            is CompositeStatement -> statement.statements.flatMap { convertStatement(statement, currentFunction, getFunctionLocals) }
        }
    }

    private fun convertExpression(
        expression: Expression,
        currentFunction: Function,
        getFunctionLocals: (Function) -> Pair<List<LocalVariable>, GCInfo>
    ): Expression {
        if (temporaryExpression.containsValue(expression)) {
            val matchedFunction = temporaryExpression.entries.firstOrNull { it.value === expression }?.key
            if (matchedFunction != null) {
                return IntLiteral(getFunctionLocals(matchedFunction).first.size.toBigInteger())
            }
        }

        if (temporaryNameExpression.containsValue(expression)) {
            val matchedFunction = temporaryNameExpression.entries.firstOrNull { it.value === expression }?.key
            if (matchedFunction != null) {
                return StringLiteral(getFunctionLocals(matchedFunction).second.name.toString())
            }
        }

        val curFunc = getFunctionLocals(currentFunction).first
        val stackPar = currentFunction.parameters.first()
        return when(expression) {
            is LocalVariableExpression -> {
                val index = curFunc.indexOf(expression.variable)
                TemporaryHeapGetExpression(
                    index = if (index == 0) {
                        ParameterExpression(stackPar)
                    } else {
                        BinaryExpression(
                            left = ParameterExpression(stackPar),
                            right = IntLiteral(index.toBigInteger()),
                            operator = BinaryOperator.ADD,
                        )
                    }
                )
            }
            is BinaryExpression -> {
                BinaryExpression(
                    left = convertExpression(expression.left, currentFunction, getFunctionLocals),
                    operator = expression.operator,
                    right = convertExpression(expression.right, currentFunction, getFunctionLocals)
                )
            }
            is ConcatExpression -> {
                ConcatExpression(
                    left = convertExpression(expression.left, currentFunction, getFunctionLocals),
                    right = convertExpression(expression.right, currentFunction, getFunctionLocals)
                )
            }
            is TemporaryLocalVariableIndexExpression -> {
                val index = curFunc.indexOf(expression.variable)
                if (index == 0) {
                    ParameterExpression(stackPar)
                } else {
                    BinaryExpression(
                        left = ParameterExpression(stackPar),
                        right = IntLiteral(index.toBigInteger()),
                        operator = BinaryOperator.ADD,
                    )
                }
            }
            is UnaryExpression -> UnaryExpression(
                operator = expression.operator,
                expression = convertExpression(expression.expression, currentFunction, getFunctionLocals)
            )
            is TemporaryScratchExpr -> TemporaryScratchExpr(
                inputExprs = expression.inputExprs.map { convertExpression(it, currentFunction, getFunctionLocals) },
                expression = expression.expression
            )
            is VariableExpression -> expression
            is TemporaryHeapGetExpression -> expression
            is ParameterExpression -> expression
            is BooleanLiteral -> expression
            is FloatLiteral -> expression
            is IntLiteral -> expression
            is StringLiteral -> expression
            is NullExpression -> expression
            is MemberExpression -> {
                val parIndex = expression.struct.getIndex(expression.member)
                val convertedLeft = convertExpression(expression.expression, currentFunction, getFunctionLocals)
                if (parIndex == 0) {
                    TemporaryHeapGetExpression(
                        convertedLeft
                    )
                } else {
                    TemporaryHeapGetExpression(
                        BinaryExpression(
                            left = convertedLeft,
                            right = IntLiteral(parIndex.toBigInteger()),
                            operator = BinaryOperator.ADD,
                        )
                    )
                }
            }
            is CallExpression -> {
                CallExpression(
                    func = expression.func,
                    arguments = expression.arguments.map { convertExpression(it, currentFunction, getFunctionLocals) }
                )
            }

            is NonNullAssertExpression -> throw UnreachableException()
        }
    }

    private fun countLocals(function: Function, code: CodeBlock): Pair<List<LocalVariable>, GCInfo> {
        val out = countInternalLocals(code)
        return out to StackGCInfo(out.map {
            it.type
        }, function).also { addGC(it) }
    }

    private fun countInternalLocals(code: CodeBlock): List<LocalVariable> {
        val vars = mutableListOf<LocalVariable>()
        for (statement in code.code) {
            when (statement) {
                is IfElseStatement -> {
                    vars += countInternalLocals(statement.thenBlock)
                    vars += countInternalLocals(statement.elseBlock)
                }
                is IfStatement -> {
                    vars += countInternalLocals(statement.thenBlock)
                }
                is RepeatStatement -> {
                    vars += countInternalLocals(statement.block)
                }
                is WhileStatement -> {
                    vars += countInternalLocals(statement.block)
                }
                is VariableStatement -> {
                    vars.add(statement.variable)
                }
                is LocalVariableAssignmentStatement -> {}
                is ReturnStatement -> {}
                is TLVariableAssignmentStatement -> {}
                is TemporaryCallStatement -> {}
                is TemporaryHeapSetStatement -> {}
                is TemporaryScratchStmt -> {}
                is VariableAssignmentStatement -> {}
                is CompositeStatement, is ExpressionStatement
                    -> {}
            }
        }
        return vars.distinct()
    }

    fun getTemporary(function: Function) = temporaryExpression.computeIfAbsent(function) { TemporaryHeapGetExpression(IntLiteral(0.toBigInteger())) }
    fun getNameTemporary(function: Function) = temporaryNameExpression.computeIfAbsent(function) { TemporaryHeapGetExpression(IntLiteral(0.toBigInteger())) }


    private fun addAllocAndFreeStack(
        function: Function,
        block: CodeBlock,
        par: Parameter
    ) {
        var returnStmt: ReturnStatement? = null
        val replacements = mutableMapOf<Statement, List<Statement>>()
        for (statement in block.code) {
            when (statement) {
                is IfElseStatement -> {
                    addAllocAndFreeStack(function, statement.thenBlock, par)
                    addAllocAndFreeStack(function, statement.elseBlock, par)
                }
                is IfStatement -> {
                    addAllocAndFreeStack(function, statement.thenBlock, par)
                }
                is RepeatStatement -> {
                    addAllocAndFreeStack(function, statement.block, par)
                }
                is WhileStatement -> {
                    addAllocAndFreeStack(function, statement.block, par)
                }
                is ReturnStatement -> {
                    returnStmt = statement
                    break //no reason to break here cause return is guaranteed to be last, but do anyway
                }
                is ExpressionStatement -> {
                    val expr = statement.expression
                    if (expr is CallExpression && expr.func !is StandardLibASTFunction && expr.func !is InlineStandardLibFunction) {
                        if (hasLocalsMap[expr.func] != true) {
                            replacements[statement] = listOf(
                                ExpressionStatement(CallExpression(expr.func, listOf(NullExpression) + expr.arguments))
                            )
                        } else {
                            val allocVar = LocalVariable(obfuscate("stackAllocationFor${expr.func.name}Call"), Type.int)
                            replacements[statement] = listOfNotNull(
                                VariableStatement(null, allocVar),
                                TemporaryCallStatement(MemoryLib.alloc, mutableListOf(getTemporary(expr.func), getNameTemporary(expr.func), TemporaryLocalVariableIndexExpression(allocVar))),
                                TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                                    listOf(ListStatements.AddToList(GCLib.rootsList, scratchArgs[0]))
                                },
                                ExpressionStatement(CallExpression(expr.func, listOf(LocalVariableExpression(allocVar)) + expr.arguments)),
                                TemporaryCallStatement(MemoryLib.free, mutableListOf(LocalVariableExpression(allocVar), getTemporary(expr.func))),
                                TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchExpressions ->
                                    listOf(ListStatements.DeleteItem(GCLib.rootsList, ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])))
                                }
                            )
                        }
                    }
                }
                is LocalVariableAssignmentStatement -> {
                    val expr = statement.assignment
                    if (expr is CallExpression && expr.func !is StandardLibASTFunction && expr.func !is InlineStandardLibFunction) {
                        if (hasLocalsMap[expr.func] != true) {
                            replacements[statement] = listOf(
                                LocalVariableAssignmentStatement(statement.variable, CallExpression(expr.func, listOf(NullExpression) + expr.arguments))
                            )
                        } else {
                            val allocVar = LocalVariable(obfuscate("stackAllocationFor${expr.func.name}Call"), Type.int)
                            replacements[statement] = listOfNotNull(
                                VariableStatement(null, allocVar),
                                TemporaryCallStatement(MemoryLib.alloc, mutableListOf(getTemporary(expr.func), getNameTemporary(expr.func), TemporaryLocalVariableIndexExpression(allocVar))),
                                TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                                    listOf(ListStatements.AddToList(GCLib.rootsList, scratchArgs[0]))
                                },
                                LocalVariableAssignmentStatement(statement.variable, CallExpression(expr.func, listOf(LocalVariableExpression(allocVar)) + expr.arguments)),
                                TemporaryCallStatement(MemoryLib.free, mutableListOf(LocalVariableExpression(allocVar), getTemporary(expr.func))),
                                TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchExpressions ->
                                    listOf(ListStatements.DeleteItem(GCLib.rootsList, ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])))
                                }
                            )
                        }
                    }
                }
                is TemporaryCallStatement -> {
                    if (statement.func is StandardLibASTFunction) continue

                    if (hasLocalsMap[statement.func] != true) {
                        replacements[statement] = listOf(
                            statement.copy(args = (listOf(NullExpression) + statement.args).toMutableList())
                        )
                    } else {
                        val allocVar = LocalVariable(obfuscate("stackAllocationFor${statement.func.name}Call"), Type.int)
                        replacements[statement] = listOfNotNull(
                            VariableStatement(null, allocVar),
                            TemporaryCallStatement(
                                MemoryLib.alloc,
                                mutableListOf(getTemporary(statement.func), getNameTemporary(statement.func), TemporaryLocalVariableIndexExpression(allocVar))
                            ),
                            TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                                listOf(
                                    ListStatements.AddToList(GCLib.rootsList, scratchArgs[0])
                                )
                            },
                            statement.copy(args = (listOf(LocalVariableExpression(allocVar)) + statement.args).toMutableList()),
                            TemporaryCallStatement(
                                MemoryLib.free,
                                mutableListOf(LocalVariableExpression(allocVar), getTemporary(statement.func))
                            ),
                            TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchExpressions ->
                                listOf(ListStatements.DeleteItem(GCLib.rootsList, ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])))
                            }
                        )
                    }
                }
                is TLVariableAssignmentStatement -> {}
                is TemporaryHeapSetStatement -> {}
                is VariableAssignmentStatement -> {}
                is VariableStatement -> {}
                is TemporaryScratchStmt -> {}
                is CompositeStatement -> {}
            }
        }

        val freeStmt = TemporaryCallStatement(
            MemoryLib.free,
            args = mutableListOf(ParameterExpression(par), getTemporary(function))
        )
        val deleteStmt = TemporaryScratchStmt(listOf(ParameterExpression(par))) { scratchExpressions ->
            listOf(
                ListStatements.DeleteItem(
                    GCLib.rootsList,
                    ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])
                )
            )
        }

        val exitStatements = mutableListOf<Statement>()
        exitStatements.add(freeStmt)
        exitStatements.add(deleteStmt)

        if (returnStmt == null && block == function.code) {
            block.code.addAll(exitStatements)
        } else if (returnStmt != null) {
            val index = block.code.indexOf(returnStmt)
            if (index != -1) {
                block.code.addAll(index, exitStatements)
            }
        }

        val newCode = mutableListOf<Statement>()
        for (statement in block.code) {
            newCode += replacements[statement] ?: listOf(statement)
        }
        block.code.clear()
        block.code.addAll(newCode)
    }


    private fun getCalls(code: CodeBlock): List<Function> {
        val list = mutableListOf<Function>()
        getCalls(code, list)
        return list
    }

    private fun getCalls(code: CodeBlock, list: MutableList<Function>) {
        code.code.forEach { getCalls(it, list) }
    }

    private fun getCalls(statement: Statement, list: MutableList<Function>) {
        when (statement) {
            is TemporaryCallStatement -> {
                if (statement.func !is StandardLibASTFunction && statement.func !is InlineStandardLibFunction) {
                    list.add(statement.func)
                }
                statement.args.forEach { getCalls(it, list) }
            }
            is ExpressionStatement -> getCalls(statement.expression, list)
            is IfStatement -> {
                getCalls(statement.condition, list)
                getCalls(statement.thenBlock, list)
            }
            is IfElseStatement -> {
                getCalls(statement.condition, list)
                getCalls(statement.thenBlock, list)
                getCalls(statement.elseBlock, list)
            }
            is WhileStatement -> {
                getCalls(statement.condition, list)
                getCalls(statement.block, list)
            }
            is RepeatStatement -> {
                getCalls(statement.amount, list)
                getCalls(statement.block, list)
            }
            is LocalVariableAssignmentStatement -> getCalls(statement.assignment, list)
            is TLVariableAssignmentStatement -> getCalls(statement.assignment, list)
            is VariableAssignmentStatement -> {
                getCalls(statement.target, list)
                getCalls(statement.assignment, list)
            }
            is VariableStatement -> statement.defaultValue?.let { getCalls(it, list) }
            is ReturnStatement -> statement.expression?.let { getCalls(it, list) }
            is TemporaryHeapSetStatement -> {
                getCalls(statement.index, list)
                getCalls(statement.data, list)
            }
            is TemporaryScratchStmt -> statement.inputExprs.forEach { getCalls(it, list) }
            is CompositeStatement -> statement.statements.forEach { getCalls(it, list) }
        }
    }

    private fun getCalls(expr: Expression, list: MutableList<Function>) {
        when (expr) {
            is CallExpression -> {
                if (expr.func !is StandardLibASTFunction && expr.func !is InlineStandardLibFunction) {
                    list.add(expr.func)
                }
                expr.arguments.forEach { getCalls(it, list) }
            }
            is BinaryExpression -> { getCalls(expr.left, list); getCalls(expr.right, list) }
            is UnaryExpression -> getCalls(expr.expression, list)
            is ConcatExpression -> { getCalls(expr.left, list); getCalls(expr.right, list) }
            is MemberExpression -> getCalls(expr.expression, list)
            is TemporaryHeapGetExpression -> getCalls(expr.index, list)
            is TemporaryScratchExpr -> expr.inputExprs.forEach { getCalls(it, list) }
            is NonNullAssertExpression -> getCalls(expr.expression, list)
            else -> {}
        }
    }
}