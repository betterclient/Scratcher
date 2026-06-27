package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object InlineSingleUseVariables : Optimization("Inline single use variables") {
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        val usages = analyzeVariables(func)
        val propagatable = usages.filter { (_, usage) ->
            val def = usage.definition
            if (usage.writeCount != 1 || def == null) return@filter false

            //just inline the damn constant
            if (usage.isConstant) return@filter true

            usage.readCount == 1 && def.isSimple()
        }.mapValues { it.value.definition!! }

        if (propagatable.isEmpty()) return false

        visit(func, object : ASTVisitor() {
            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                propagatable[variable]?.let {
                    return visit(it)
                }
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (propagatable.contains(variable)) return null
                return super.visitVariableStatement(defaultValue, variable)
            }
        })

        return true
    }

    private fun Expression.isSimple(): Boolean {
        return when (this) {
            is CallExpression -> false
            is NonNullAssertExpression -> false
            is BinaryExpression -> left.isSimple() && right.isSimple()
            is ConcatExpression -> left.isSimple() && right.isSimple()
            is UnaryExpression -> expression.isSimple()
            is MemberExpression -> expression.isSimple()
            is TemporaryHeapGetExpression -> index.isSimple()
            is TemporaryScratchExpr -> inputExprs.all { it.isSimple() }
            else -> true
        }
    }
}

class VarUsage(
    var writeCount: Int = 0,
    var readCount: Int = 0,
    var definition: Expression? = null
) {
    val isConstant: Boolean
        get() = definition is Literal || definition == NullExpression
}

private fun analyzeVariables(function: Function): Map<LocalVariable, VarUsage> {
    val usageMap = mutableMapOf<LocalVariable, VarUsage>()

    visit(function, object : ASTVisitor() {
        override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
            return VisitMode.READ_ONLY
        }

        override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
            val usage = usageMap.getOrPut(variable) { VarUsage() }
            usage.definition = defaultValue
            usage.writeCount++
            return super.visitVariableStatement(defaultValue, variable)
        }

        override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
            val usage = usageMap.getOrPut(variable) { VarUsage() }
            usage.writeCount++
            return super.visitLocalVariableAssignmentStatement(variable, assignment)
        }

        override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
            val usage = usageMap.getOrPut(variable) { VarUsage() }
            usage.readCount++
            return super.visitLocalVariableExpression(variable)
        }
    })

    return usageMap
}