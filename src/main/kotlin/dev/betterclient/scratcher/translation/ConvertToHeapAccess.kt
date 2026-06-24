package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.ConcatExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NewStructExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.TemporaryHeapSetStatement
import dev.betterclient.scratcher.ast.TemporaryLocalVariableIndexExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.codegen.rand
import dev.betterclient.scratcher.std.MemoryLibrary
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class ConvertToHeapAccess(
    val functions: List<Function>
) {
    private val temporaryExpression = mutableMapOf<Function, TemporaryHeapGetExpression>() //used as a marker

    fun run(): Map<Function, Int> {
        println("Add stack parameter")
        val stacks = functions.filter { it !is StandardLibASTFunction }.map { func ->
            Parameter("stack", Type.int).also { func.parameters.add(0, it) } to func
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
            .associateWith { countLocals(it.code) }

        println("Convert to heap")
        for (function in newFuncs.keys) {
            convert(function.code, function) { newFuncs[it]!! }
        }

        return newFuncs.mapValues { it.value.size }
    }

    private fun convert(
        block: CodeBlock,
        currentFunction: Function,
        getFunctionLocals: (Function) -> List<LocalVariable>,
    ) {
        block.code.map { convertStatement(it, currentFunction, getFunctionLocals) }.reduceOrNull { a, b -> a + b }?.let {
            block.code.clear()
            block.code.addAll(it)
        }
    }

    private fun convertStatement(
        statement: Statement,
        currentFunction: Function,
        getFunctionLocals: (Function) -> List<LocalVariable>
    ): List<Statement> {
        val curFunc = getFunctionLocals(currentFunction)
        val stackPar = currentFunction.parameters.first()
        return when (statement) {
            is ExpressionStatement -> throw UnsupportedOperationException("unreachable")
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
                            right = IntLiteral(index),
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
                listOf(VariableAssignmentStatement(
                    variable = statement.variable,
                    struct = statement.struct,
                    assignment = convertExpression(statement.assignment, currentFunction, getFunctionLocals)
                ))
            }
            is VariableStatement -> {
                listOf(TemporaryHeapSetStatement(
                    index = BinaryExpression(
                        left = ParameterExpression(stackPar),
                        right = IntLiteral(curFunc.indexOf(statement.variable)),
                        operator = BinaryOperator.ADD,
                    ),
                    data = convertExpression(statement.defaultValue, currentFunction, getFunctionLocals)
                ))
            }
            is WhileStatement -> {
                listOf(WhileStatement(
                    condition = convertExpression(statement.condition, currentFunction, getFunctionLocals),
                    block = statement.block.also { convert(it, currentFunction, getFunctionLocals) }
                ))
            }
        }
    }

    private fun convertExpression(
        expression: Expression,
        currentFunction: Function,
        getFunctionLocals: (Function) -> List<LocalVariable>
    ): Expression {
        if (temporaryExpression.containsValue(expression)) {
            val matchedFunction = temporaryExpression.entries.firstOrNull { it.value === expression }?.key
            if (matchedFunction != null) {
                return IntLiteral(getFunctionLocals(matchedFunction).size)
            }
        }

        val curFunc = getFunctionLocals(currentFunction)
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
                            right = IntLiteral(index),
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
                        right = IntLiteral(index),
                        operator = BinaryOperator.ADD,
                    )
                }
            }
            is UnaryExpression -> UnaryExpression(
                operator = expression.operator,
                expression = convertExpression(expression.expression, currentFunction, getFunctionLocals)
            )
            is VariableExpression -> expression
            is TemporaryHeapGetExpression -> expression
            is ParameterExpression -> expression
            is BooleanLiteral -> expression
            is FloatLiteral -> expression
            is IntLiteral -> expression
            is StringLiteral -> expression
            is MemberExpression -> TODO("heap...")
            is NewStructExpression -> TODO("heap...")
            is CallExpression -> throw UnsupportedOperationException("unreachable")
        }
    }

    private fun countLocals(code: CodeBlock): List<LocalVariable> {
        val vars = mutableListOf<LocalVariable>()
        for (statement in code.code) {
            when (statement) {
                is ExpressionStatement -> throw UnsupportedOperationException("unreachable")
                is IfElseStatement -> {
                    vars += countLocals(statement.thenBlock)
                    vars += countLocals(statement.elseBlock)
                }
                is IfStatement -> {
                    vars += countLocals(statement.thenBlock)
                }
                is RepeatStatement -> {
                    vars += countLocals(statement.block)
                }
                is WhileStatement -> {
                    vars += countLocals(statement.block)
                }
                is VariableStatement -> {
                    vars.add(statement.variable)
                }
                is LocalVariableAssignmentStatement -> {}
                is ReturnStatement -> {}
                is TLVariableAssignmentStatement -> {}
                is TemporaryCallStatement -> {}
                is TemporaryHeapSetStatement -> {}
                is VariableAssignmentStatement -> {}
            }
        }
        return vars.toSet().toList() //little trick to remove duplicates
    }

    fun getTemporary(function: Function) = temporaryExpression.computeIfAbsent(function) { TemporaryHeapGetExpression(IntLiteral(0)) }

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
                is ExpressionStatement -> throw UnsupportedOperationException("unreachable")
                is LocalVariableAssignmentStatement -> {}
                is TLVariableAssignmentStatement -> {}
                is TemporaryCallStatement -> {
                    if (statement.func is StandardLibASTFunction) continue
                    val allocVar = LocalVariable(rand(), Type.int)
                    replacements[statement] = listOf(
                        VariableStatement(IntLiteral(-1), allocVar),
                        TemporaryCallStatement(
                            MemoryLibrary.alloc,
                            mutableListOf(getTemporary(statement.func), TemporaryLocalVariableIndexExpression(allocVar))
                        ),
                        statement.copy(args = (listOf(LocalVariableExpression(allocVar)) + statement.args).toMutableList())
                    )
                }
                is TemporaryHeapSetStatement -> {}
                is VariableAssignmentStatement -> {}
                is VariableStatement -> {}
            }
        }

        val freeStmt = TemporaryCallStatement(
            MemoryLibrary.free,
            args = mutableListOf(ParameterExpression(par), getTemporary(function))
        )
        if (returnStmt == null && block == function.code) {
            block.code.add(
                freeStmt
            )
        } else if (returnStmt != null) {
            val index = block.code.indexOf(returnStmt)
            if (index != -1) {
                block.code.add(index, freeStmt)
            }
        }

        val newCode = mutableListOf<Statement>()
        for (statement in block.code) {
            newCode += replacements[statement] ?: listOf(statement)
        }
        block.code.clear()
        block.code.addAll(newCode)
    }
}