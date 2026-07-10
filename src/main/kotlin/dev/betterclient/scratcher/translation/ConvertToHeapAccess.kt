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
            is ExpressionStatement -> throw UnreachableException()
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
            is CompositeStatement -> statement.statements.flatMap { convertStatement(it, currentFunction, getFunctionLocals) }
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

            is CallExpression, is NonNullAssertExpression, is EnumLiteral, is WhenExpression -> throw UnreachableException()
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

        fun countFromStatement(statement: Statement) {
            when (statement) {
                is ExpressionStatement -> throw UnreachableException()
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
                is CompositeStatement -> {
                    statement.statements.forEach { countFromStatement(it) }
                }
                else -> {}
            }
        }

        code.code.forEach { countFromStatement(it) }
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

        fun processStatements(statements: List<Statement>): List<Statement> {
            val result = mutableListOf<Statement>()
            for (statement in statements) {
                when (statement) {
                    is IfElseStatement -> {
                        addAllocAndFreeStack(function, statement.thenBlock, par)
                        addAllocAndFreeStack(function, statement.elseBlock, par)
                        result.add(statement)
                    }
                    is IfStatement -> {
                        addAllocAndFreeStack(function, statement.thenBlock, par)
                        result.add(statement)
                    }
                    is RepeatStatement -> {
                        addAllocAndFreeStack(function, statement.block, par)
                        result.add(statement)
                    }
                    is WhileStatement -> {
                        addAllocAndFreeStack(function, statement.block, par)
                        result.add(statement)
                    }
                    is ReturnStatement -> {
                        returnStmt = statement
                        result.add(statement)
                        break
                    }
                    is ExpressionStatement -> throw UnreachableException()
                    is TemporaryCallStatement -> {
                        if (statement.func is StandardLibASTFunction) {
                            result.add(statement)
                            continue
                        }

                        if (hasLocalsMap[statement.func] != true) {
                            result.add(statement.copy(args = (listOf(NullExpression) + statement.args).toMutableList()))
                        } else {
                            val allocVar = LocalVariable(obfuscate("stackAllocationFor${statement.func.name}Call"), Type.int)
                            result.add(VariableStatement(null, allocVar))
                            result.add(TemporaryCallStatement(
                                MemoryLib.alloc,
                                mutableListOf(getTemporary(statement.func), getNameTemporary(statement.func), TemporaryLocalVariableIndexExpression(allocVar))
                            ))
                            result.add(TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                                listOf(
                                    ListStatements.AddToList(GCLib.rootsList, scratchArgs[0])
                                )
                            })
                            result.add(statement.copy(args = (listOf(LocalVariableExpression(allocVar)) + statement.args).toMutableList()))
                        }
                    }
                    is CompositeStatement -> {
                        val processed = processStatements(statement.statements)
                        result.add(CompositeStatement(processed))
                    }
                    else -> {
                        result.add(statement)
                    }
                }
            }
            return result
        }

        val processedCode = processStatements(block.code)
        block.code.clear()
        block.code.addAll(processedCode)

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
            }
            is IfStatement -> getCalls(statement.thenBlock, list)
            is IfElseStatement -> {
                getCalls(statement.thenBlock, list)
                getCalls(statement.elseBlock, list)
            }
            is WhileStatement -> getCalls(statement.block, list)
            is RepeatStatement -> getCalls(statement.block, list)
            is CompositeStatement -> {
                statement.statements.forEach { getCalls(it, list) }
            }
            else -> {}
        }
    }
}