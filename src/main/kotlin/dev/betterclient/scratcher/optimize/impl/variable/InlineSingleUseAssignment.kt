package dev.betterclient.scratcher.optimize.impl.variable

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object InlineSingleUseAssignment : Optimization("Inline single-use assignments") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        val analysis = InlineSingleUseAnalysis()
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
            is WhenExpression -> false
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

class InlineSingleUseAnalysis {
    val activeVars = mutableMapOf<LocalVariable, SSAVar>()
    val allVars = mutableListOf<SSAVar>()
    val writeCounts = mutableMapOf<LocalVariable, Int>()
    private val invalidated = mutableSetOf<LocalVariable>()
    private var loopDepth = 0

    private val exprVisitor = object : ASTVisitor() {
        override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
            val def = activeVars[variable]
            if (def != null) {
                def.readCount++
            } else if (loopDepth > 0 && writeCounts.containsKey(variable)) {
                invalidated.add(variable)
            }

            return super.visitLocalVariableExpression(variable)
        }
    }

    fun analyze(function: Function) {
        prescanWrites(function)
        analyzeBlock(function.code)

        allVars.forEach { def ->
            if (def.variable in invalidated) {
                def.isInvalid = true
            }
            if ((writeCounts[def.variable] ?: 0) > 1) {
                def.isInvalid = true
            }
        }
    }

    private fun prescanWrites(function: Function) {
        visit(function, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (defaultValue != null) {
                    writeCounts[variable] = (writeCounts[variable] ?: 0) + 1
                }
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                writeCounts[variable] = (writeCounts[variable] ?: 0) + 1
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }
        })
    }

    private fun analyzeBlock(code: CodeBlock) {
        code.code.forEach { analyzeStatement(it) }
    }

    private fun analyzeStatement(stmt: Statement) {
        when(stmt) {
            is VariableStatement -> {
                stmt.defaultValue?.let { defaultValue ->
                    exprVisitor.visit(defaultValue)

                    val def = SSAVar(stmt.variable, defaultValue)
                    allVars.add(def)
                    removeDependents(setOf(stmt.variable))
                    activeVars[stmt.variable] = def

                    if (defaultValue.dependsOn(setOf(stmt.variable))) {
                        def.isInvalid = true
                    }
                }
            }
            is LocalVariableAssignmentStatement -> {
                exprVisitor.visit(stmt.assignment)

                val def = SSAVar(stmt.variable, stmt.assignment)
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

                loopDepth++
                analyzeBlock(stmt.block)
                loopDepth--

                modified.forEach { activeVars.remove(it) }
            }
            is RepeatStatement -> {
                exprVisitor.visit(stmt.amount)

                val modified = collectModifiedVariables(stmt)
                removeDependents(modified)

                loopDepth++
                analyzeBlock(stmt.block)
                loopDepth--

                modified.forEach { activeVars.remove(it) }
            }
            is CompositeStatement -> {
                stmt.statements.forEach { analyzeStatement(it) }
            }
            is ExpressionStatement -> exprVisitor.visit(stmt.expression)
            is ReturnStatement -> stmt.expression?.let { exprVisitor.visit(it) }
            is TLVariableAssignmentStatement -> {
                invalidateGlobalDependents(stmt.variable)
                exprVisitor.visit(stmt.assignment)
            }
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

    private fun invalidateGlobalDependents(modified: TLVariable) {
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
            is WhenExpression -> {
                val subjectDepends = subject?.let { stmtDependsOn(it, variables) } ?: false
                subjectDepends || branches.any { branch ->
                    branch.cond.dependsOn(variables) || blockDependsOn(branch.block, variables)
                }
            }
            else -> false
        }
    }

    private fun Expression.dependsOn(variable: TLVariable): Boolean {
        return when (this) {
            is VariableExpression -> this.variable == variable
            is BinaryExpression -> left.dependsOn(variable) || right.dependsOn(variable)
            is UnaryExpression -> expression.dependsOn(variable)
            is ConcatExpression -> left.dependsOn(variable) || right.dependsOn(variable)
            is MemberExpression -> expression.dependsOn(variable)
            is CallExpression -> arguments.any { it.dependsOn(variable) }
            is NonNullAssertExpression -> expression.dependsOn(variable)
            is TemporaryHeapGetExpression -> index.dependsOn(variable)
            is TemporaryScratchExpr -> inputExprs.any { it.dependsOn(variable) }
            else -> false
        }
    }

    private fun blockDependsOn(block: CodeBlock, variables: Set<LocalVariable>): Boolean {
        return block.code.any { stmtDependsOn(it, variables) }
    }

    private fun stmtDependsOn(stmt: Statement, variables: Set<LocalVariable>): Boolean {
        return when (stmt) {
            is ExpressionStatement -> stmt.expression.dependsOn(variables)
            is VariableStatement -> stmt.defaultValue?.dependsOn(variables) ?: false
            is LocalVariableAssignmentStatement -> stmt.assignment.dependsOn(variables)
            is VariableAssignmentStatement -> stmt.target.dependsOn(variables) || stmt.assignment.dependsOn(variables)
            is TLVariableAssignmentStatement -> stmt.assignment.dependsOn(variables)
            is ReturnStatement -> stmt.expression?.dependsOn(variables) ?: false
            is IfStatement -> stmt.condition.dependsOn(variables) || blockDependsOn(stmt.thenBlock, variables)
            is IfElseStatement -> stmt.condition.dependsOn(variables) || blockDependsOn(stmt.thenBlock, variables) || blockDependsOn(stmt.elseBlock, variables)
            is WhileStatement -> stmt.condition.dependsOn(variables) || blockDependsOn(stmt.block, variables)
            is RepeatStatement -> stmt.amount.dependsOn(variables) || blockDependsOn(stmt.block, variables)
            is TemporaryCallStatement -> stmt.args.any { it.dependsOn(variables) }
            is TemporaryHeapSetStatement -> stmt.index.dependsOn(variables) || stmt.data.dependsOn(variables)
            is TemporaryScratchStmt -> stmt.inputExprs.any { it.dependsOn(variables) }
            is CompositeStatement -> stmt.statements.any { stmtDependsOn(it, variables) }
        }
    }

    private fun collectModifiedVariables(statement: Statement): Set<LocalVariable> {
        val modified = mutableSetOf<LocalVariable>()

        visitStmt(modified, statement)
        return modified
    }

    private fun visitStmt(modified: MutableSet<LocalVariable>, stmt: Statement) {
        when (stmt) {
            is VariableStatement -> {
                modified.add(stmt.variable)
                stmt.defaultValue?.let { visitExpr(modified, it) }
            }
            is LocalVariableAssignmentStatement -> {
                modified.add(stmt.variable)
                visitExpr(modified, stmt.assignment)
            }
            is IfStatement -> {
                visitExpr(modified, stmt.condition)
                stmt.thenBlock.code.forEach { visitStmt(modified, it) }
            }
            is IfElseStatement -> {
                visitExpr(modified, stmt.condition)
                stmt.thenBlock.code.forEach { visitStmt(modified, it) }
                stmt.elseBlock.code.forEach { visitStmt(modified, it) }
            }
            is WhileStatement -> {
                visitExpr(modified, stmt.condition)
                stmt.block.code.forEach { visitStmt(modified, it) }
            }
            is RepeatStatement -> {
                visitExpr(modified, stmt.amount)
                stmt.block.code.forEach { visitStmt(modified, it) }
            }
            is ExpressionStatement -> visitExpr(modified, stmt.expression)
            is TLVariableAssignmentStatement -> visitExpr(modified, stmt.assignment)
            is VariableAssignmentStatement -> {
                visitExpr(modified, stmt.target)
                visitExpr(modified, stmt.assignment)
            }
            is TemporaryCallStatement -> stmt.args.forEach { visitExpr(modified, it) }
            is TemporaryHeapSetStatement -> {
                visitExpr(modified, stmt.index)
                visitExpr(modified, stmt.data)
            }
            is TemporaryScratchStmt -> stmt.inputExprs.forEach { visitExpr(modified, it) }
            is CompositeStatement -> stmt.statements.forEach { visitStmt(modified, it) }
            is ReturnStatement -> stmt.expression?.let { visitExpr(modified, it) }
        }
    }

    private fun visitExpr(modified: MutableSet<LocalVariable>, expr: Expression) {
        when (expr) {
            is WhenExpression -> {
                expr.subject?.let { visitStmt(modified, it) }
                expr.branches.forEach { branch ->
                    visitExpr(modified, branch.cond)
                    branch.block.code.forEach { visitStmt(modified, it) }
                }
            }

            is BinaryExpression -> {
                visitExpr(modified, expr.left)
                visitExpr(modified, expr.right)
            }

            is UnaryExpression -> visitExpr(modified, expr.expression)
            is ConcatExpression -> {
                visitExpr(modified, expr.left)
                visitExpr(modified, expr.right)
            }

            is MemberExpression -> visitExpr(modified, expr.expression)
            is CallExpression -> expr.arguments.forEach { visitExpr(modified, it) }
            is NonNullAssertExpression -> visitExpr(modified, expr.expression)
            is TemporaryHeapGetExpression -> visitExpr(modified, expr.index)
            is TemporaryScratchExpr -> expr.inputExprs.forEach { visitExpr(modified, it) }
            else -> {}
        }
    }
}

class SSAVar(
    val variable: LocalVariable,
    val definitionExpr: Expression,
    var readCount: Int = 0,
    var isInvalid: Boolean = false
) {
    val isConstant: Boolean
        get() = definitionExpr is Literal || definitionExpr == NullExpression
}
