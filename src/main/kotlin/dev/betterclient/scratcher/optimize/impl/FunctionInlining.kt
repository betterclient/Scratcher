package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.optimize.visitCopy
import dev.betterclient.scratcher.translation.ExpressionLowerResult

object FunctionInlining : Optimization("Function inlining") {
    val voidMarkerExpr = TemporaryHeapGetExpression(IntLiteral(0.toBigInteger()))

    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        //val eligible = findEligible(func, graph)

        if (func.name == "compiler@eventlistener@GreenFlagi0") {
            visit(func, object : ASTVisitor() {
                override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                    if (func.name == "a") {
                        val out = inline(func, args)
                        addStatements(out.prepend)
                        return out.expression?: voidMarkerExpr
                    }

                    return super.visitCallExpression(func, args)
                }

                override fun visitExpressionStatement(expression: Expression): Statement? {
                    if (expression == voidMarkerExpr) {
                        return null
                    }

                    return super.visitExpressionStatement(expression)
                }
            })
            return true
        }

        return false
    }

    private fun inline(func: Function, args: List<Expression>): ExpressionLowerResult {
        val prepend = mutableListOf<Statement>()

        //put args in variables (this will be inlined in later optimizations if only used once)
        val argVars = args.associateWith { LocalVariable(getUniqueName(), Type.int) }
        argVars.forEach { (value, variable) ->
            prepend.add(VariableStatement(value, variable))
        }
        val returnVar = LocalVariable(getUniqueName(), Type.int)
        if (func.returnType != Type.void) {
            prepend.add(VariableStatement(null, returnVar))
        }
        val out = visitCopy(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.COPY
            override fun visitParameterExpression(parameter: Parameter): Expression {
                return LocalVariableExpression(argVars.values.toList()[func.parameters.indexOf(parameter)])
            }

            override fun visitReturnStatement(expression: Expression?): Statement? {
                expression?.let {
                    return LocalVariableAssignmentStatement(returnVar, it)
                }
                return null
            }
        })
        visit(out, EarlyReturnRewriter)
        prepend.addAll(out.code)

        return ExpressionLowerResult(
            if (func.returnType == Type.void) null else LocalVariableExpression(returnVar),
            prepend
        )
    }

    private fun findEligible(func: Function, graph: TCallGraph): List<Function> {
        val costs = mutableMapOf<Function, Int>()
        graph[func]!!.forEach { maybe ->
            costs[maybe] = costs.getOrDefault(maybe, 0) + calculateCost(maybe)
        }

        return costs.filter { (_, cost) -> cost < 10000 }.keys.toList()
    }

    private fun calculateCost(func: Function): Int {
        var currentCost = 0

        TODO()

        return currentCost
    }
}

object EarlyReturnRewriter : ASTVisitor() {


    private fun doesBlockGuaranteeReturn(code: List<Statement>): Boolean {
        for (statement in code) {
            if (doesStatementGuaranteeReturn(statement)) {
                return true
            }
        }
        return false
    }

    private fun doesStatementGuaranteeReturn(statement: Statement): Boolean {
        return when (statement) {
            is ReturnStatement -> true
            is IfElseStatement -> {
                doesBlockGuaranteeReturn(statement.thenBlock.code) && doesBlockGuaranteeReturn(statement.elseBlock.code)
            }
            else -> false
        }
    }


}