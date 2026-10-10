package dev.betterclient.scratcher.sugar.loop

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.LambdaExpression
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StatementExpression
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhenBranch
import dev.betterclient.scratcher.ast.WhenExpression
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar
import org.antlr.v4.kotlinruntime.ast.Position
import java.util.Stack

object ContinueAndBreakDesugaring : CompilerSugar() {
    val loopState = Stack<LoopState>()

    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        visit(func, object : ASTVisitor() {
            override fun visitStatement(statement: Statement) {
                if (statement is WhileStatement) {
                    //before
                    val state = LoopState()
                    loopState.add(state)
                }
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock, position: Position?): Statement? {
                //after
                val myState = loopState.pop()

                if (myState.hasSkipVar) {
                    addStatements(listOf(
                        VariableStatement(BooleanLiteral(false, null), myState.doneVar, null),
                        VariableStatement(BooleanLiteral(true, null), myState.skipVar, null),
                    ))

                    val wrappedCode = wrap(block.code, myState.skipVar)
                    block.code.clear()
                    block.code.addAll(wrappedCode)

                    block.code.add(0, LocalVariableAssignmentStatement(myState.skipVar, BooleanLiteral(false, null), null))

                    return super.visitWhileStatement(
                        condition = BinaryExpression(
                            left = condition,
                            operator = BinaryOperator.AND,
                            right = UnaryExpression(
                                operator = UnaryOperator.NOT,
                                expression = LocalVariableExpression(myState.doneVar, null), null
                            ), null
                        ),
                        block = block, null
                    )
                }

                //no continue or break, keep normal loop
                return super.visitWhileStatement(condition, block, null)
            }

            override fun visitBreakStatement(position: Position?): Statement {
                val state = loopState.last()
                state.hasSkipVar = true
                return CompositeStatement(listOf(
                    LocalVariableAssignmentStatement(state.doneVar, BooleanLiteral(true, null), null),
                    LocalVariableAssignmentStatement(state.skipVar, BooleanLiteral(true, null), null)
                ), null)
            }

            override fun visitContinueStatement(position: Position?): Statement {
                val state = loopState.last()
                state.hasSkipVar = true
                return LocalVariableAssignmentStatement(
                    state.skipVar,
                    BooleanLiteral(true, null), null
                )
            }

            override fun visitLambdaExpression(
                block: CodeBlock,
                arguments: List<LocalVariable>,
                captured: MutableSet<LocalVariable>,
                position: Position?
            ): Expression {
                val enclosingStates = loopState.toList()
                loopState.clear()
                return try {
                    LambdaExpression(arguments, visitCodeBlock(block), captured, null)
                } finally {
                    loopState.addAll(enclosingStates)
                }
            }
        })
    }

    data class LoopState(
        val skipVar: LocalVariable = LocalVariable("continue_skip_${getUniqueName()}", PrimitiveType.Bool),
        val doneVar: LocalVariable = LocalVariable("continue_done_${getUniqueName()}", PrimitiveType.Bool),
        var hasSkipVar: Boolean = false
    )

    private fun wrap(statements: List<Statement>, skipVar: LocalVariable): List<Statement> {
        val rewrittenStatements = mutableListOf<Statement>()
        var hasPossibleSkipBefore = false
        val currentDeferred = mutableListOf<Statement>()

        for (stmt in statements) {
            val processedStmt = when (stmt) {
                is ExpressionStatement -> ExpressionStatement(processExpression(stmt.expression, skipVar), null)
                is IfStatement -> IfStatement(
                    stmt.condition,
                    CodeBlock(wrap(stmt.thenBlock.code, skipVar).toMutableList()), null
                )
                is IfElseStatement -> IfElseStatement(
                    stmt.condition,
                    CodeBlock(wrap(stmt.thenBlock.code, skipVar).toMutableList()),
                    CodeBlock(wrap(stmt.elseBlock.code, skipVar).toMutableList()), null
                )
                is CompositeStatement -> CompositeStatement(
                    wrap(stmt.statements, skipVar), null
                )
                else -> stmt
            }

            if (hasPossibleSkipBefore) {
                currentDeferred.add(processedStmt)
            } else {
                rewrittenStatements.add(processedStmt)
                if (containsSkipVarSet(processedStmt, skipVar)) {
                    hasPossibleSkipBefore = true
                }
            }
        }

        if (currentDeferred.isNotEmpty()) {
            val processedDeferred = wrap(currentDeferred, skipVar)
            val innerBlock = CodeBlock(processedDeferred.toMutableList())
            val condition = UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(skipVar, null), null)
            rewrittenStatements.add(IfStatement(condition, innerBlock, null))
        }

        return rewrittenStatements
    }

    private fun processExpression(expression: Expression, skipVar: LocalVariable): Expression {
        return when (expression) {
            is WhenExpression -> WhenExpression(
                expression.subject,
                expression.branches.map { branch ->
                    WhenBranch(
                        branch.cond,
                        CodeBlock(wrap(branch.block.code, skipVar).toMutableList()),
                        branch.isElse, null
                    )
                }, null
            )
            is StatementExpression -> StatementExpression(
                wrap(expression.statements, skipVar),
                processExpression(expression.expression, skipVar), null
            )
            else -> expression
        }
    }

    private fun containsSkipVarSet(statement: Statement, skipVar: LocalVariable): Boolean {
        return when (statement) {
            is LocalVariableAssignmentStatement -> statement.variable == skipVar
            is VariableStatement -> statement.variable == skipVar
            is ExpressionStatement -> containsSkipVarSet(statement.expression, skipVar)
            is IfStatement -> containsSkipVarSet(statement.thenBlock, skipVar)
            is IfElseStatement -> containsSkipVarSet(statement.thenBlock, skipVar) || containsSkipVarSet(statement.elseBlock, skipVar)
            is CompositeStatement -> statement.statements.any { containsSkipVarSet(it, skipVar) }
            else -> false
        }
    }

    private fun containsSkipVarSet(block: CodeBlock, skipVar: LocalVariable): Boolean {
        return block.code.any { containsSkipVarSet(it, skipVar) }
    }

    private fun containsSkipVarSet(expression: Expression, skipVar: LocalVariable): Boolean {
        return when (expression) {
            is WhenExpression -> expression.branches.any { containsSkipVarSet(it.block, skipVar) }
            is StatementExpression -> expression.statements.any { containsSkipVarSet(it, skipVar) }
                || containsSkipVarSet(expression.expression, skipVar)
            else -> false
        }
    }
}