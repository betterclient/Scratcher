package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

class ReParseLocalVariables(val func: Function) : ASTVisitor() {
    fun run() {
        visit(func, this)
    }

    private var currentCollector: MutableSet<LocalVariable>? = null
    override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val previousCollector = currentCollector
        val myCollector = mutableSetOf<LocalVariable>()
        currentCollector = myCollector

        super.visitCodeBlock(block)

        block.localVariables.clear()
        block.localVariables.addAll(myCollector)

        currentCollector = previousCollector
        return block
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        currentCollector?.add(variable)
        return super.visitVariableStatement(defaultValue, variable)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
        currentCollector?.add(variable)
        return super.visitLocalVariableAssignmentStatement(variable, assignment)
    }
}