package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapSetStatement
import dev.betterclient.scratcher.ast.TemporaryLocalVariableIndexExpression
import dev.betterclient.scratcher.ast.TemporaryScratchStmt
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.std.lib.MemoryLib
import java.math.BigInteger

class RemoveEmptyAllocations(val function: Function, val functionLocalCount: Int) {
    private val removedAllocVars = mutableSetOf<LocalVariable>()
    private val stackPar = function.parameters.firstOrNull()

    fun run() = run(function.code)

    fun run(code: CodeBlock) {
        for (statement in code.code) {
            when (statement) {
                is WhileStatement -> run(statement.block)
                is IfElseStatement -> {
                    run(statement.thenBlock)
                    run(statement.elseBlock)
                }
                is IfStatement -> run(statement.thenBlock)
                is RepeatStatement -> run(statement.block)

                is VariableStatement, is LocalVariableAssignmentStatement, is ExpressionStatement ->
                    throw UnreachableException()

                is ReturnStatement, is TLVariableAssignmentStatement, is TemporaryCallStatement,
                is TemporaryHeapSetStatement, is VariableAssignmentStatement, is TemporaryScratchStmt,
                is CompositeStatement -> {}
            }
        }

        for (statement in code.code) {
            if (statement is TemporaryCallStatement && statement.isAlloc0()) {
                val allocVarExpr = statement.args.getOrNull(2) as? TemporaryLocalVariableIndexExpression
                if (allocVarExpr != null) {
                    removedAllocVars.add(allocVarExpr.variable)
                }
            }
        }

        code.code.removeAll { statement ->
            when (statement) {
                is TemporaryCallStatement -> statement.isAlloc0() || statement.isFree0()
                is TemporaryScratchStmt -> statement.isRemovedScratch()
                else -> false
            }
        }
    }

    private fun TemporaryCallStatement.isAlloc0(): Boolean {
        if (func != MemoryLib.alloc) return false
        val firstArg = args.getOrNull(0) as? IntLiteral ?: return false
        return firstArg.value == BigInteger.ZERO
    }

    private fun TemporaryCallStatement.isFree0(): Boolean {
        if (func != MemoryLib.free || functionLocalCount != 0) return false
        val firstArg = args.getOrNull(0) as? ParameterExpression ?: return false
        return firstArg.parameter == stackPar
    }

    private fun TemporaryScratchStmt.isRemovedScratch(): Boolean {
        val firstInput = inputExprs.getOrNull(0) ?: return false

        val isRemovedAllocScratch = firstInput is TemporaryLocalVariableIndexExpression &&
                firstInput.variable in removedAllocVars

        val isRemovedFreeScratch = functionLocalCount == 0 &&
                firstInput is ParameterExpression &&
                firstInput.parameter == stackPar

        return isRemovedAllocScratch || isRemovedFreeScratch
    }
}