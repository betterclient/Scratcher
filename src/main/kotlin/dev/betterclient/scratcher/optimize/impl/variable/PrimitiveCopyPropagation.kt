package dev.betterclient.scratcher.optimize.impl.variable

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object PrimitiveCopyPropagation : Optimization("Primitive copy propagation") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        val worker = Worker()
        worker.processBlock(func.code)
        return worker.changed
    }

    private class Worker {
        var changed = false

        private val recurseVisitor = object : ASTVisitor() {
            override fun visitCodeBlock(block: CodeBlock): CodeBlock {
                processStmtList(block.code)
                return super.visitCodeBlock(block)
            }

            override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
                val inner = statements.toMutableList()
                processStmtList(inner)
                return super.visitStatementExpression(inner, expression)
            }
        }

        fun processBlock(block: CodeBlock) {
            recurseVisitor.visitCodeBlock(block)
        }

        private fun processStmtList(code: MutableList<Statement>) {
            var i = 0
            while (i < code.size) {
                val copy = matchCopy(code[i])
                if (copy != null) {
                    runCandidate(code, i, copy.first, copy.second)
                }
                i++
            }
        }

        private fun matchCopy(stmt: Statement): Pair<LocalVariable, LocalVariable>? {
            val dest: LocalVariable
            val init: Expression?
            when (stmt) {
                is VariableStatement -> {
                    dest = stmt.variable
                    init = stmt.defaultValue
                }
                is LocalVariableAssignmentStatement -> {
                    dest = stmt.variable
                    init = stmt.assignment
                }
                else -> {
                    return null
                }
            }
            val src = (init as? LocalVariableExpression)?.variable ?: return null
            if (dest == src) return null
            if (!dest.type.isPrimitive || !src.type.isPrimitive) return null
            if (dest.type != src.type) return null
            return dest to src
        }

        private fun runCandidate(code: MutableList<Statement>, startIdx: Int, dest: LocalVariable, src: LocalVariable) {
            var j = startIdx + 1
            while (j < code.size) {
                val stmt = code[j]
                if (defines(stmt, src)) break
                val destRedefined = defines(stmt, dest)
                if (mentionsNested(stmt, dest, src)) break
                code[j] = substUsesInStmt(stmt, dest, src)
                if (destRedefined) break
                j++
            }
        }

        private fun defines(stmt: Statement, v: LocalVariable): Boolean {
            if (stmt is VariableStatement) return stmt.variable == v
            if (stmt is LocalVariableAssignmentStatement) return stmt.variable == v
            return false
        }

        private fun mentionsNested(stmt: Statement, a: LocalVariable, b: LocalVariable): Boolean {
            val checker = NestedMentionChecker(a, b)
            checker.visit(stmt)
            return checker.found
        }

        private class NestedMentionChecker(val a: LocalVariable, val b: LocalVariable) : ASTVisitor() {
            var found = false
            var depth = 0

            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitCodeBlock(block: CodeBlock): CodeBlock {
                depth++
                try {
                    return super.visitCodeBlock(block)
                } finally {
                    depth--
                }
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                if (depth > 0 && (variable == a || variable == b)) found = true
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (depth > 0 && (variable == a || variable == b)) found = true
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
                if (depth > 0 && (variable == a || variable == b)) found = true
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
                depth++
                try {
                    statements.forEach { visit(it) }
                } finally {
                    depth--
                }
                return super.visitStatementExpression(statements, expression)
            }

            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
                if (subject != null) {
                    depth++
                    try {
                        visit(subject)
                    } finally {
                        depth--
                    }
                }
                return super.visitWhenExpr(branches, subject)
            }

            override fun visitLambdaExpression(block: CodeBlock, arguments: List<LocalVariable>, captured: MutableSet<LocalVariable>): Expression {
                if (captured.any { it == a || it == b }) found = true
                return super.visitLambdaExpression(block, arguments, captured)
            }
        }

        private fun substUsesInStmt(stmt: Statement, dest: LocalVariable, src: LocalVariable): Statement {
            val v = object : ASTVisitor() {
                override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                    if (variable == dest) {
                        this@Worker.changed = true
                        return LocalVariableExpression(src)
                    }
                    return super.visitLocalVariableExpression(variable)
                }

                override fun visitCodeBlock(block: CodeBlock): CodeBlock = block

                override fun visitLambdaExpression(block: CodeBlock, arguments: List<LocalVariable>, captured: MutableSet<LocalVariable>): Expression =
                    LambdaExpression(arguments, block, captured)

                override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression =
                    StatementExpression(statements, visit(expression))

                override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression =
                    WhenExpression(subject, branches.map { it.copy(cond = visit(it.cond)) })
            }
            if (stmt is ExpressionStatement) return stmt.copy(expression = v.visit(stmt.expression))
            if (stmt is VariableStatement) return stmt.copy(defaultValue = stmt.defaultValue?.let { v.visit(it) })
            if (stmt is LocalVariableAssignmentStatement) return stmt.copy(assignment = v.visit(stmt.assignment))
            if (stmt is VariableAssignmentStatement) return stmt.copy(target = v.visit(stmt.target), assignment = v.visit(stmt.assignment))
            if (stmt is TLVariableAssignmentStatement) return stmt.copy(assignment = v.visit(stmt.assignment))
            if (stmt is ReturnStatement) return stmt.copy(expression = stmt.expression?.let { v.visit(it) })
            if (stmt is StaticListSetStatement) return stmt.copy(index = v.visit(stmt.index), value = v.visit(stmt.value))
            if (stmt is StaticListAddStatement) return stmt.copy(item = v.visit(stmt.item))
            if (stmt is StaticListInsertStatement) return stmt.copy(index = v.visit(stmt.index), value = v.visit(stmt.value))
            if (stmt is StaticListRemoveStatement) return stmt.copy(index = v.visit(stmt.index))
            return stmt
        }
    }
}