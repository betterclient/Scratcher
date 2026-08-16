package dev.betterclient.scratcher.optimize.impl.control

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object WhenToStatementExpression : Optimization("When to Statement expression") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        var modified = false

        visit(func, object : ASTVisitor() {
            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
                modified = true

                val prepend = mutableListOf<Statement>()

                val tempWhenExpr = WhenExpression(subject, branches)
                val returnType = ExpressionTypes.getExpressionType(context, tempWhenExpr)
                val tempVar = if (returnType != PrimitiveType.Void) {
                    LocalVariable("whenResult@${getUniqueName()}", returnType)
                } else null

                val loweredBranches = branches.map { branch ->
                    val branchStatements = mutableListOf<Statement>()
                    branchStatements.addAll(branch.block.code)

                    if (tempVar != null && branchStatements.isNotEmpty()) {
                        val value = ExpressionTypes.getWhenBranchValue(context, branch)
                        if (value != null) {
                            branchStatements.add(LocalVariableAssignmentStatement(tempVar, value))
                        }
                    }

                    WhenBranch(
                        cond = branch.cond,
                        isElse = branch.isElse,
                        block = CodeBlock().also { it.code.addAll(branchStatements) }
                    )
                }

                if (subject != null) {
                    prepend.add(subject)
                }

                if (tempVar != null) {
                    prepend.add(VariableStatement(null, tempVar))
                }

                buildIfChain(loweredBranches)?.let {
                    prepend.add(it)
                }

                return StatementExpression(
                    expression = if (tempVar != null) LocalVariableExpression(tempVar) else NullExpression,
                    statements = prepend
                )
            }
        })

        return modified
    }

    private fun buildIfChain(branches: List<WhenBranch>): Statement? {
        if (branches.isEmpty()) return null

        if (branches.size == 1 && branches[0].isElse) {
            val code = branches[0].block.code
            return if (code.size == 1) code[0] else CompositeStatement(code)
        }

        val lastBranch = branches.last()
        val preceding: List<WhenBranch>
        val initial: Statement

        if (lastBranch.isElse) {
            val rest = branches.dropLast(1)
            val penUlti = rest.last()

            preceding = rest.dropLast(1)
            initial = IfElseStatement(penUlti.cond, penUlti.block, lastBranch.block)
        } else {
            preceding = branches.dropLast(1)
            initial = IfStatement(lastBranch.cond, lastBranch.block)
        }

        return preceding.foldRight(initial) { branch, accum ->
            val elseBlock = CodeBlock().apply { code.add(accum) }
            IfElseStatement(branch.cond, branch.block, elseBlock)
        }
    }
}