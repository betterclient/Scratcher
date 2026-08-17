package dev.betterclient.scratcher.sugar.`when`

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
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StatementExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhenBranch
import dev.betterclient.scratcher.ast.WhenExpression
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar

object WhenDesugaring : CompilerSugar() {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ) {
        visit(func, object : ASTVisitor() {
            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
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
                            val last = branchStatements.lastOrNull()
                            if (last is ExpressionStatement && last.expression === value) {
                                branchStatements.removeAt(branchStatements.lastIndex)
                            }
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