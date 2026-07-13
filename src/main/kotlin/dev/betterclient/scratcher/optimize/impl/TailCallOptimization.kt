package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.OptimizationUtils
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object TailCallOptimization : Optimization("Tail call optimization") {
    override fun shouldApply(func: Function, callGraph: TCallGraph): Boolean {
        return OptimizationUtils.isOnlyDirectlyRecursive(func, callGraph)
    }
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        val tailCalls = findTailCalls(func)
        if (countSelfCalls(func) != tailCalls.size) return false

        val tcoActiveVar = LocalVariable(obfuscate("TCO@active"), PrimitiveType.Bool)
        val declarations = mutableListOf<Statement>()
        declarations.add(VariableStatement(BooleanLiteral(true), tcoActiveVar))

        val args = func.parameters.associateWith { LocalVariable(obfuscate("TCO@argument@${it.name}"), it.type) }
        args.forEach { (param, variable) -> declarations.add(VariableStatement(ParameterExpression(param), variable)) }

        visit(func, object : ASTVisitor() {
            override fun visitCodeBlock(block: CodeBlock): CodeBlock {
                if (func.code == block) return rewriteToLoop(block)

                val processedBlock = super.visitCodeBlock(block)
                val guardedStatements = processedBlock.code.map { stmt ->
                    IfStatement(
                        LocalVariableExpression(tcoActiveVar),
                        CodeBlock().apply { code.add(stmt) }
                    )
                }
                processedBlock.code.clear()
                processedBlock.code.addAll(guardedStatements)
                return processedBlock
            }

            private fun rewriteToLoop(block: CodeBlock): CodeBlock {
                val out = super.visitCodeBlock(block)

                val guardedBody = out.code.map { stmt ->
                    IfStatement(
                        LocalVariableExpression(tcoActiveVar),
                        CodeBlock().apply { code.add(stmt) }
                    )
                }

                val loopBody = CodeBlock()
                loopBody.code.add(LocalVariableAssignmentStatement(tcoActiveVar, BooleanLiteral(true)))
                loopBody.code.addAll(guardedBody)

                val loop = WhileStatement(BooleanLiteral(true), loopBody)

                val outBlock = CodeBlock()
                outBlock.code.addAll(declarations)
                outBlock.code.add(loop)

                return outBlock
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                declarations.add(VariableStatement(null, variable))
                return if (defaultValue != null) LocalVariableAssignmentStatement(variable, defaultValue) else null
            }

            override fun visitReturnStatement(expression: Expression?): Statement {
                if (expression is CallExpression && expression.func == func) {
                    val statements = mutableListOf<Statement>()

                    if (func.parameters.size == 1) {
                        val param = func.parameters[0]
                        val shadowVar = args[param]!!
                        statements.add(LocalVariableAssignmentStatement(shadowVar, expression.arguments[0]))
                    } else {
                        val tempVars = expression.arguments.mapIndexed { i, arg ->
                            val param = func.parameters[i]
                            val tempVar = LocalVariable(obfuscate("TCOtemp@${param.name}"), param.type)
                            declarations.add(VariableStatement(null, tempVar))
                            statements.add(LocalVariableAssignmentStatement(tempVar, arg))
                            tempVar
                        }
                        expression.arguments.forEachIndexed { i, _ ->
                            val shadowVar = args[func.parameters[i]]!!
                            statements.add(
                                LocalVariableAssignmentStatement(
                                    shadowVar,
                                    LocalVariableExpression(tempVars[i])
                                )
                            )
                        }
                    }

                    statements.add(LocalVariableAssignmentStatement(tcoActiveVar, BooleanLiteral(false)))
                    return CompositeStatement(statements)
                }

                return ReturnStatement(expression)
            }

            override fun visitParameterExpression(parameter: Parameter): Expression {
                return LocalVariableExpression(args[parameter]!!)
            }
        })

        return true
    }

    private fun findTailCalls(func: Function): List<CallExpression> {
        val tailCalls = mutableListOf<CallExpression>()
        analyzeBlock(func.code, atTail = true, targetFunc = func, tailCalls = tailCalls)
        return tailCalls
    }

    private fun analyzeBlock(
        block: CodeBlock,
        atTail: Boolean,
        targetFunc: Function,
        tailCalls: MutableList<CallExpression>
    ) {
        val lastIndex = block.code.lastIndex
        block.code.forEachIndexed { i, stmt ->
            val stmtAtTail = atTail && (i == lastIndex)

            when (stmt) {
                is ReturnStatement -> {
                    val expr = stmt.expression
                    if (expr is CallExpression && expr.func == targetFunc) tailCalls.add(expr)
                }
                is ExpressionStatement -> {
                    val expr = stmt.expression
                    if (stmtAtTail && expr is CallExpression && expr.func == targetFunc) tailCalls.add(expr)
                }
                is IfStatement -> analyzeBlock(stmt.thenBlock, stmtAtTail, targetFunc, tailCalls)
                is IfElseStatement -> {
                    analyzeBlock(stmt.thenBlock, stmtAtTail, targetFunc, tailCalls)
                    analyzeBlock(stmt.elseBlock, stmtAtTail, targetFunc, tailCalls)
                }
                is WhileStatement -> analyzeBlock(stmt.block, false, targetFunc, tailCalls)
                else -> {}
            }
        }
    }

    private fun countSelfCalls(target: Function): Int {
        var calls = 0
        visit(target, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                if (target == func) calls++

                return super.visitCallExpression(func, args)
            }
        })
        return calls
    }
}