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
import org.antlr.v4.kotlinruntime.ast.Position

class FunctionReachability(val entrypoints: List<ASTEventListener>) {

    fun run(startAST: ASTFile): Triple<MutableList<Function>, Map<TLVariable, Expression?>, List<TLStaticList>> {
        val visitedFunctions = mutableSetOf<Function>()
        val visitedVariables = mutableSetOf<TLVariable>()

        val functionQueue = ArrayDeque<Function>()
        val variableQueue = ArrayDeque<TLVariable>()
        val reachableLists = mutableListOf<TLStaticList>()

        val visitor = Visitor({ func ->
            if (func is InlineStandardLibFunction) return@Visitor
            if (visitedFunctions.add(func)) {
                functionQueue.addLast(func)
            }
        }, { variable ->
            if (visitedVariables.add(variable)) {
                variableQueue.addLast(variable)
            }
        }, reachableLists)

        entrypoints.forEach { entrypoint ->
            entrypoint.sourceAST.variables.forEach { variable ->
                if (visitedVariables.add(variable)) {
                    variableQueue.addLast(variable)
                }
            }
            entrypoint.ctx?.let { func ->
                if (func is InlineStandardLibFunction) return@let
                if (visitedFunctions.add(func)) {
                    functionQueue.addLast(func)
                }
            }
        }

        val visitedImports = mutableSetOf<ASTFile>()
        fun visitExports(ast: ASTFile) {
            if (!visitedImports.add(ast)) return
            ast.functions.forEach { func ->
                if (func.export) {
                    if (func is InlineStandardLibFunction) return@forEach
                    if (visitedFunctions.add(func)) {
                        functionQueue.addLast(func)
                    }
                }
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

    class Visitor(
        val enqueueFunction: (Function) -> Unit,
        val enqueueVariable: (TLVariable) -> Unit,
        val reachableLists: MutableList<TLStaticList>
    ) : ASTVisitor() {
        override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

        override fun visitCallExpression(func: Function, args: List<Expression>, position: Position?): Expression {
            enqueueFunction(func)
            return super.visitCallExpression(func, args, position)
        }

        override fun visitFunctionLiteral(func: Function, position: Position?): Expression {
            enqueueFunction(func)
            return super.visitFunctionLiteral(func, position)
        }

        override fun visitTLVariableAssignmentStatement(
            variable: TLVariable,
            sourceAST: ASTFile,
            assignment: Expression,
            position: Position?
        ): Statement? {
            enqueueVariable(variable)
            return super.visitTLVariableAssignmentStatement(variable, sourceAST, assignment, position)
        }

        override fun visitVariableExpression(variable: TLVariable, sourceAST: ASTFile, position: Position?): Expression {
            enqueueVariable(variable)
            return super.visitVariableExpression(variable, sourceAST, position)
        }

        override fun visitStaticListAddStatement(list: TLStaticList, item: Expression, position: Position?): Statement? {
            reachableLists.add(list)
            return super.visitStaticListAddStatement(list, item, position)
        }

        override fun visitStaticListClearStatement(list: TLStaticList, position: Position?): Statement? {
            reachableLists.add(list)
            return super.visitStaticListClearStatement(list, position)
        }

        override fun visitStaticListContainsExpression(list: TLStaticList, item: Expression, position: Position?): Expression {
            reachableLists.add(list)
            return super.visitStaticListContainsExpression(list, item, position)
        }

        override fun visitStaticListInsertStatement(
            list: TLStaticList,
            index: Expression,
            value: Expression,
            position: Position?
        ): Statement? {
            reachableLists.add(list)
            return super.visitStaticListInsertStatement(list, index, value, position)
        }

        override fun visitStaticListItemExpression(list: TLStaticList, item: Expression, position: Position?): Expression {
            reachableLists.add(list)
            return super.visitStaticListItemExpression(list, item, position)
        }

        override fun visitStaticListItemIndexExpression(list: TLStaticList, item: Expression, position: Position?): Expression {
            reachableLists.add(list)
            return super.visitStaticListItemIndexExpression(list, item, position)
        }

        override fun visitStaticListLengthExpression(list: TLStaticList, position: Position?): Expression {
            reachableLists.add(list)
            return super.visitStaticListLengthExpression(list, position)
        }

        override fun visitStaticListRemoveStatement(list: TLStaticList, index: Expression, position: Position?): Statement? {
            reachableLists.add(list)
            return super.visitStaticListRemoveStatement(list, index, position)
        }

        override fun visitStaticListSetStatement(
            list: TLStaticList,
            index: Expression,
            item: Expression,
            position: Position?
        ): Statement? {
            reachableLists.add(list)
            return super.visitStaticListSetStatement(list, index, item, position)
        }

        override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>, position: Position?): Statement? {
            enqueueFunction(func)
            return super.visitTemporaryCallStatement(func, args, position)
        }
    }

    fun run(root: Function): MutableList<Function> {
        val visitedFunctions = mutableSetOf<Function>()

        val functionQueue = ArrayDeque<Function>()

        val visitor = Visitor({ func ->
            if (func is InlineStandardLibFunction) return@Visitor
            if (visitedFunctions.add(func)) {
                functionQueue.addLast(func)
            }
        }, { _ -> }, mutableListOf())

        if (root !is InlineStandardLibFunction) {
            if (visitedFunctions.add(root)) {
                functionQueue.addLast(root)
            }
        }

        while (functionQueue.isNotEmpty()) {
            val func = functionQueue.removeFirst()
            visit(func, visitor)
        }

        return visitedFunctions.toMutableList()
    }
}