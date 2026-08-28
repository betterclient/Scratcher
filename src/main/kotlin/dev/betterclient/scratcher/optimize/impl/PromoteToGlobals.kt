package dev.betterclient.scratcher.optimize.impl

import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.ArrayType
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.ast.WhenBranch
import dev.betterclient.scratcher.ast.WhenExpression
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.OptimizationUtils
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator

object PromoteToGlobals : Optimization("Promote to globals") {
    override fun shouldApply(func: Function, callGraph: TCallGraph): Boolean {
        return func.warp || func.isEventListener
    }

    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        if (context.isPreOptimize) return false

        val eligible = findEligibleLocals(func, graph)
        if (eligible.isEmpty()) return false
        val globals = eligible.associateWith {
            val variable = TLVariable(
                name = obfuscate("compiler@promoteToGlobal@${func.name}::${it.name}"),
                mutable = true,
                type = it.type,
                defaultValue = null,
                sourceAST = StandardLibASTGenerator.globalPromotionLib
            )
            StandardLibASTGenerator.globalPromotionLib.variables.add(variable)
            variable
        }

        visit(func, object : ASTVisitor() {
            override fun visitExpressionStatement(expression: Expression): Statement? {
                if (expression is CallExpression &&
                    (expression.func.name.startsWith("dec@") || expression.func.name.startsWith("decList@")) &&
                    expression.arguments.size == 1
                ) {
                    val arg = expression.arguments[0]
                    if (arg is LocalVariableExpression && globals.containsKey(arg.variable)) {
                        return null
                    }
                }
                return super.visitExpressionStatement(expression)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                globals[variable]?.let {
                    return TLVariableAssignmentStatement(it, it.sourceAST, assignment)
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                globals[variable]?.let {
                    return VariableExpression(it, it.sourceAST)
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                globals[variable]?.let { variable ->
                    defaultValue?.let {
                        return TLVariableAssignmentStatement(variable, variable.sourceAST, it)
                    }
                    return null
                }

                return super.visitVariableStatement(defaultValue, variable)
            }
        })

        return true
    }

    private fun findEligibleLocals(current: Function, graph: TCallGraph): List<LocalVariable> {
        val locals = OptimizationUtils.countLocals(current)
        if (!OptimizationUtils.isRecursive(current, graph)) {
            return locals.filter { variable -> variable.type.isPrimitive || variable.type is ArrayType }
        }

        val variableStates = locals.associateWith { VariableState.NOT_DECLARED }.toMutableMap()

        //this is called liveness analysis?
        visit(current, object : ASTVisitor() {
            private val manuallyVisiting = mutableListOf<CodeBlock>()
            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
                if (block == current.code || block in manuallyVisiting) {
                    return VisitMode.READ_ONLY
                }
                return VisitMode.NONE
            }

            private fun visitBlockManually(block: CodeBlock) {
                manuallyVisiting.add(block)
                visitCodeBlock(block)
                manuallyVisiting.remove(block)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                if (defaultValue != null) {
                    variableStates[variable] = VariableState.DIRTY
                }

                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                if (variableStates[variable] != VariableState.UNSAFE) {
                    variableStates[variable] = VariableState.DIRTY
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                if(variableStates[variable] == VariableState.USED) {
                    variableStates[variable] = VariableState.UNSAFE
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                if (OptimizationUtils.hasCalls(current, func, graph)) {
                    //recursive!
                    variableStates.replaceAll { _, state ->
                        if (state == VariableState.DIRTY) {
                            VariableState.USED
                        } else state
                    }
                }

                return super.visitCallExpression(func, args)
            }

            override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
                subject?.let { visit(it) }

                val preWhenStates = variableStates.toMap()
                val branchOutcomes = mutableListOf<Map<LocalVariable, VariableState>>()

                for (branch in branches) {
                    variableStates.clear()
                    variableStates.putAll(preWhenStates)
                    visit(branch.cond)
                    visitBlockManually(branch.block)
                    branchOutcomes.add(variableStates.toMap())
                }

                if (branchOutcomes.isNotEmpty()) {
                    val finalMerged = branchOutcomes.reduce { acc, outcome ->
                        merge(acc, outcome)
                    }
                    variableStates.clear()
                    variableStates.putAll(finalMerged)
                } else {
                    variableStates.clear()
                    variableStates.putAll(preWhenStates)
                }

                return WhenExpression(subject, branches)
            }

            override fun visitIfStatement(condition: Expression, thenBlock: CodeBlock): Statement? {
                val pre = variableStates.toMap()
                visitBlockManually(thenBlock)
                merge(pre, variableStates).let {
                    variableStates.clear()
                    variableStates.putAll(it)
                }

                return super.visitIfStatement(condition, thenBlock)
            }

            override fun visitIfElseStatement(condition: Expression, thenBlock: CodeBlock, elseBlock: CodeBlock): Statement? {
                val preBranchStates = variableStates.toMap()

                visitBlockManually(thenBlock)
                val thenStates = variableStates.toMap()

                variableStates.clear()
                variableStates.putAll(preBranchStates)
                visitBlockManually(elseBlock)
                val elseStates = variableStates.toMap()

                variableStates.clear()
                variableStates.putAll(merge(thenStates, elseStates))

                return super.visitIfElseStatement(condition, thenBlock, elseBlock)
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                val preLoopStates = variableStates.toMap()

                visitBlockManually(block)
                val pass1States = merge(preLoopStates, variableStates)

                variableStates.clear()
                variableStates.putAll(pass1States)

                visitBlockManually(block)

                val finalStates = merge(preLoopStates, variableStates)
                variableStates.clear()
                variableStates.putAll(finalStates)

                return super.visitWhileStatement(condition, block)
            }
        })

        return variableStates.filter { (variable, state) -> (variable.type.isPrimitive || variable.type is ArrayType) && state != VariableState.UNSAFE }.keys.toList()
    }

    private fun merge(left: Map<LocalVariable, VariableState>, right: Map<LocalVariable, VariableState>): Map<LocalVariable, VariableState> {
        val out = mutableMapOf<LocalVariable, VariableState>()
        out.putAll(left)
        out.replaceAll { variable, s1 ->
            val s2 = right[variable]!!
            when {
                s1 == VariableState.UNSAFE || s2 == VariableState.UNSAFE -> VariableState.UNSAFE
                s1 == VariableState.USED || s2 == VariableState.USED -> VariableState.USED
                s1 == VariableState.DIRTY || s2 == VariableState.DIRTY -> VariableState.DIRTY
                else -> VariableState.NOT_DECLARED
            }
        }
        return out
    }
}

enum class VariableState {
    NOT_DECLARED, DIRTY, USED, UNSAFE
}