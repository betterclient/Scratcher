package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit

object DeadCodeElimination : Optimization {
    override fun shouldApply(func: Function, callGraph: TCallGraph) = true
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        var modified = false

        visit(func, object : ASTVisitor() {
            override fun visitIfStatement(condition: Expression, thenBlock: CodeBlock): Statement? {
                if (condition is BooleanLiteral) {
                    modified = true
                    return if (condition.value) {
                        //inline
                        CompositeStatement(thenBlock.code)
                    } else {
                        null
                    }
                }

                return super.visitIfStatement(condition, thenBlock)
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                if (condition is BooleanLiteral) {
                    if (!condition.value) {
                        modified = true
                        return null
                    }
                }

                return super.visitWhileStatement(condition, block)
            }

            override fun visitIfElseStatement(
                condition: Expression,
                thenBlock: CodeBlock,
                elseBlock: CodeBlock
            ): Statement? {
                if (condition is BooleanLiteral) {
                    modified = true
                    return if (condition.value) {
                        CompositeStatement(thenBlock.code)
                    } else {
                        CompositeStatement(elseBlock.code)
                    }
                }

                return super.visitIfElseStatement(condition, thenBlock, elseBlock)
            }
        })

        //if "if(true) return;" got inlined
        if (pruneUnreachableCode(func.code)) {
            modified = true
        }

        return modified
    }

    private fun pruneUnreachableCode(block: CodeBlock): Boolean {
        var modified = false
        val code = block.code

        for (statement in code) {
            when (statement) {
                is IfStatement -> {
                    if (pruneUnreachableCode(statement.thenBlock)) modified = true
                }
                is IfElseStatement -> {
                    if (pruneUnreachableCode(statement.thenBlock)) modified = true
                    if (pruneUnreachableCode(statement.elseBlock)) modified = true
                }
                is WhileStatement -> {
                    if (pruneUnreachableCode(statement.block)) modified = true
                }
                is RepeatStatement -> {
                    if (pruneUnreachableCode(statement.block)) modified = true
                }
                else -> {}
            }
        }

        val returnIndex = code.indexOfFirst { it is ReturnStatement }
        if (returnIndex != -1 && returnIndex < code.size - 1) {
            val keptCode = code.take(returnIndex + 1)
            block.code.clear()
            block.code.addAll(keptCode)
            modified = true
        }

        return modified
    }
}