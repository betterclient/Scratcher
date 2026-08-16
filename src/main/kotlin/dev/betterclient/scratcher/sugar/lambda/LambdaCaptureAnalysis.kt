package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

class LambdaCaptureAnalysis : ASTVisitor() {
    private val scopeStack = ArrayDeque<Scope>()

    private class Scope(val lambda: LambdaExpression?) {
        val declaredLocals = mutableSetOf<LocalVariable>()
    }

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

    fun run(func: Function) {
        scopeStack.clear()
        val rootScope = Scope(null)
        scopeStack.addLast(rootScope)
        visit(func, this)
        scopeStack.removeLast()
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        scopeStack.lastOrNull()?.declaredLocals?.addAll(block.localVariables)
        return super.visitCodeBlock(block)
    }

    override fun visitParameterExpression(parameter: Parameter): Expression {
        return super.visitParameterExpression(parameter)
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        scopeStack.lastOrNull()?.declaredLocals?.add(variable)
        return super.visitVariableStatement(defaultValue, variable)
    }

    override fun visitLambdaExpression(
        block: CodeBlock,
        arguments: List<LocalVariable>,
        captured: MutableSet<LocalVariable>
    ): Expression {
        captured.clear()

        val lambdaScope = Scope(LambdaExpression(arguments, block, captured))
        lambdaScope.declaredLocals.addAll(arguments)
        lambdaScope.declaredLocals.addAll(block.localVariables)

        scopeStack.addLast(lambdaScope)
        super.visitLambdaExpression(block, arguments, captured)
        scopeStack.removeLast()

        return LambdaExpression(arguments, block, captured)
    }

    override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
        recordVariableAccess(variable)
        return super.visitLocalVariableExpression(variable)
    }

    override fun visitLocalVariableAssignmentStatement(
        variable: LocalVariable,
        assignment: Expression
    ): Statement? {
        recordVariableAccess(variable)
        return super.visitLocalVariableAssignmentStatement(variable, assignment)
    }

    private fun recordVariableAccess(variable: LocalVariable) {
        val currentScope = scopeStack.lastOrNull() ?: return
        if (currentScope.declaredLocals.contains(variable)) {
            return
        }

        var foundDeclaringScope = false
        val intermediateScopes = mutableListOf<Scope>()

        for (scope in scopeStack.reversed()) {
            if (scope.declaredLocals.contains(variable)) {
                foundDeclaringScope = true
                break
            }
            if (scope.lambda != null) {
                intermediateScopes.add(scope)
            }
        }

        if (foundDeclaringScope) {
            for (scope in intermediateScopes) {
                scope.lambda?.capturedVariables?.add(variable)
            }
        }
    }
}