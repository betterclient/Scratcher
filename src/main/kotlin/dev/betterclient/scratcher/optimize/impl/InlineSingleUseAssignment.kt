package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.BaseExpressionVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object InlineSingleUseAssignment : Optimization("Inline single-use assignments") {
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        val analysis = SSAAnalysis()
        analysis.analyze(func)

        val propVars = analysis.allVars.filter {
            !it.isInvalid && it.readCount == 1 && (it.isConstant || it.definitionExpr.isSimple())
        }
        if (propVars.isEmpty()) return false
        val inlinedValues = propVars.associate { it.variable to it.definitionExpr }
        val inliningStack = mutableSetOf<LocalVariable>()

        visit(func, object : ASTVisitor() {
            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                val inlinedExpr = inlinedValues[variable]
                if (inlinedExpr != null && inliningStack.add(variable)) {
                    val result = visit(inlinedExpr)
                    inliningStack.remove(variable)
                    return result
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (inlinedValues.containsKey(variable)) {
                    return null //remove definition if unused
                }

                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                if (inlinedValues.containsKey(variable)) {
                    return null //remove assignments
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
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

class SSAAnalysis {
    val activeVars = mutableMapOf<LocalVariable, SSAVar>()
    val allVars = mutableListOf<SSAVar>()
    val writeCounts = mutableMapOf<LocalVariable, Int>()

    private val exprVisitor = object : BaseExpressionVisitor {
        override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
            activeVars[variable]?.let {
                it.readCount++
            }

            return super.visitLocalVariableExpression(variable)
        }
    }

    fun analyze(function: Function) {
        analyzeBlock(function.code)

        allVars.forEach { def ->
            if ((writeCounts[def.variable] ?: 0) > 1) {
                def.isInvalid = true
            }
        }
    }

    private fun analyzeBlock(code: CodeBlock) {
        code.code.forEach { analyzeStatement(it) }
    }

    private fun analyzeStatement(stmt: Statement) {
        when(stmt) {
            is VariableStatement -> {
                writeCounts[stmt.variable] = (writeCounts[stmt.variable] ?: 0) + 1
                stmt.defaultValue?.let { exprVisitor.visit(it) }

                if (stmt.defaultValue != null) {
                    val def = SSAVar(stmt.variable, stmt.defaultValue, stmt)
                    allVars.add(def)
                    removeDependents(setOf(stmt.variable))
                    activeVars[stmt.variable] = def

                    if (stmt.defaultValue.dependsOn(setOf(stmt.variable))) {
                        def.isInvalid = true
                    }
                }
            }
            is LocalVariableAssignmentStatement -> {
                writeCounts[stmt.variable] = (writeCounts[stmt.variable] ?: 0) + 1
                exprVisitor.visit(stmt.assignment)

                val def = SSAVar(stmt.variable, stmt.assignment, stmt)
                allVars.add(def)
                removeDependents(setOf(stmt.variable))
                activeVars[stmt.variable] = def

                if (stmt.assignment.dependsOn(setOf(stmt.variable))) {
                    def.isInvalid = true
                }
            }
            is IfStatement -> {
                exprVisitor.visit(stmt.condition)

                val modified = collectModifiedVariables(stmt)
                removeDependents(modified)

                analyzeBlock(stmt.thenBlock)

                modified.forEach { activeVars.remove(it) }
            }
            is IfElseStatement -> {
                exprVisitor.visit(stmt.condition)

                val modified = collectModifiedVariables(stmt)
                removeDependents(modified)

                analyzeBlock(stmt.thenBlock)
                analyzeBlock(stmt.elseBlock)

                modified.forEach { activeVars.remove(it) }
            }
            is WhileStatement -> {
                exprVisitor.visit(stmt.condition)

                val modified = collectModifiedVariables(stmt)
                removeDependents(modified)

                analyzeBlock(stmt.block)

                modified.forEach { activeVars.remove(it) }
            }
            is RepeatStatement -> {
                exprVisitor.visit(stmt.amount)

                val modified = collectModifiedVariables(stmt)
                removeDependents(modified)

                analyzeBlock(stmt.block)

                modified.forEach { activeVars.remove(it) }
            }
            is CompositeStatement -> {
                stmt.statements.forEach { analyzeStatement(it) }
            }
            is ExpressionStatement -> exprVisitor.visit(stmt.expression)
            is ReturnStatement -> stmt.expression?.let { exprVisitor.visit(it) }
            is TLVariableAssignmentStatement -> exprVisitor.visit(stmt.assignment)
            is VariableAssignmentStatement -> {
                exprVisitor.visit(stmt.target)
                exprVisitor.visit(stmt.assignment)
            }
            is TemporaryCallStatement -> stmt.args.forEach { exprVisitor.visit(it) }
            is TemporaryHeapSetStatement -> {
                exprVisitor.visit(stmt.index)
                exprVisitor.visit(stmt.data)
            }
            is TemporaryScratchStmt -> stmt.inputExprs.forEach { exprVisitor.visit(it) }
        }
    }

    private fun removeDependents(modified: Set<LocalVariable>) {
        activeVars.values.forEach { def ->
            if (def.definitionExpr.dependsOn(modified)) {
                def.isInvalid = true
            }
        }
    }

    private fun Expression.dependsOn(variables: Set<LocalVariable>): Boolean {
        return when (this) {
            is LocalVariableExpression -> variables.contains(this.variable)
            is BinaryExpression -> left.dependsOn(variables) || right.dependsOn(variables)
            is UnaryExpression -> expression.dependsOn(variables)
            is ConcatExpression -> left.dependsOn(variables) || right.dependsOn(variables)
            is MemberExpression -> expression.dependsOn(variables)
            is TemporaryHeapGetExpression -> index.dependsOn(variables)
            is TemporaryScratchExpr -> inputExprs.any { it.dependsOn(variables) }
            else -> false
        }
    }

    private fun collectModifiedVariables(statement: Statement): Set<LocalVariable> {
        val modified = mutableSetOf<LocalVariable>()
        fun visit(stmt: Statement) {
            when (stmt) {
                is LocalVariableAssignmentStatement -> modified.add(stmt.variable)
                is VariableStatement -> modified.add(stmt.variable)
                is IfStatement -> stmt.thenBlock.code.forEach { visit(it) }
                is IfElseStatement -> {
                    stmt.thenBlock.code.forEach { visit(it) }
                    stmt.elseBlock.code.forEach { visit(it) }
                }
                is WhileStatement -> stmt.block.code.forEach { visit(it) }
                is RepeatStatement -> stmt.block.code.forEach { visit(it) }
                is CompositeStatement -> stmt.statements.forEach { visit(it) }
                else -> {}
            }
        }
        visit(statement)
        return modified
    }
}

class SSAVar(
    val variable: LocalVariable,
    val definitionExpr: Expression,
    val definingStatement: Statement,
    var readCount: Int = 0,
    var isInvalid: Boolean = false
) {
    val isConstant: Boolean
        get() = definitionExpr is Literal || definitionExpr == NullExpression
}