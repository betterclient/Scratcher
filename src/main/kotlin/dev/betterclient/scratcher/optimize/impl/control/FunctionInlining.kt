package dev.betterclient.scratcher.optimize.impl.control

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.*
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import java.math.BigInteger

object FunctionInlining : Optimization("Function inlining") {
    val voidMarkerExpr = TemporaryHeapGetExpression(IntLiteral(0.toBigInteger()))

    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ): Boolean {
        val eligible = InlineEligibility.findEligible(func, graph)
        val visitor = InliningVisitor(eligible)
        visit(func, visitor)
        return visitor.modified
    }

    private fun inline(func: Function, args: List<Expression>): ExpressionLowerResult {
        val prepend = mutableListOf<Statement>()

        //put args in variables (this will be inlined in later optimizations if only used once)
        val argVars = func.parameters.associateWith { LocalVariable("FunctionInlining@${it.name}${getUniqueName()}", PrimitiveType.Integer) }
        args.forEachIndexed { index, arg ->
            prepend.add(VariableStatement(arg, argVars.values.toList()[index]))
        }
        val returnVar = LocalVariable("FunctionInlining@return${getUniqueName()}", func.returnType)
        if (func.returnType != PrimitiveType.Void) {
            prepend.add(VariableStatement(null, returnVar))
        }
        val hasReturned = LocalVariable("FunctionInlining@hasReturned${getUniqueName()}", PrimitiveType.Bool)
        prepend.add(VariableStatement(BooleanLiteral(false), hasReturned))

        //returns are something...
        val out = visitCopy(func, EarlyReturnRewriter(hasReturned))
        visit(out, object : ASTVisitor() {
            override fun visitParameterExpression(parameter: Parameter): Expression {
                return LocalVariableExpression(argVars[parameter]!!)
            }

            override fun visitReturnStatement(expression: Expression?): Statement? {
                expression?.let {
                    return LocalVariableAssignmentStatement(returnVar, it)
                }
                return null
            }
        })
        prepend.addAll(out.code)

        return ExpressionLowerResult(
            if (func.returnType == PrimitiveType.Void) null else LocalVariableExpression(returnVar),
            prepend
        )
    }

    private class InliningVisitor(
        private val eligible: List<Function>
    ) : ASTVisitor() {
    var modified = false
    private val inlinedExprs = mutableListOf<Expression>()
    private val frames = mutableListOf<Frame>()
    private var isInWhileCondition = false
    private val conditionLists = mutableListOf<MutableList<Statement>>()

    private class Frame(
        val expression: Expression,
        val indexInParent: Int,
        val children: MutableList<Frame> = mutableListOf()
    )

    override fun visitExpr(expression: Expression) {
        val parent = frames.lastOrNull()
        val frame = Frame(expression, parent?.children?.size ?: 0)
        parent?.children?.add(frame)
        frames.add(frame)
    }

    override fun afterVisit(expression: Expression, result: Expression) {
        frames.removeAt(frames.lastIndex)
    }

    override fun visitStatement(statement: Statement) {
        if (statement is WhileStatement) {
            isInWhileCondition = true
            conditionLists.add(mutableListOf())
        }
    }

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
        isInWhileCondition = false
        return super.shouldVisitCodeBlock(block)
    }

    override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
        val conditionPrepended = conditionLists.removeAt(conditionLists.lastIndex)
        val updates = conditionPrepended
            .flatMap(::flatten)
            .filter { it !is VariableStatement || it.defaultValue != null } //keep arg evaluation and hasReturned reset, skip pure declarations
        block.code.addAll(updates)

        return super.visitWhileStatement(condition, block)
    }

    private fun flatten(statement: Statement): List<Statement> {
        return when (statement) {
            is CompositeStatement -> statement.statements.flatMap(::flatten)
            else -> listOf(statement)
        }
    }

    override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
        if (func in eligible && isSafeToInline()) {
            modified = true
            val out = inline(func, args)
            if (isInWhileCondition) {
                conditionLists.last().addAll(out.prepend)
            }
            addStatements(out.prepend)
            return (out.expression?.also {
                inlinedExprs.add(it)
            })?: voidMarkerExpr
        }

        return super.visitCallExpression(func, args)
    }

    override fun visitExpressionStatement(expression: Expression): Statement? {
        if (expression == voidMarkerExpr) {
            return null
        }
        if (inlinedExprs.contains(expression)) {
            return null //ignoring returns
        }

        return super.visitExpressionStatement(expression)
    }

    private fun isSafeToInline(): Boolean {
        var index = frames.lastIndex
        while (index > 0) {
            val parentFrame = frames[index - 1]
            when (val parent = parentFrame.expression) {
                is WhenExpression -> {
                    if (parent.subject != null) return false
                    if (isInWhileCondition) return false
                }
                else -> {}
            }

            val myIndex = frames[index].indexInParent
            for (i in 0 until myIndex) {
                if (!isPure(parentFrame.children[i].expression)) return false
            }
            index--
        }
        return true
    }

    private fun isPure(expression: Expression): Boolean {
        return when (expression) {
            is Literal -> true
            is LocalVariableExpression -> true
            is ParameterExpression -> true
            is VariableExpression -> true
            is BinaryExpression -> isPure(expression.left) && isPure(expression.right)
            is UnaryExpression -> isPure(expression.expression)
            is ConcatExpression -> isPure(expression.left) && isPure(expression.right)
            is MemberExpression -> isPure(expression.expression)
            is NonNullAssertExpression -> isPure(expression.expression)
            is NonNullOrElseExpression -> isPure(expression.operand1) && isPure(expression.operand2)
            else -> false
        }
    }
}
}

object InlineEligibility {
    fun findEligible(targetFunc: Function, graph: TCallGraph): List<Function> {
        val costs = mutableMapOf<Function, BigInteger>() //need big integer to not overflow when we hit a recursive function and try to inline it!
        visit(targetFunc, object : ASTVisitor() {
            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                costs[func] = (costs[func]?: 0.toBigInteger()) + calculateCost(targetFunc, func, graph).toBigInteger()

                return super.visitCallExpression(func, args)
            }
        })

        return costs.filter { (_, cost) -> cost <= 5000.toBigInteger() }.keys.toList().filter { it !is StandardLibASTFunction && it !is InlineStandardLibFunction }
    }

    private fun calculateCost(func: Function, targetFunc: Function, graph: TCallGraph): Int {
        var currentCost = 0
        if (targetFunc.name == "markTopLevels" && targetFunc.sourceAST == StandardLibASTGenerator.gc) return Int.MAX_VALUE
        if (targetFunc.sourceAST == StandardLibASTGenerator.typeChecker) return Int.MAX_VALUE
        if (OptimizationUtils.isRecursive(targetFunc, graph)) return Int.MAX_VALUE
        if (func.warp != targetFunc.warp) return Int.MAX_VALUE //not happening...

        visit(targetFunc, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
            override fun visitReturnStatement(expression: Expression?): Statement? {
                currentCost += 600
                return super.visitReturnStatement(expression)
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                currentCost += 300
                return super.visitWhileStatement(condition, block)
            }

            override fun visitIfStatement(condition: Expression, thenBlock: CodeBlock): Statement? {
                currentCost += 50
                return super.visitIfStatement(condition, thenBlock)
            }

            override fun visitIfElseStatement(
                condition: Expression,
                thenBlock: CodeBlock,
                elseBlock: CodeBlock
            ): Statement? {
                currentCost += 100
                return super.visitIfElseStatement(condition, thenBlock, elseBlock)
            }

            override fun visitExpr(expression: Expression) {
                currentCost += 20
            }

            override fun visitStatement(statement: Statement) {
                currentCost += 50
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                currentCost += 100
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
                currentCost += when (func) {
                    is InlineStandardLibFunction -> 100
                    is StandardLibASTFunction -> 20
                    else -> 20
                }

                return super.visitCallExpression(func, args)
            }

            override fun visitNonNullAssertExpression(expression: Expression): Expression {
                currentCost += 100
                return super.visitNonNullAssertExpression(expression)
            }

            override fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression): Expression {
                currentCost += 200

                return super.visitNonNullOrElseExpression(operand1, operand2)
            }
        })

        return currentCost
    }
}

class EarlyReturnRewriter(
    val hasReturned: LocalVariable
) : ASTVisitor() {

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
        return VisitMode.COPY
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val mode = shouldVisitCodeBlock(block)
        if (mode != VisitMode.COPY) {
            return super.visitCodeBlock(block)
        }

        val visitedBlock = super.visitCodeBlock(block)

        val processed = sequentiallyWrap(visitedBlock.code)
        visitedBlock.code.clear()
        visitedBlock.code.addAll(processed)

        return visitedBlock
    }

    private fun sequentiallyWrap(statements: List<Statement>): List<Statement> {
        val rewrittenStatements = mutableListOf<Statement>()
        var hasPossibleReturnBefore = false
        val currentDeferred = mutableListOf<Statement>()

        for (stmt in statements) {
            if (hasPossibleReturnBefore) {
                currentDeferred.add(stmt)
            } else {
                rewrittenStatements.add(stmt)
                if (containsHasReturnedSet(stmt)) {
                    hasPossibleReturnBefore = true
                }
            }
        }

        if (currentDeferred.isNotEmpty()) {
            val processedDeferred = sequentiallyWrap(currentDeferred)
            val innerBlock = CodeBlock(processedDeferred.toMutableList())
            val condition = UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(hasReturned))
            rewrittenStatements.add(IfStatement(condition, innerBlock))
        }

        return rewrittenStatements
    }

    override fun visitReturnStatement(expression: Expression?): Statement {
        val list = mutableListOf<Statement>()
        list.add(ReturnStatement(expression?.let { visit(it) }))
        list.add(LocalVariableAssignmentStatement(hasReturned, BooleanLiteral(true)))
        return CompositeStatement(list)
    }

    override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement {
        val visited = super.visitWhileStatement(condition, block) as WhileStatement
        if (containsHasReturnedSet(visited.block)) {
            val loopCondition = BinaryExpression(
                visited.condition,
                BinaryOperator.AND,
                UnaryExpression(UnaryOperator.NOT, LocalVariableExpression(hasReturned))
            )
            return WhileStatement(loopCondition, visited.block)
        }
        return visited
    }

    private fun containsHasReturnedSet(statement: Statement): Boolean {
        return when (statement) {
            is LocalVariableAssignmentStatement -> statement.variable == hasReturned
            is VariableStatement -> statement.variable == hasReturned
            is IfStatement -> containsHasReturnedSet(statement.thenBlock)
            is IfElseStatement -> containsHasReturnedSet(statement.thenBlock) || containsHasReturnedSet(statement.elseBlock)
            is WhileStatement -> containsHasReturnedSet(statement.block)
            is RepeatStatement -> containsHasReturnedSet(statement.block)
            is CompositeStatement -> statement.statements.any { containsHasReturnedSet(it) }
            else -> false
        }
    }

    private fun containsHasReturnedSet(block: CodeBlock): Boolean {
        return block.code.any { containsHasReturnedSet(it) }
    }
}