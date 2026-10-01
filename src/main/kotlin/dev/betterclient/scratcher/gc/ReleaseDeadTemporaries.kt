package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.ArrayLib

object ReleaseDeadTemporaries {
    fun run(functions: Collection<Function>, decFuncs: Set<Function>, incFunc: Function): Int {
        var sunk = 0
        val stats = Stats()
        for (func in functions) {
            visit(func, object : ASTVisitor() {})
            collectInto(func, decFuncs, stats)
        }
        for (func in functions) {
            val groups = buildGroups(func)
            val stored = collectStored(func, incFunc, decFuncs)
            val sinker = DecSinker(stats, groups, decFuncs, incFunc, stored)
            visit(func, sinker)
            sunk += sinker.sunk
        }
        return sunk
    }

    private class Stats {
        val decls = mutableMapOf<LocalVariable, Int>()
        val assigns = mutableMapOf<LocalVariable, Int>()
        val reads = mutableMapOf<LocalVariable, Int>()
        val decs = mutableMapOf<LocalVariable, Int>()
        val addrs = mutableMapOf<LocalVariable, Int>()
    }

    private fun collectInto(func: Function, decFuncs: Set<Function>, stats: Stats) {
        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                stats.decls[variable] = (stats.decls[variable] ?: 0) + 1
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                stats.assigns[variable] = (stats.assigns[variable] ?: 0) + 1
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                stats.reads[variable] = (stats.reads[variable] ?: 0) + 1
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
                stats.reads[variable] = (stats.reads[variable] ?: 0) + 1
                stats.addrs[variable] = (stats.addrs[variable] ?: 0) + 1
                return super.visitTemporaryLocalVariableIndexExpression(variable)
            }

            override fun visitExpressionStatement(expression: Expression): Statement? {
                countDecCall(expression, decFuncs, stats)
                return super.visitExpressionStatement(expression)
            }

            override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
                if (func in decFuncs && args.size == 1 && args[0] is LocalVariableExpression) {
                    val target = (args[0] as LocalVariableExpression).variable
                    stats.decs[target] = (stats.decs[target] ?: 0) + 1
                }
                return super.visitTemporaryCallStatement(func, args)
            }
        })
    }

    private fun countDecCall(expression: Expression, decFuncs: Set<Function>, stats: Stats) {
        val call = expression as? CallExpression
        if (call != null && call.func in decFuncs && call.arguments.size == 1 &&
            call.arguments[0] is LocalVariableExpression
        ) {
            val target = (call.arguments[0] as LocalVariableExpression).variable
            stats.decs[target] = (stats.decs[target] ?: 0) + 1
        }
    }

    private fun buildGroups(func: Function): Map<LocalVariable, Set<LocalVariable>> {
        val parent = mutableMapOf<LocalVariable, LocalVariable>()
        fun find(v: LocalVariable): LocalVariable {
            val p = parent[v] ?: return v
            val r = find(p)
            parent[v] = r
            return r
        }

        val universe = mutableSetOf<LocalVariable>()
        fun union(a: LocalVariable, b: LocalVariable) {
            if (a == b) return
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                universe.add(variable)
                val src = (defaultValue as? LocalVariableExpression)?.variable
                if (src != null) {
                    universe.add(src)
                    union(variable, src)
                }
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                universe.add(variable)
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                universe.add(variable)
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
                universe.add(variable)
                return super.visitTemporaryLocalVariableIndexExpression(variable)
            }
        })
        val byRoot = mutableMapOf<LocalVariable, MutableSet<LocalVariable>>()
        for (v in universe) {
            byRoot.getOrPut(find(v)) { mutableSetOf() }.add(v)
        }
        val rev = mutableMapOf<LocalVariable, Set<LocalVariable>>()
        for (members in byRoot.values) {
            for (v in members) rev[v] = members
        }
        return rev
    }

    private class StoredCollector(
        private val incFunc: Function,
        private val decFuncs: Set<Function>,
        private val out: MutableSet<LocalVariable>
    ) : ASTVisitor() {
        private var storing = false

        override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

        override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
            if (storing) out.add(variable)
            return super.visitLocalVariableExpression(variable)
        }

        override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
            if (storing) out.add(variable)
            return super.visitTemporaryLocalVariableIndexExpression(variable)
        }

        override fun visitVariableAssignmentStatement(
            target: Expression,
            variable: Parameter,
            struct: Struct,
            assignment: Expression
        ): Statement? {
            val saved = storing
            visit(target)
            storing = true
            visit(assignment)
            storing = saved
            return super.visitVariableAssignmentStatement(target, variable, struct, assignment)
        }

        override fun visitTLVariableAssignmentStatement(
            variable: TLVariable,
            sourceAST: ASTFile,
            assignment: Expression
        ): Statement? {
            val saved = storing
            storing = true
            visit(assignment)
            storing = saved
            return super.visitTLVariableAssignmentStatement(variable, sourceAST, assignment)
        }

        override fun visitStaticListSetStatement(list: TLStaticList, index: Expression, item: Expression): Statement? {
            val saved = storing
            visit(index)
            storing = true
            visit(item)
            storing = saved
            return super.visitStaticListSetStatement(list, index, item)
        }

        override fun visitStaticListAddStatement(list: TLStaticList, item: Expression): Statement? {
            val saved = storing
            storing = true
            visit(item)
            storing = saved
            return super.visitStaticListAddStatement(list, item)
        }

        override fun visitStaticListInsertStatement(
            list: TLStaticList,
            index: Expression,
            value: Expression
        ): Statement? {
            val saved = storing
            visit(index)
            storing = true
            visit(value)
            storing = saved
            return super.visitStaticListInsertStatement(list, index, value)
        }

        override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
            val saved = storing
            args.forEachIndexed { idx, arg ->
                storing = if (replaceStoresIdx(func, idx)) {
                    true
                } else {
                    saved || callStores(func, incFunc, decFuncs)
                }
                visit(arg)
            }
            storing = saved
            return super.visitTemporaryCallStatement(func, args)
        }

        override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
            val saved = storing
            args.forEachIndexed { idx, arg ->
                storing = if (func == ArrayLib.replace && idx != 1) {
                    saved
                } else {
                    saved || !isPureCall(func, incFunc, decFuncs)
                }
                visit(arg)
            }
            storing = saved
            return super.visitCallExpression(func, args)
        }
    }

    private fun collectStored(func: Function, incFunc: Function, decFuncs: Set<Function>): Set<LocalVariable> {
        val out = mutableSetOf<LocalVariable>()
        visit(func, StoredCollector(incFunc, decFuncs, out))
        return out
    }

    private class ReadCollector(
        private val group: Set<LocalVariable>,
        private val top: Int,
        private val out: MutableMap<LocalVariable, MutableList<Int>>
    ) : ASTVisitor() {
        override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

        override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
            if (variable in group) out.getOrPut(variable) { mutableListOf() }.add(top)
            return super.visitLocalVariableExpression(variable)
        }

        override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
            if (variable in group) out.getOrPut(variable) { mutableListOf() }.add(top)
            return super.visitTemporaryLocalVariableIndexExpression(variable)
        }
    }

    private fun readPositions(
        code: List<Statement>,
        group: Set<LocalVariable>
    ): Map<LocalVariable, MutableList<Int>> {
        val out = mutableMapOf<LocalVariable, MutableList<Int>>()
        code.forEachIndexed { idx, s ->
            ReadCollector(group, idx, out).visit(s)
        }
        return out
    }

    private class ReturnFinder : ASTVisitor() {
        var found = false
        override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
        override fun visitReturnStatement(expression: Expression?): Statement? {
            found = true
            return super.visitReturnStatement(expression)
        }
    }

    private fun containsReturn(s: Statement): Boolean {
        val v = ReturnFinder()
        v.visit(s)
        return v.found
    }

    private fun isStraightInc(s: Statement, group: Set<LocalVariable>, incFunc: Function): Boolean {
        if (s is ExpressionStatement) return hasIncExpr(s.expression, group, incFunc)
        if (s is TemporaryCallStatement) return isIncOf(s.func, s.args, group, incFunc)
        if (s is CompositeStatement) return s.statements.any { isStraightInc(it, group, incFunc) }
        return false
    }

    private fun hasIncExpr(e: Expression, group: Set<LocalVariable>, incFunc: Function): Boolean {
        if (e is CallExpression) return isIncOf(e.func, e.arguments, group, incFunc)
        if (e is StatementExpression) {
            return e.statements.any { isStraightInc(it, group, incFunc) } || hasIncExpr(e.expression, group, incFunc)
        }
        return false
    }

    private fun isIncOf(func: Function, args: List<Expression>, group: Set<LocalVariable>, incFunc: Function): Boolean {
        if (func != incFunc || args.size != 1) return false
        val v = (args[0] as? LocalVariableExpression)?.variable
        return v != null && v in group
    }

    private fun isPureCall(func: Function, incFunc: Function, decFuncs: Set<Function>): Boolean {
        return func == incFunc || func in decFuncs ||
                func == ArrayLib.length || func == ArrayLib.itemAt
    }

    private fun replaceStoresIdx(func: Function, idx: Int): Boolean {
        return func == ArrayLib.replace && idx == 1
    }

    private fun callStores(func: Function, incFunc: Function, decFuncs: Set<Function>): Boolean {
        return !isPureCall(func, incFunc, decFuncs) && func != ArrayLib.replace
    }

    private fun matchDec(stmt: Statement, decFuncs: Set<Function>): LocalVariable? {
        val exprStmt = stmt as? ExpressionStatement
        if (exprStmt != null) {
            val expr = exprStmt.expression as? CallExpression ?: return null
            if (expr.func !in decFuncs || expr.arguments.size != 1) return null
            return (expr.arguments[0] as? LocalVariableExpression)?.variable
        }
        val tmp = stmt as? TemporaryCallStatement
        if (tmp != null) {
            if (tmp.func !in decFuncs || tmp.args.size != 1) return null
            return (tmp.args[0] as? LocalVariableExpression)?.variable
        }
        return null
    }

    private fun processList(
        code: MutableList<Statement>,
        stats: Stats,
        groups: Map<LocalVariable, Set<LocalVariable>>,
        decFuncs: Set<Function>,
        incFunc: Function,
        stored: Set<LocalVariable>
    ): Int {
        val moved = mutableSetOf<LocalVariable>()
        var sunk = 0
        var j = 0
        while (j < code.size) {
            val target = matchDec(code[j], decFuncs)
            if (target != null && target !in moved &&
                trySink(code, j, target, stats, groups, incFunc, stored)
            ) {
                moved.add(target)
                sunk++
                continue
            }
            j++
        }
        return sunk
    }

    private fun trySink(
        code: MutableList<Statement>,
        decIdx: Int,
        target: LocalVariable,
        stats: Stats,
        groups: Map<LocalVariable, Set<LocalVariable>>,
        incFunc: Function,
        stored: Set<LocalVariable>
    ): Boolean {
        if ((stats.decls[target] ?: 0) != 1) return false
        if ((stats.assigns[target] ?: 0) != 0) return false
        if ((stats.decs[target] ?: 0) != 1) return false

        var defIdx = -1
        for (i in 0 until decIdx) {
            val s = code[i]
            if (s is VariableStatement && s.variable == target) {
                defIdx = i
                break
            }
        }
        if (defIdx < 0) return false

        val group = groups[target] ?: setOf(target)
        for (u in group) {
            if ((stats.decls[u] ?: 0) != 1) return false
            if ((stats.assigns[u] ?: 0) != 0) return false
            if ((stats.addrs[u] ?: 0) > 1) return false
            if ((stats.decs[u] ?: 0) > 1) return false
        }

        var permanent = false
        for (i in code.indices) {
            if (isStraightInc(code[i], group, incFunc)) {
                permanent = true
                break
            }
        }
        if (!permanent && group.any { it in stored }) return false

        val positions = readPositions(code, group)
        var total = 0
        var lastObs = defIdx
        for (u in group) {
            val list = positions[u] ?: emptyList()
            total += list.size
            for (j in list) {
                if (j > decIdx) return false
                if (j in defIdx..<decIdx && j > lastObs) lastObs = j
            }
        }
        var expected = 0
        for (u in group) expected += stats.reads[u] ?: 0
        if (total != expected) return false
        if (lastObs + 1 == decIdx) return false

        for (i in defIdx + 1 until decIdx) {
            if (containsReturn(code[i])) return false
        }

        val stmt = code.removeAt(decIdx)
        code.add(lastObs + 1, stmt)
        return true
    }

    private class DecSinker(
        private val stats: Stats,
        private val groups: Map<LocalVariable, Set<LocalVariable>>,
        private val decFuncs: Set<Function>,
        private val incFunc: Function,
        private val stored: Set<LocalVariable>
    ) : ASTVisitor() {
        var sunk = 0

        override fun visitCodeBlock(block: CodeBlock): CodeBlock {
            sunk += processList(block.code, stats, groups, decFuncs, incFunc, stored)
            return super.visitCodeBlock(block)
        }

        override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
            val code = statements.toMutableList()
            sunk += processList(code, stats, groups, decFuncs, incFunc, stored)
            return super.visitStatementExpression(code, expression)
        }
    }
}