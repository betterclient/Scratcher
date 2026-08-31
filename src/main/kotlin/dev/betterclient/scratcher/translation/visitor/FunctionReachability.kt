package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

class FunctionReachability(val entrypoints: List<ASTEventListener>) {

    fun run(startAST: ASTFile): Pair<MutableList<Function>, Map<TLVariable, Expression?>> {
        val visitedFunctions = mutableSetOf<Function>()
        val visitedVariables = mutableSetOf<TLVariable>()

        val functionQueue = ArrayDeque<Function>()
        val variableQueue = ArrayDeque<TLVariable>()

        fun enqueueFunction(func: Function) {
            if (func is InlineStandardLibFunction) return
            if (visitedFunctions.add(func)) {
                functionQueue.addLast(func)
            }
        }

        fun enqueueVariable(variable: TLVariable) {
            if (visitedVariables.add(variable)) {
                variableQueue.addLast(variable)
            }
        }

        val visitor = object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                enqueueFunction(func)
                return super.visitCallExpression(func, args)
            }

            override fun visitFunctionLiteral(func: Function): Expression {
                enqueueFunction(func)
                return super.visitFunctionLiteral(func)
            }

            override fun visitTLVariableAssignmentStatement(
                variable: TLVariable,
                sourceAST: ASTFile,
                assignment: Expression
            ): Statement? {
                enqueueVariable(variable)
                return super.visitTLVariableAssignmentStatement(variable, sourceAST, assignment)
            }

            override fun visitVariableExpression(variable: TLVariable, sourceAST: ASTFile): Expression {
                enqueueVariable(variable)
                return super.visitVariableExpression(variable, sourceAST)
            }
        }

        entrypoints.forEach { entrypoint ->
            entrypoint.sourceAST.variables.forEach { variable ->
                enqueueVariable(variable)
            }
            entrypoint.ctx?.let { func ->
                enqueueFunction(func)
            }
        }

        val visitedImports = mutableSetOf<ASTFile>()
        fun visitExports(ast: ASTFile) {
            if (!visitedImports.add(ast)) return
            ast.functions.forEach { func ->
                if (func.export) enqueueFunction(func)
            }
            ast.imports.values.forEach(::visitExports)
            (ast.flatImportNames.values + ast.wildcardImportSources).forEach(::visitExports)
        }
        visitExports(startAST)

        while (functionQueue.isNotEmpty() || variableQueue.isNotEmpty()) {
            if (functionQueue.isNotEmpty()) {
                val func = functionQueue.removeFirst()
                visit(func, visitor)
            } else {
                val variable = variableQueue.removeFirst()
                variable.defaultValue?.let { expr ->
                    visitor.visit(expr)
                }
            }
        }

        return visitedFunctions.toMutableList() to visitedVariables.associateWith { it.defaultValue }
    }
}