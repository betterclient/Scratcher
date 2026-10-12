package dev.betterclient.scratcher.optimize.impl.variable

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.*
import org.antlr.v4.kotlinruntime.ast.Position

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
            override fun visitLocalVariableExpression(variable: LocalVariable, position: Position?): Expression {
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
                return super.visitLocalVariableExpression(variable, position)
            }

            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?, position: Position?): Expression {
                val processedSubject = subject?.let { processStatement(it) }
                val snapshot = knownValues.toMap()

                val processedBranches = branches.map { branch ->
                    knownValues.clear()
                    knownValues.putAll(snapshot)
                    val cond = propagate(branch.cond)
                    val block = processCodeBlock(branch.block)
                    WhenBranch(cond, block, branch.isElse, branch.position)
                }

                knownValues.clear()
                knownValues.putAll(snapshot)

                val allModified = branches.flatMap { collectModifiedVariables(it.block) }.toSet()
                invalidateAll(allModified)

                return WhenExpression(processedSubject, processedBranches, position)
            }

            override fun visitCallExpression(func: Function, args: List<Expression>, position: Position?): Expression {
                val visitedArgs = args.map { propagate(it) }
                knownValues.clear()
                return CallExpression(func, visitedArgs, position)
            }

            override fun visitDynamicCallExpression(
                function: Expression,
                args: List<Expression>,
                type: FunctionType,
                position: Position?
            ): Expression {
                val fn = propagate(function)
                val visitedArgs = args.map { propagate(it) }
                knownValues.clear()
                return DynamicCallExpression(type, fn, visitedArgs, position)
            }

            override fun visitLambdaExpression(
                block: CodeBlock,
                arguments: List<LocalVariable>,
                captured: MutableSet<LocalVariable>,
                position: Position?
            ): Expression {
                val lambdaPass = Pass()
                val processedBlock = lambdaPass.processCodeBlock(block)
                if (lambdaPass.modified) {
                    modified = true
                }

                val modifiedInside = collectModifiedVariables(block)
                invalidateAll(modifiedInside)

                return LambdaExpression(arguments, processedBlock, captured, position)
            }
        }

        fun propagate(expr: Expression): Expression {
            if (expr is StatementExpression) {
                return StatementExpression(expr.statements.map { processStatement(it) }, propagate(expr.expression), expr.position)
            }
            return expressionVisitor.visit(expr)
        }

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

        fun processStatement(statement: Statement): Statement {
            return when (statement) {
                is ExpressionStatement -> {
                    val expr = propagate(statement.expression)
                    if (hasCallsOrLambdas(expr)) {
                        knownValues.clear()
                    }
                    ExpressionStatement(expr, statement.position)
                }

                is VariableStatement -> {
                    val propagatedDefault = statement.defaultValue?.let { propagate(it) }
                    invalidate(statement.variable)

                    if (propagatedDefault != null && propagatedDefault.isConstant()) {
                        knownValues[statement.variable] = propagatedDefault
                    }
                    VariableStatement(propagatedDefault, statement.variable, statement.position)
                }

                is LocalVariableAssignmentStatement -> {
                    val propagatedAssignment = propagate(statement.assignment)
                    invalidate(statement.variable)

                    if (propagatedAssignment.isConstant()) {
                        knownValues[statement.variable] = propagatedAssignment
                    }
                    LocalVariableAssignmentStatement(statement.variable, propagatedAssignment, statement.position)
                }

                is IfStatement -> {
                    val cond = propagate(statement.condition)
                    val snapshot = knownValues.toMap()
                    val processedThen = processCodeBlock(statement.thenBlock)

                    knownValues.clear()
                    knownValues.putAll(snapshot)
                    invalidateAll(collectModifiedVariables(statement.thenBlock))

                    IfStatement(cond, processedThen, statement.position)
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

                    IfElseStatement(cond, processedThen, processedElse, statement.position)
                }

                is WhileStatement -> {
                    val modified = collectModifiedVariables(statement.block)
                    invalidateAll(modified)

                    val cond = propagate(statement.condition)
                    val snapshot = knownValues.toMap()

                    val processedBlock = processCodeBlock(statement.block)

                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    WhileStatement(cond, processedBlock, statement.position)
                }

                is RepeatStatement -> {
                    val amt = propagate(statement.amount)

                    val modified = collectModifiedVariables(statement.block)
                    invalidateAll(modified)

                    val snapshot = knownValues.toMap()

                    val processedBlock = processCodeBlock(statement.block)

                    knownValues.clear()
                    knownValues.putAll(snapshot)

                    RepeatStatement(amt, processedBlock, statement.position)
                }

                is VariableAssignmentStatement -> VariableAssignmentStatement(
                    propagate(statement.target), statement.variable, statement.struct, propagate(statement.assignment), statement.position
                )

                is TLVariableAssignmentStatement -> TLVariableAssignmentStatement(
                    statement.variable, statement.sourceAST, propagate(statement.assignment), statement.position
                )

                is ReturnStatement -> ReturnStatement(statement.expression?.let { propagate(it) }, statement.position)

                is TemporaryCallStatement -> TemporaryCallStatement(
                    statement.func, statement.args.map { propagate(it) }.toMutableList(), statement.position
                )

                is TemporaryHeapSetStatement -> TemporaryHeapSetStatement(
                    propagate(statement.index), propagate(statement.data), statement.position
                )

                is TemporaryScratchStmt -> TemporaryScratchStmt(
                    statement.inputExprs.map { propagate(it) }, statement.stmt, statement.position
                )

                is CompositeStatement -> {
                    val stmts = statement.statements.map { processStatement(it) }
                    CompositeStatement(stmts, statement.position)
                }

                is StaticListSetStatement -> StaticListSetStatement(
                    statement.list, propagate(statement.index), propagate(statement.value), statement.position
                )
                is StaticListAddStatement -> StaticListAddStatement(
                    statement.list, propagate(statement.item), statement.position
                )
                is StaticListInsertStatement -> StaticListInsertStatement(
                    statement.list, propagate(statement.index), propagate(statement.value), statement.position
                )
                is StaticListRemoveStatement -> StaticListRemoveStatement(
                    statement.list, propagate(statement.index), statement.position
                )

                is BreakStatement -> BreakStatement(statement.position)
                is ContinueStatement -> ContinueStatement(statement.position)
                is StaticListClearStatement -> StaticListClearStatement(statement.list, statement.position)
            }
        }

        private fun collectModifiedVariables(block: CodeBlock): Set<LocalVariable> {
            val modified = mutableSetOf<LocalVariable>()
            val visitor = object : ASTVisitor() {
                override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

                override fun visitLocalVariableAssignmentStatement(
                    variable: LocalVariable,
                    assignment: Expression,
                    position: Position?
                ): Statement? {
                    modified.add(variable)
                    return super.visitLocalVariableAssignmentStatement(variable, assignment, position)
                }

                override fun visitVariableStatement(
                    defaultValue: Expression?,
                    variable: LocalVariable,
                    position: Position?
                ): Statement? {
                    modified.add(variable)
                    return super.visitVariableStatement(defaultValue, variable, position)
                }
            }
            visitor.visitCodeBlock(block)
            return modified
        }

        private fun Expression.isConstant(): Boolean {
            return this is Literal
        }
    }

    private fun hasCallsOrLambdas(expr: Expression): Boolean {
        return when (expr) {
            is CallExpression, is DynamicCallExpression, is LambdaExpression -> true
            is BinaryExpression -> hasCallsOrLambdas(expr.left) || hasCallsOrLambdas(expr.right)
            is UnaryExpression -> hasCallsOrLambdas(expr.expression)
            is ConcatExpression -> hasCallsOrLambdas(expr.left) || hasCallsOrLambdas(expr.right)
            is MemberExpression -> hasCallsOrLambdas(expr.expression)
            is SafeDotExpression -> hasCallsOrLambdas(expr.target)
            is NonNullAssertExpression -> hasCallsOrLambdas(expr.expression)
            is NonNullOrElseExpression -> hasCallsOrLambdas(expr.operand1) || hasCallsOrLambdas(expr.operand2)
            is StatementExpression -> expr.statements.any { hasCallsOrLambdas(it) } || hasCallsOrLambdas(expr.expression)
            else -> false
        }
    }

    private fun hasCallsOrLambdas(block: CodeBlock): Boolean {
        return block.code.any { hasCallsOrLambdas(it) }
    }

    private fun hasCallsOrLambdas(stmt: Statement): Boolean {
        return when (stmt) {
            is ExpressionStatement -> hasCallsOrLambdas(stmt.expression)
            is VariableStatement -> stmt.defaultValue?.let { hasCallsOrLambdas(it) } ?: false
            is LocalVariableAssignmentStatement -> hasCallsOrLambdas(stmt.assignment)
            is VariableAssignmentStatement -> hasCallsOrLambdas(stmt.target) || hasCallsOrLambdas(stmt.assignment)
            is TLVariableAssignmentStatement -> hasCallsOrLambdas(stmt.assignment)
            is ReturnStatement -> stmt.expression?.let { hasCallsOrLambdas(it) } ?: false
            is IfStatement -> hasCallsOrLambdas(stmt.condition) || hasCallsOrLambdas(stmt.thenBlock)
            is IfElseStatement -> hasCallsOrLambdas(stmt.condition) || hasCallsOrLambdas(stmt.thenBlock) || hasCallsOrLambdas(stmt.elseBlock)
            is WhileStatement -> hasCallsOrLambdas(stmt.condition) || hasCallsOrLambdas(stmt.block)
            is RepeatStatement -> hasCallsOrLambdas(stmt.amount) || hasCallsOrLambdas(stmt.block)
            is TemporaryCallStatement -> stmt.args.any { hasCallsOrLambdas(it) }
            is TemporaryHeapSetStatement -> hasCallsOrLambdas(stmt.index) || hasCallsOrLambdas(stmt.data)
            is TemporaryScratchStmt -> stmt.inputExprs.any { hasCallsOrLambdas(it) }
            is CompositeStatement -> stmt.statements.any { hasCallsOrLambdas(it) }
            is StaticListSetStatement -> hasCallsOrLambdas(stmt.index) || hasCallsOrLambdas(stmt.value)
            is StaticListAddStatement -> hasCallsOrLambdas(stmt.item)
            is StaticListInsertStatement -> hasCallsOrLambdas(stmt.index) || hasCallsOrLambdas(stmt.value)
            is StaticListRemoveStatement -> hasCallsOrLambdas(stmt.index)

            is BreakStatement -> false
            is ContinueStatement -> false
            is StaticListClearStatement -> false
        }
    }
}