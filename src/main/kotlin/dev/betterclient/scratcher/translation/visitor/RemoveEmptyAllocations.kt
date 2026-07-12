package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.MemoryLib
import java.math.BigInteger

class RemoveEmptyAllocations(val function: Function, val functionLocalCount: Int) : ASTVisitor() {
    private val removedAllocVars = mutableSetOf<LocalVariable>()
    private val stackPar = function.parameters.firstOrNull()

    fun run() {
        visit(function, object : ASTVisitor() {
            override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
                if (func == MemoryLib.alloc) {
                    val firstArg = args.getOrNull(0) as? IntLiteral
                    if (firstArg?.value == BigInteger.ZERO) {
                        val allocVarExpr = args.getOrNull(2) as? TemporaryLocalVariableIndexExpression
                        if (allocVarExpr != null) {
                            removedAllocVars.add(allocVarExpr.variable)
                        }
                    }
                }
                return super.visitTemporaryCallStatement(func, args)
            }
        })

        visit(function, this)
    }

    override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
        if (isAlloc0(func, args) || isFree0(func, args)) {
            return null
        }
        return super.visitTemporaryCallStatement(func, args)
    }

    override fun visitTemporaryScratchStmt(
        inputExprs: List<Expression>,
        stmt: (List<ScratchExpression>) -> List<ScratchStatement>
    ): Statement? {
        if (isRemovedScratch(inputExprs)) {
            return null
        }
        return super.visitTemporaryScratchStmt(inputExprs, stmt)
    }

    private fun isAlloc0(func: Function, args: List<Expression>): Boolean {
        if (func != MemoryLib.alloc) return false
        val firstArg = args.getOrNull(0) as? IntLiteral ?: return false
        return firstArg.value == BigInteger.ZERO
    }

    private fun isFree0(func: Function, args: List<Expression>): Boolean {
        if (func != MemoryLib.free || functionLocalCount != 0) return false
        val firstArg = args.getOrNull(0) as? ParameterExpression ?: return false
        return firstArg.parameter == stackPar
    }

    private fun isRemovedScratch(inputExprs: List<Expression>): Boolean {
        val firstInput = inputExprs.getOrNull(0) ?: return false

        val isRemovedAllocScratch = firstInput is TemporaryLocalVariableIndexExpression &&
                firstInput.variable in removedAllocVars

        val isRemovedFreeScratch = functionLocalCount == 0 &&
                firstInput is ParameterExpression &&
                firstInput.parameter == stackPar

        return isRemovedAllocScratch || isRemovedFreeScratch
    }
}