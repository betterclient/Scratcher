package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import java.math.BigInteger

object RepeatToWhile : Optimization {
    private var counterIndex = 0

    override fun shouldApply(func: Function, callGraph: TCallGraph) = true
    override fun apply(
        func: Function,
        graph: TCallGraph
    ): Boolean {
        var modified = false

        visit(func, object : ASTVisitor() {
            override fun visitRepeatStatement(amount: Expression, block: CodeBlock): Statement? {
                val outerBlock = currentBlock ?: throw IllegalStateException("currentBlock is null")

                val counterVar = LocalVariable(
                    obfuscate("compiler@repeatCounteri" + counterIndex++),
                    Type.int
                )

                outerBlock.localVariables.add(counterVar)

                val visitedBlock = visitCodeBlock(block)
                currentBlock = outerBlock
                val initStmt = VariableStatement(amount, counterVar)
                val decrementStmt = LocalVariableAssignmentStatement(
                    counterVar,
                    BinaryExpression(
                        LocalVariableExpression(counterVar),
                        BinaryOperator.SUBTRACT,
                        IntLiteral(BigInteger.ONE)
                    )
                )

                visitedBlock.code.add(decrementStmt)

                val condition = BinaryExpression(
                    LocalVariableExpression(counterVar),
                    BinaryOperator.GREATER_THAN,
                    IntLiteral(BigInteger.ZERO)
                )

                val whileStmt = WhileStatement(condition, visitedBlock)

                modified = true

                return CompositeStatement(listOf(initStmt, whileStmt))
            }
        })

        return modified
    }
}