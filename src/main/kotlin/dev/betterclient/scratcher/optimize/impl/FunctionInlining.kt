package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.OptimizationUtils
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.optimize.visitCopy
import dev.betterclient.scratcher.translation.ExpressionLowerResult
import java.math.BigInteger

object FunctionInlining : Optimization("Function inlining") {
    val voidMarkerExpr = TemporaryHeapGetExpression(IntLiteral(0.toBigInteger()))

    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        val eligible = InlineEligibility.findEligible(func, graph)

        var modified = false
        val inlinedExprs = mutableListOf<Expression>()
        visit(func, object : ASTVisitor() {
            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                if (eligible.contains(func)) {
                    modified = true
                    val out = inline(func, args)
                    addStatements(out.prepend)
                    return (out.expression?.also {
                        inlinedExprs.add(it)
                    })?: voidMarkerExpr
                }

                return super.visitCallExpression(func, args)
            }

            override fun visitExpressionStatement(expression: Expression): Statement? {
                if (expression == voidMarkerExpr) {
                    return null
                }
                if (inlinedExprs.contains(expression)) {
                    return null //ignoring returns, huh? I see how it is
                }

                return super.visitExpressionStatement(expression)
            }
        })
        return modified
    }

    private fun inline(func: Function, args: List<Expression>): ExpressionLowerResult {
        val prepend = mutableListOf<Statement>()

        //put args in variables (this will be inlined in later optimizations if only used once)
        val argVars = func.parameters.associateWith { LocalVariable(getUniqueName(), Type.int) }
        args.forEachIndexed { index, arg ->
            prepend.add(VariableStatement(arg, argVars.values.toList()[index]))
        }
        val returnVar = LocalVariable(getUniqueName(), Type.int)
        if (func.returnType != Type.void) {
            prepend.add(VariableStatement(null, returnVar))
        }
        val hasReturned = LocalVariable(getUniqueName(), Type.bool)
        prepend.add(VariableStatement(BooleanLiteral(false), hasReturned))

        //returns are something...
        val out = visitCopy(func, EarlyReturnRewriter(hasReturned))
        visit(out, object : ASTVisitor() {
            override fun visitParameterExpression(parameter: Parameter): Expression {
                return LocalVariableExpression(argVars[parameter]!!)
            }

            override fun visitReturnStatement(expression: Expression?): Statement? {
                expression?.let {
                    return LocalVariableAssignmentStatement(returnVar, it)
                }
                return null
            }
        })
        prepend.addAll(out.code)

        return ExpressionLowerResult(
            if (func.returnType == Type.void) null else LocalVariableExpression(returnVar),
            prepend
        )
    }
}

object InlineEligibility {
    fun findEligible(targetFunc: Function, graph: TCallGraph): List<Function> {
        val costs = mutableMapOf<Function, BigInteger>() //need big integer to not overflow when we hit a recursive function and try to inline it!
        visit(targetFunc, object : ASTVisitor() {
            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                costs[func] = (costs[func]?: 0.toBigInteger()) + calculateCost(targetFunc, func, graph).toBigInteger()

                return super.visitCallExpression(func, args)
            }
        })

        return costs.filter { (_, cost) -> cost <= 10000.toBigInteger() }.keys.toList().filter { it !is StandardLibASTFunction && it !is InlineStandardLibFunction }
    }

    private fun calculateCost(func: Function, targetFunc: Function, graph: TCallGraph): Int {
        var currentCost = 0
        if (OptimizationUtils.isRecursive(targetFunc, graph)) return Int.MAX_VALUE
        if (func.warp != targetFunc.warp) return Int.MAX_VALUE //not happening...

        visit(targetFunc, object : ASTVisitor() {
            override fun visitReturnStatement(expression: Expression?): Statement? {
                currentCost += 500
                return super.visitReturnStatement(expression)
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                currentCost += 100
                return super.visitWhileStatement(condition, block)
            }

            override fun visitIfStatement(condition: Expression, thenBlock: CodeBlock): Statement? {
                currentCost += 50
                return super.visitIfStatement(condition, thenBlock)
            }

            override fun visitIfElseStatement(
                condition: Expression,
                thenBlock: CodeBlock,
                elseBlock: CodeBlock
            ): Statement? {
                currentCost += 100
                return super.visitIfElseStatement(condition, thenBlock, elseBlock)
            }

            override fun visitExpr(expression: Expression) {
                currentCost += 20
            }

            override fun visitStatement(statement: Statement) {
                currentCost += 50
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                currentCost += 100
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                currentCost += when (func) {
                    is InlineStandardLibFunction -> 100
                    is StandardLibASTFunction -> 20
                    else -> 20
                }

                return super.visitCallExpression(func, args)
            }
        })

        return currentCost
    }
}

class EarlyReturnRewriter(
    val hasReturned: LocalVariable
) : ASTVisitor() {

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
        return VisitMode.COPY
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val mode = shouldVisitCodeBlock(block)
        if (mode != VisitMode.COPY) {
            return super.visitCodeBlock(block)
        }

        val visitedBlock = super.visitCodeBlock(block)

        val processed = sequentiallyWrap(visitedBlock.code)
        visitedBlock.code.clear()
        visitedBlock.code.addAll(processed)

        return visitedBlock
    }

    private fun sequentiallyWrap(statements: List<Statement>): List<Statement> {
        val rewrittenStatements = mutableListOf<Statement>()
        var hasPossibleReturnBefore = false
        val currentDeferred = mutableListOf<Statement>()

        for (stmt in statements) {
            if (hasPossibleReturnBefore) {
                currentDeferred.add(stmt)
            } else {
                rewrittenStatements.add(stmt)
                if (containsHasReturnedSet(stmt)) {
                    hasPossibleReturnBefore = true
                }
            }
        }

        if (currentDeferred.isNotEmpty()) {
            val processedDeferred = sequentiallyWrap(currentDeferred)
            val innerBlock = CodeBlock(processedDeferred.toMutableList())
            val condition = UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(hasReturned))
            rewrittenStatements.add(IfStatement(condition, innerBlock))
        }

        return rewrittenStatements
    }

    override fun visitReturnStatement(expression: Expression?): Statement {
        val list = mutableListOf<Statement>()
        list.add(ReturnStatement(expression?.let { visit(it) }))
        list.add(LocalVariableAssignmentStatement(hasReturned, BooleanLiteral(true)))
        return CompositeStatement(list)
    }

    override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement {
        val visited = super.visitWhileStatement(condition, block) as WhileStatement
        if (containsHasReturnedSet(visited.block)) {
            val loopCondition = BinaryExpression(
                visited.condition,
                BinaryOperator.AND,
                UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(hasReturned))
            )
            return WhileStatement(loopCondition, visited.block)
        }
        return visited
    }

    private fun containsHasReturnedSet(statement: Statement): Boolean {
        return when (statement) {
            is LocalVariableAssignmentStatement -> statement.variable == hasReturned
            is VariableStatement -> statement.variable == hasReturned
            is IfStatement -> containsHasReturnedSet(statement.thenBlock)
            is IfElseStatement -> containsHasReturnedSet(statement.thenBlock) || containsHasReturnedSet(statement.elseBlock)
            is WhileStatement -> containsHasReturnedSet(statement.block)
            is CompositeStatement -> statement.statements.any { containsHasReturnedSet(it) }
            else -> false
        }
    }

    private fun containsHasReturnedSet(block: CodeBlock): Boolean {
        return block.code.any { containsHasReturnedSet(it) }
    }
}