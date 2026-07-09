package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BooleanLiteral
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
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhenBranch
import dev.betterclient.scratcher.ast.WhenExpression
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object WhenToIf : Optimization("When to if") {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        var modified = false

        visit(func, object : ASTVisitor() {
            override fun visitWhenExpr(branches: List<WhenBranch>, subject: VariableStatement?): Expression {
                modified = true

                val whenExpr = WhenExpression(subject, branches)
                val returnType = ExpressionTypes.getExpressionType(context, whenExpr)

                val tempVar = if (returnType != Type.void) {
                    val tv = LocalVariable("whenResult@${getUniqueName()}", returnType)
                    currentBlock?.localVariables?.add(tv)
                    tv
                } else null

                if (subject != null) {
                    currentBlock?.localVariables?.add(subject.variable)
                    addStatements(listOf(subject))
                }

                if (tempVar != null) {
                    addStatements(listOf(VariableStatement(null, tempVar)))
                }

                val ifChain = buildIfChain(branches, 0, tempVar)
                if (ifChain != null) {
                    addStatements(listOf(ifChain))
                }

                return if (tempVar != null) {
                    LocalVariableExpression(tempVar)
                } else {
                    NullExpression
                }
            }

            private fun convertBranchBlock(originalBlock: CodeBlock, tempVar: LocalVariable?): CodeBlock {
                val newBlock = CodeBlock()
                newBlock.localVariables.addAll(originalBlock.localVariables)

                val processedBlock = visitCodeBlock(originalBlock)
                newBlock.code.addAll(processedBlock.code)

                if (tempVar != null && newBlock.code.isNotEmpty()) {
                    val lastIndex = newBlock.code.lastIndex
                    val lastStmt = newBlock.code[lastIndex]
                    if (lastStmt is ExpressionStatement) {
                        newBlock.code[lastIndex] = LocalVariableAssignmentStatement(tempVar, lastStmt.expression)
                    }
                }
                return newBlock
            }

            private fun buildIfChain(branches: List<WhenBranch>, index: Int, tempVar: LocalVariable?): Statement? {
                if (index >= branches.size) return null

                val branch = branches[index]
                val cond = visit(branch.cond)
                val branchBlock = convertBranchBlock(branch.block, tempVar)

                if (index == branches.size - 1 && cond is BooleanLiteral && cond.value) {
                    return CompositeStatement(branchBlock.code)
                }

                val nextStmt = buildIfChain(branches, index + 1, tempVar)
                return if (nextStmt != null) {
                    val elseBlock = CodeBlock()
                    elseBlock.code.add(nextStmt)
                    IfElseStatement(cond, branchBlock, elseBlock)
                } else {
                    IfStatement(cond, branchBlock)
                }
            }
        })

        return modified
    }
}