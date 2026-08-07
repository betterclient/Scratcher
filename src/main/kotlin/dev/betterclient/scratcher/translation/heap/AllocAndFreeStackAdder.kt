package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.MemoryLib

class AllocAndFreeStackAdder(
    val stackParameter: Parameter,
    val func: Function,
    val hasLocalsMap: Map<Function, Boolean>
) : ASTVisitor() {

    fun run() {
        visit(func, this)
    }

    private fun createExitStatements(): List<Statement> {
        if (hasLocalsMap[func] != true) return emptyList()

        val freePtr: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
            BinaryExpression(
                left = ParameterExpression(stackParameter),
                right = IntLiteral(java.math.BigInteger.ONE),
                operator = BinaryOperator.SUBTRACT
            )
        } else {
            ParameterExpression(stackParameter)
        }

        val freeSize: Expression = if (CompilationConstants.MARK_AND_SWEEP_GC) {
            BinaryExpression(
                left = TemporaryStackSizeExpression(func),
                right = IntLiteral(java.math.BigInteger.ONE),
                operator = BinaryOperator.ADD
            )
        } else {
            TemporaryStackSizeExpression(func)
        }

        val freeStmt = TemporaryCallStatement(
            MemoryLib.free,
            args = mutableListOf(freePtr, freeSize)
        )
        val deleteStmt = TemporaryScratchStmt(listOf(ParameterExpression(stackParameter))) { scratchExpressions ->
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                listOf(ListStatements.DeleteItem(
                    GCLib.rootsList,
                    ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])
                ))
            } else emptyList()
        }
        return listOf(deleteStmt, freeStmt)
    }

    override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
        if (func is StandardLibASTFunction) {
            return super.visitTemporaryCallStatement(func, args)
        }

        if (hasLocalsMap[func] != true) {
            return TemporaryCallStatement(func, (listOf(NullExpression) + args).toMutableList())
        } else {
            val result = mutableListOf<Statement>()
            val allocVar = LocalVariable(obfuscate("stackAllocationFor${func.name}Call"), PrimitiveType.Integer)
            result.add(VariableStatement(null, allocVar))
            result.add(TemporaryCallStatement(
                MemoryLib.alloc,
                mutableListOf<Expression>(
                    TemporaryStackSizeExpression(func),
                    TemporaryLocalVariableIndexExpression(allocVar)
                ).also {
                    if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        it.add(1, TemporaryStackNameExpression(func))
                    }
                }
            ))
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                result.add(TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                    listOf(
                        ListStatements.AddToList(GCLib.rootsList, scratchArgs[0])
                    )
                })
            }
            result.add(TemporaryCallStatement(func, (listOf(LocalVariableExpression(allocVar)) + args).toMutableList()))

            addStatements(result)
            return null
        }
    }

    override fun visitReturnStatement(expression: Expression?): Statement {
        val cleanups = createExitStatements()
        return CompositeStatement(cleanups + ReturnStatement(expression))
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val isMainBlock = block === func.code
        super.visitCodeBlock(block)

        if (isMainBlock) {
            val lastStmt = block.code.lastOrNull()
            val endsWithReturn = lastStmt is ReturnStatement ||
                    (lastStmt is CompositeStatement && lastStmt.statements.lastOrNull() is ReturnStatement)

            if (!endsWithReturn) {
                block.code.addAll(createExitStatements())
            }
        }
        return block
    }
}