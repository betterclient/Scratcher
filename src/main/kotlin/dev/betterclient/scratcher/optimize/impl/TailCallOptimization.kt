package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.*

object TailCallOptimization : Optimization("Tail call optimization") {
    override fun shouldApply(func: Function, callGraph: TCallGraph): Boolean {
        return OptimizationUtils.isOnlyDirectlyRecursive(func, callGraph)
    }
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        val tailCalls = findTailCalls(func)
        if (countSelfCalls(func) != tailCalls.size) return false
        //apply time!

        val hasReturnedVar = LocalVariable(obfuscate("TCO@hasReturned"), Type.bool)
        val tcoActiveVar = LocalVariable(obfuscate("TCO@active"), Type.bool)
        val resultVar = if (func.returnType != Type.void) {
            LocalVariable(obfuscate("TCO@result"), func.returnType)
        } else null
        val declarations = mutableListOf<Statement>()
        declarations.add(VariableStatement(BooleanLiteral(false), hasReturnedVar))
        declarations.add(VariableStatement(BooleanLiteral(true), tcoActiveVar))
        if (resultVar != null) declarations.add(VariableStatement(IntLiteral((-1).toBigInteger()), resultVar))

        val args = func.parameters.associateWith { LocalVariable(obfuscate("TCO@argument@${it.name}"), it.type) }
        args.forEach { (param, variable) -> declarations.add(VariableStatement(ParameterExpression(param), variable)) }

        visit(func, object : ASTVisitor() {
            override fun visitCodeBlock(block: CodeBlock): CodeBlock {
                if (func.code == block) {
                    return rewriteToLoop(block)
                }

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
                val out = super.visitCodeBlock(block) //weeewooo

                val guardedBody = out.code.map { stmt ->
                    IfStatement(
                        LocalVariableExpression(tcoActiveVar),
                        CodeBlock().apply { code.add(stmt) }
                    )
                }

                val loopBody = CodeBlock()
                loopBody.code.add(LocalVariableAssignmentStatement(tcoActiveVar, BooleanLiteral(true)))
                loopBody.code.addAll(guardedBody)
                loopBody.code.add(
                    IfStatement(
                        LocalVariableExpression(tcoActiveVar),
                        CodeBlock().apply {
                            code.add(LocalVariableAssignmentStatement(hasReturnedVar, BooleanLiteral(true)))
                        }
                    )
                )

                val loopCondition = UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(hasReturnedVar))
                val loop = WhileStatement(loopCondition, loopBody)

                val outBlock = CodeBlock()
                outBlock.code.addAll(declarations)
                outBlock.code.add(loop)

                if (resultVar != null) {
                    outBlock.code.add(ReturnStatement(LocalVariableExpression(resultVar)))
                } else {
                    outBlock.code.add(ReturnStatement(null))
                }

                return outBlock
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                //move all decls up to the top
                declarations.add(VariableStatement(null, variable))

                if (defaultValue != null) {
                    return LocalVariableAssignmentStatement(variable, defaultValue)
                }

                return null
            }

            override fun visitReturnStatement(expression: Expression?): Statement? {
                val statements = mutableListOf<Statement>()

                if (expression is CallExpression && expression.func == func) {
                    val tempVars = mutableListOf<LocalVariable>()

                    expression.arguments.forEachIndexed { i, arg ->
                        val param = func.parameters[i]
                        val tempVar = LocalVariable(obfuscate("TCOtemp@${param.name}"), param.type)
                        declarations.add(VariableStatement(null, tempVar))

                        statements.add(LocalVariableAssignmentStatement(tempVar, arg))
                        tempVars.add(tempVar)
                    }

                    expression.arguments.forEachIndexed { i, _ ->
                        val param = func.parameters[i]
                        val shadowVar = args[param]!!
                        val tempVar = tempVars[i]
                        statements.add(LocalVariableAssignmentStatement(shadowVar, LocalVariableExpression(tempVar)))
                    }

                    statements.add(LocalVariableAssignmentStatement(tcoActiveVar, BooleanLiteral(false)))

                    return CompositeStatement(statements)
                } else {
                    if (expression != null && resultVar != null) {
                        statements.add(LocalVariableAssignmentStatement(resultVar, expression))
                    }
                    statements.add(LocalVariableAssignmentStatement(hasReturnedVar, BooleanLiteral(true)))
                    statements.add(LocalVariableAssignmentStatement(tcoActiveVar, BooleanLiteral(false)))

                    return CompositeStatement(statements)
                }
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