package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TLStaticList
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

class FunctionReachability(val entrypoints: List<ASTEventListener>) {

    fun run(startAST: ASTFile): Triple<MutableList<Function>, Map<TLVariable, Expression?>, List<TLStaticList>> {
        val visitedFunctions = mutableSetOf<Function>()
        val visitedVariables = mutableSetOf<TLVariable>()

        val functionQueue = ArrayDeque<Function>()
        val variableQueue = ArrayDeque<TLVariable>()
        val reachableLists = mutableListOf<TLStaticList>()

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

            override fun visitStaticListAddStatement(list: TLStaticList, item: Expression): Statement? {
                reachableLists.add(list)
                return super.visitStaticListAddStatement(list, item)
            }

            override fun visitStaticListClearStatement(list: TLStaticList): Statement? {
                reachableLists.add(list)
                return super.visitStaticListClearStatement(list)
            }

            override fun visitStaticListContainsExpression(list: TLStaticList, item: Expression): Expression {
                reachableLists.add(list)
                return super.visitStaticListContainsExpression(list, item)
            }

            override fun visitStaticListInsertStatement(
                list: TLStaticList,
                index: Expression,
                value: Expression
            ): Statement? {
                reachableLists.add(list)
                return super.visitStaticListInsertStatement(list, index, value)
            }

            override fun visitStaticListItemExpression(list: TLStaticList, item: Expression): Expression {
                reachableLists.add(list)
                return super.visitStaticListItemExpression(list, item)
            }

            override fun visitStaticListItemIndexExpression(list: TLStaticList, item: Expression): Expression {
                reachableLists.add(list)
                return super.visitStaticListItemIndexExpression(list, item)
            }

            override fun visitStaticListLengthExpression(list: TLStaticList): Expression {
                reachableLists.add(list)
                return super.visitStaticListLengthExpression(list)
            }

            override fun visitStaticListRemoveStatement(list: TLStaticList, index: Expression): Statement? {
                reachableLists.add(list)
                return super.visitStaticListRemoveStatement(list, index)
            }

            override fun visitStaticListSetStatement(
                list: TLStaticList,
                index: Expression,
                item: Expression
            ): Statement? {
                reachableLists.add(list)
                return super.visitStaticListSetStatement(list, index, item)
            }

            override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
                enqueueFunction(func)
                return super.visitTemporaryCallStatement(func, args)
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

        return Triple(visitedFunctions.toMutableList(), visitedVariables.associateWith { it.defaultValue }, reachableLists.distinct())
    }
}