package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import java.math.BigInteger

object RemoveRedundantHeapStores {
    fun run(functions: List<Function>): Int {
        var removed = 0
        var changed = true
        while (changed) {
            changed = false
            for (func in functions) {
                if (func is StandardLibASTFunction) continue
                val count = removeInFunc(func)
                if (count > 0) {
                    removed += count
                    changed = true
                }
            }
        }
        return removed
    }

    private fun removeInFunc(func: Function): Int {
        val stackParam = func.parameters.firstOrNull() ?: return 0
        val remover = Remover(stackParam)
        visit(func, remover)
        return remover.removed
    }

    private class Remover(val stackParam: Parameter) : ASTVisitor() {
        var removed = 0

        override fun visitCodeBlock(block: CodeBlock): CodeBlock {
            removeInList(block.code)
            return super.visitCodeBlock(block)
        }

        override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
            val mutable = statements.toMutableList()
            removeInList(mutable)
            return StatementExpression(mutable, expression)
        }

        private fun removeInList(code: MutableList<Statement>) {
            var i = 0
            while (i < code.size) {
                val cur = code[i]
                if (cur is TemporaryHeapSetStatement && isSelfCopy(cur)) {
                    code.removeAt(i)
                    removed++
                    continue
                }
                if (i + 1 < code.size) {
                    val nxt = code[i + 1]
                    if (cur is TemporaryHeapSetStatement && nxt is TemporaryHeapSetStatement &&
                        isDisguisedSelfCopy(cur, nxt)
                    ) {
                        code.removeAt(i + 1)
                        removed++
                        continue
                    }
                }
                i++
            }
        }

        private fun indexKey(expr: Expression): Int? = when (expr) {
            is ParameterExpression -> if (expr.parameter == stackParam) 0 else null
            is BinaryExpression -> {
                if (expr.operator != BinaryOperator.ADD) return null
                val base = expr.left as? ParameterExpression ?: return null
                if (base.parameter != stackParam) return null
                val offset = expr.right as? IntLiteral ?: return null
                if (offset.value < BigInteger.ZERO) return null
                offset.value.toInt()
            }
            else -> null
        }

        private fun isSelfCopy(stmt: TemporaryHeapSetStatement): Boolean {
            val dst = indexKey(stmt.index) ?: return false
            val src = (stmt.data as? TemporaryHeapGetExpression) ?: return false
            return indexKey(src.index) == dst
        }

        private fun isDisguisedSelfCopy(
            first: TemporaryHeapSetStatement,
            second: TemporaryHeapSetStatement
        ): Boolean {
            val dst1 = indexKey(first.index) ?: return false
            val src1 = (first.data as? TemporaryHeapGetExpression)?.let { indexKey(it.index) } ?: return false
            val dst2 = indexKey(second.index) ?: return false
            val src2 = (second.data as? TemporaryHeapGetExpression)?.let { indexKey(it.index) } ?: return false
            if (dst1 == src1) return false
            return dst1 == src2 && dst2 == src1
        }
    }
}