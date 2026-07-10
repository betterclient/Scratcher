package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.*

object SequentialConstantPropagation : Optimization("Sequential constant propagation") {

    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        val pass = Pass()
        pass.processCodeBlock(func.code)
        return pass.modified
    }

    private class Pass {
        var modified = false
        val knownValues = mutableMapOf<LocalVariable, Expression>()
        val currentlyVisiting = mutableSetOf<LocalVariable>()

        val expressionVisitor = object : ASTVisitor() {
            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                val known = knownValues[variable]
                if (known != null) {
                    if (currentlyVisiting.add(variable)) {
                        try {
                            modified = true
                            return visit(known)
                        } finally {
                            currentlyVisiting.remove(variable)
                        }
                    }
                }
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
                val processedSubject = subject?.let { processStatement(it) }
                val snapshot = knownValues.toMap()

                val processedBranches = branches.map { branch ->
                    knownValues.clear()
                    knownValues.putAll(snapshot)
                    val cond = propagate(branch.cond)
                    val block = processCodeBlock(branch.block)
                    WhenBranch(cond, block, branch.isElse)
                }

                knownValues.clear()
                knownValues.putAll(snapshot)

                val allModified = branches.flatMap { collectModifiedVariables(it.block) }.toSet()
                invalidateAll(allModified)

                return WhenExpression(processedSubject, processedBranches)
            }
        }

        fun propagate(expr: Expression): Expression = expressionVisitor.visit(expr)

        fun invalidate(variable: LocalVariable) {
            knownValues.remove(variable)
        }

        fun invalidateAll(variables: Set<LocalVariable>) {
            knownValues.keys.removeAll(variables)
        }

        fun processCodeBlock(block: CodeBlock): CodeBlock {
            val newCode = mutableListOf<Statement>()
            for (stmt in block.code) {
                val processed = processStatement(stmt)
                newCode.addAll(flattenStatement(processed))
            }
            block.code.clear()
            block.code.addAll(newCode)
            return block
        }

        fun flattenStatement(statement: Statement?): List<Statement> {
            return when (statement) {
                null -> emptyList()
                is CompositeStatement -> statement.statements.flatMap { flattenStatement(it) }
                else -> listOf(statement)
            }
        }

        fun processStatement(statement: Statement): Statement? {
            return when (statement) {
                is ExpressionStatement -> ExpressionStatement(propagate(statement.expression))

                is VariableStatement -> {
                    val propagatedDefault = statement.defaultValue?.let { propagate(it) }
                    invalidate(statement.variable)

                    if (propagatedDefault != null && propagatedDefault.isConstant()) {
                        knownValues[statement.variable] = propagatedDefault
                    }
                    VariableStatement(propagatedDefault, statement.variable)
                }

                is LocalVariableAssignmentStatement -> {
                    val propagatedAssignment = propagate(statement.assignment)
                    invalidate(statement.variable)

                    if (propagatedAssignment.isConstant()) {
                        knownValues[statement.variable] = propagatedAssignment
                    }
                    LocalVariableAssignmentStatement(statement.variable, propagatedAssignment)
                }

                is IfStatement -> {
                    val cond = propagate(statement.condition)
                    val snapshot = knownValues.toMap()
                    val processedThen = processCodeBlock(statement.thenBlock)

                    knownValues.clear()
                    knownValues.putAll(snapshot)
                    invalidateAll(collectModifiedVariables(statement.thenBlock))

                    IfStatement(cond, processedThen)
                }

                is IfElseStatement -> {
                    val cond = propagate(statement.condition)
                    val snapshot = knownValues.toMap()

                    val processedThen = processCodeBlock(statement.thenBlock)
                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    val processedElse = processCodeBlock(statement.elseBlock)
                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    val modified = collectModifiedVariables(statement.thenBlock) + collectModifiedVariables(statement.elseBlock)
                    invalidateAll(modified)

                    IfElseStatement(cond, processedThen, processedElse)
                }

                is WhileStatement -> {
                    val modified = collectModifiedVariables(statement.block)
                    invalidateAll(modified)

                    val cond = propagate(statement.condition)
                    val snapshot = knownValues.toMap()

                    val processedBlock = processCodeBlock(statement.block)

                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    WhileStatement(cond, processedBlock)
                }

                is RepeatStatement -> {
                    val amt = propagate(statement.amount)

                    val modified = collectModifiedVariables(statement.block)
                    invalidateAll(modified)

                    val snapshot = knownValues.toMap()

                    val processedBlock = processCodeBlock(statement.block)

                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    RepeatStatement(amt, processedBlock)
                }

                is VariableAssignmentStatement -> VariableAssignmentStatement(
                    propagate(statement.target), statement.variable, statement.struct, propagate(statement.assignment)
                )

                is TLVariableAssignmentStatement -> TLVariableAssignmentStatement(
                    statement.variable, statement.sourceAST, propagate(statement.assignment)
                )

                is ReturnStatement -> ReturnStatement(statement.expression?.let { propagate(it) })

                is TemporaryCallStatement -> TemporaryCallStatement(
                    statement.func, statement.args.map { propagate(it) }.toMutableList()
                )

                is TemporaryHeapSetStatement -> TemporaryHeapSetStatement(
                    propagate(statement.index), propagate(statement.data)
                )

                is TemporaryScratchStmt -> TemporaryScratchStmt(
                    statement.inputExprs.map { propagate(it) }, statement.stmt
                )

                is CompositeStatement -> {
                    val stmts = statement.statements.mapNotNull { processStatement(it) }
                    CompositeStatement(stmts)
                }
            }
        }

        private fun collectModifiedVariables(block: CodeBlock): Set<LocalVariable> {
            val modified = mutableSetOf<LocalVariable>()
            val visitor = object : ASTVisitor() {
                override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

                override fun visitLocalVariableAssignmentStatement(
                    variable: LocalVariable,
                    assignment: Expression
                ): Statement? {
                    modified.add(variable)
                    return super.visitLocalVariableAssignmentStatement(variable, assignment)
                }

                override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                    modified.add(variable)
                    return super.visitVariableStatement(defaultValue, variable)
                }
            }
            visitor.visitCodeBlock(block)
            return modified
        }

        private fun Expression.isConstant(): Boolean {
            return this is Literal
        }
    }
}