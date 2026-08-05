package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.std.lib.ListLib
import java.math.BigInteger

class RefCountVisitor(
    val structDecs: Map<Struct, Function>,
    val inc: Function,
    val generateDecList: (ListType) -> Function,
    val compilationContext: CompilationContext,
    val currentFunction: Function? = null
) : ASTVisitor() {

    private var isInWhileCondition = false
    private val whileCondTempsStack = mutableListOf<MutableList<Pair<LocalVariable, Expression>>>()
    private val activeScopes = mutableListOf<MutableSet<LocalVariable>>()

    override fun visitStatement(statement: Statement) {
        if (statement is WhileStatement) {
            isInWhileCondition = true
            whileCondTempsStack.add(mutableListOf())
        }
        super.visitStatement(statement)
    }

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
        isInWhileCondition = false
        return super.shouldVisitCodeBlock(block)
    }

    override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement {
        val condTemps = if (whileCondTempsStack.isNotEmpty()) {
            whileCondTempsStack.removeAt(whileCondTempsStack.lastIndex)
        } else {
            emptyList()
        }

        val whileStmt = WhileStatement(condition, block)

        condTemps.forEach { (temp, originalExpr) ->
            val tempOld = LocalVariable("compiler@gc_old_cond_${getUniqueName()}", temp.type)
            block.code.add(VariableStatement(LocalVariableExpression(temp), tempOld))
            block.code.add(LocalVariableAssignmentStatement(temp, originalExpr))
            getDecCall(temp.type, LocalVariableExpression(tempOld))?.let { block.code.add(it) }
        }

        val afterLoopCleanups = condTemps.mapNotNull { (temp, _) ->
            getDecCall(temp.type, LocalVariableExpression(temp))
        }

        return if (afterLoopCleanups.isNotEmpty()) {
            CompositeStatement(listOf(whileStmt) + afterLoopCleanups)
        } else {
            whileStmt
        }
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        if (variable.type.isRefCounted() && !variable.name.startsWith("compiler@")) {
            activeScopes.lastOrNull()?.add(variable)
            if (defaultValue != null) {
                addStatements(listOf(VariableStatement(defaultValue, variable)))
                if (defaultValue.isReturningPlusOne()) {
                    return null
                }
                return LocalVariableExpression(variable).asIncCall
            }
            return VariableStatement(NullExpression, variable)
        }
        return super.visitVariableStatement(defaultValue, variable)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
        if (!variable.type.isRefCounted()) return super.visitLocalVariableAssignmentStatement(variable, assignment)

        val stmts = mutableListOf<Statement>()
        val tempRhs = LocalVariable("compiler@gc_new_${variable.name}_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs))
        stmts.add(VariableStatement(LocalVariableExpression(variable), tempOld))
        stmts.add(LocalVariableAssignmentStatement(variable, LocalVariableExpression(tempRhs)))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(LocalVariableExpression(variable).asIncCall)
        }

        getDecCall(variable.type, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

        return CompositeStatement(stmts)
    }

    override fun visitVariableAssignmentStatement(
        target: Expression,
        variable: Parameter,
        struct: Struct,
        assignment: Expression
    ): Statement? {
        if (!variable.type.isRefCounted()) {
            return super.visitVariableAssignmentStatement(target, variable, struct, assignment)
        }

        val stmts = mutableListOf<Statement>()
        val (targetExpr, targetStmt) = if (!target.simple) {
            val tempTarget = LocalVariable("compiler@gc_target_${getUniqueName()}", target.getType())
            activeScopes.lastOrNull()?.add(tempTarget)
            val targetStmts = mutableListOf<Statement>(VariableStatement(target, tempTarget))
            if (!target.isReturningPlusOne()) {
                targetStmts.add(LocalVariableExpression(tempTarget).asIncCall)
            }
            Pair(LocalVariableExpression(tempTarget), CompositeStatement(targetStmts))
        } else {
            Pair(target, null)
        }

        targetStmt?.let { stmts.add(it) }

        val memberExpr = MemberExpression(targetExpr, variable, struct)
        val tempRhs = LocalVariable("compiler@gc_new_member_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_member_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs))
        stmts.add(VariableStatement(memberExpr, tempOld))
        stmts.add(VariableAssignmentStatement(targetExpr, variable, struct, LocalVariableExpression(tempRhs)))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(LocalVariableExpression(tempRhs).asIncCall)
        }
        getDecCall(variable.type, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

        return CompositeStatement(stmts)
    }

    override fun visitTLVariableAssignmentStatement(
        variable: TLVariable,
        sourceAST: ASTFile,
        assignment: Expression
    ): Statement? {
        if (!variable.type.isRefCounted()) {
            return super.visitTLVariableAssignmentStatement(variable, sourceAST, assignment)
        }
        if (assignment == IntLiteral(BigInteger.valueOf(-1L))) {
            return TLVariableAssignmentStatement(variable, sourceAST, assignment)
        }

        val stmts = mutableListOf<Statement>()
        val tempRhs = LocalVariable("compiler@gc_new_${variable.name}_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs))
        stmts.add(VariableStatement(VariableExpression(variable, sourceAST), tempOld))
        stmts.add(TLVariableAssignmentStatement(variable, sourceAST, LocalVariableExpression(tempRhs)))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(VariableExpression(variable, sourceAST).asIncCall)
        }
        getDecCall(variable.type, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

        return CompositeStatement(stmts)
    }

    override fun visitExpressionStatement(expression: Expression): Statement? {
        if (expression is CallExpression) {
            val listExpr = expression.arguments.getOrNull(0)
            val listType = listExpr?.getType()?.asNonNull() as? ListType

            if (listType != null && listType.elementType.isRefCounted()) {
                val elemType = listType.elementType

                if (expression.func == ListLib.replace && expression.arguments.size == 3) {
                    val itemExpr = expression.arguments[1]
                    val indexExpr = expression.arguments[2]

                    val stmts = mutableListOf<Statement>()
                    val tempOld = LocalVariable("compiler@gc_old_item_${getUniqueName()}", elemType)
                    val tempItem = LocalVariable("compiler@gc_new_item_${getUniqueName()}", elemType)

                    stmts.add(VariableStatement(itemExpr, tempItem))
                    stmts.add(VariableStatement(CallExpression(ListLib.itemAt, listOf(listExpr, indexExpr)), tempOld))
                    if (!itemExpr.isReturningPlusOne()) {
                        stmts.add(LocalVariableExpression(tempItem).asIncCall)
                    }
                    stmts.add(ExpressionStatement(CallExpression(ListLib.replace, listOf(listExpr, LocalVariableExpression(tempItem), indexExpr))))
                    getDecCall(elemType, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

                    return CompositeStatement(stmts)

                } else if (expression.func == ListLib.add && expression.arguments.size == 2) {
                    val itemExpr = expression.arguments[1]
                    val tempItem = LocalVariable("compiler@gc_add_item_${getUniqueName()}", elemType)

                    val stmts = mutableListOf<Statement>(
                        VariableStatement(itemExpr, tempItem)
                    )
                    if (!itemExpr.isReturningPlusOne()) {
                        stmts.add(LocalVariableExpression(tempItem).asIncCall)
                    }
                    stmts.add(ExpressionStatement(CallExpression(ListLib.add, listOf(listExpr, LocalVariableExpression(tempItem)))))

                    return CompositeStatement(stmts)

                } else if (expression.func == ListLib.remove && expression.arguments.size == 2) {
                    val indexExpr = expression.arguments[1]
                    val tempOld = LocalVariable("compiler@gc_old_item_${getUniqueName()}", elemType)

                    val stmts = mutableListOf<Statement>(
                        VariableStatement(CallExpression(ListLib.itemAt, listOf(listExpr, indexExpr)), tempOld),
                        ExpressionStatement(expression)
                    )
                    getDecCall(elemType, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

                    return CompositeStatement(stmts)

                } else if (expression.func == ListLib.clear && expression.arguments.size == 1) {
                    val stmts = mutableListOf<Statement>()

                    val iVar = LocalVariable("compiler@gc_clear_i_${getUniqueName()}", PrimitiveType.Integer)
                    val lenVar = LocalVariable("compiler@gc_clear_len_${getUniqueName()}", PrimitiveType.Integer)

                    stmts.add(VariableStatement(IntLiteral(BigInteger.ZERO), iVar))
                    stmts.add(VariableStatement(CallExpression(ListLib.length, listOf(listExpr)), lenVar))

                    val loopBody = CodeBlock().apply {
                        val tempItem = LocalVariable("compiler@gc_clear_item_${getUniqueName()}", elemType)
                        code.add(VariableStatement(CallExpression(ListLib.itemAt, listOf(listExpr, LocalVariableExpression(iVar))), tempItem))
                        getDecCall(elemType, LocalVariableExpression(tempItem))?.let { code.add(it) }
                        code.add(LocalVariableAssignmentStatement(
                            iVar,
                            BinaryExpression(LocalVariableExpression(iVar), BinaryOperator.ADD, IntLiteral(BigInteger.ONE))
                        ))
                    }

                    stmts.add(WhileStatement(
                        BinaryExpression(LocalVariableExpression(iVar), BinaryOperator.LESS_THAN, LocalVariableExpression(lenVar)),
                        loopBody
                    ))

                    stmts.add(ExpressionStatement(expression))
                    return CompositeStatement(stmts)
                }
            }
        }

        val type = expression.getType()
        if (type.isRefCounted() && expression.isReturningPlusOne()) {
            val tempRet = LocalVariable("compiler@gc_ignored_ret_${getUniqueName()}", type)
            val stmts = mutableListOf<Statement>()
            stmts.add(VariableStatement(expression, tempRet))
            getDecCall(type, LocalVariableExpression(tempRet))?.let { stmts.add(it) }
            return CompositeStatement(stmts)
        }

        return super.visitExpressionStatement(expression)
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val isMainBlock = block === currentBlock
        activeScopes.add(mutableSetOf())

        if (isMainBlock) {
            currentFunction?.parameters?.forEach { param ->
                if (param.type.isRefCounted()) {
                    val paramLocal = LocalVariable(param.name, param.type)
                    addStatements(listOf(ParameterExpression(param).asIncCall))
                    activeScopes.last().add(paramLocal)
                }
            }
        }

        val visited = super.visitCodeBlock(block)

        val blockLocals = activeScopes.removeAt(activeScopes.lastIndex)
        for (local in blockLocals) {
            getDecCall(local.type, LocalVariableExpression(local))?.let {
                visited.code.add(it)
            }
        }
        return visited
    }

    override fun visitReturnStatement(expression: Expression?): Statement {
        val stmts = mutableListOf<Statement>()

        var returnExpr = expression
        if (expression != null && expression.getType().isRefCounted()) {
            if (currentFunction?.userAccessible == false && currentFunction.name.startsWith("new")) {

            } else {
                if (expression !is LocalVariableExpression) {
                    val tempRet = LocalVariable("compiler@gc_ret_${getUniqueName()}", expression.getType())
                    stmts.add(VariableStatement(expression, tempRet))
                    returnExpr = LocalVariableExpression(tempRet)
                }
                if (!expression.isReturningPlusOne()) {
                    stmts.add(returnExpr.asIncCall)
                }
            }
        }

        for (condTempsLevel in whileCondTempsStack) {
            for ((temp, _) in condTempsLevel) {
                getDecCall(temp.type, LocalVariableExpression(temp))?.let { stmts.add(it) }
            }
        }

        for (scope in activeScopes.reversed()) {
            for (local in scope) {
                getDecCall(local.type, LocalVariableExpression(local))?.let { stmts.add(it) }
            }
        }

        stmts.add(ReturnStatement(returnExpr))
        return CompositeStatement(stmts)
    }

    override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
        val processedArgs = args.map { arg ->
            val type = arg.getType()
            if (type.isRefCounted() && arg.isReturningPlusOne() && arg !is LocalVariableExpression) {
                val temp = LocalVariable("compiler@gc_arg_${getUniqueName()}", type)
                addStatements(listOf(VariableStatement(arg, temp)))
                if (isInWhileCondition && whileCondTempsStack.isNotEmpty()) {
                    whileCondTempsStack.last().add(temp to arg)
                } else {
                    activeScopes.lastOrNull()?.add(temp)
                }
                LocalVariableExpression(temp)
            } else {
                arg
            }
        }
        return super.visitCallExpression(func, processedArgs)
    }

    override fun visitDynamicCallExpression(
        function: Expression,
        args: List<Expression>,
        type: FunctionType
    ): Expression {
        val processedArgs = args.map { arg ->
            val argType = arg.getType()
            if (argType.isRefCounted() && arg.isReturningPlusOne() && arg !is LocalVariableExpression) {
                val temp = LocalVariable("compiler@gc_arg_${getUniqueName()}", argType)
                addStatements(listOf(VariableStatement(arg, temp)))
                if (isInWhileCondition && whileCondTempsStack.isNotEmpty()) {
                    whileCondTempsStack.last().add(temp to arg)
                } else {
                    activeScopes.lastOrNull()?.add(temp)
                }
                LocalVariableExpression(temp)
            } else {
                arg
            }
        }
        return super.visitDynamicCallExpression(function, processedArgs, type)
    }

    private fun Type.isRefCounted(): Boolean {
        val nonNull = this.asNonNull()
        if (nonNull is ListType) return true
        if (nonNull is SimpleType) {
            return compilationContext.asts.values.flatMap { it.structs }.any { it.type.asNonNull() == nonNull }
        }
        return false
    }

    private fun getDecCall(type: Type, expr: Expression): Statement? {
        return when (val targetType = type.asNonNull()) {
            is SimpleType -> {
                val struct = targetType.sourceAST.structs.find { it.name == targetType.name } ?: return null
                val decFunc = structDecs[struct] ?: return null
                ExpressionStatement(CallExpression(decFunc, listOf(expr)))
            }
            is ListType -> {
                val decFunc = generateDecList(targetType)
                ExpressionStatement(CallExpression(decFunc, listOf(expr)))
            }
            else -> null
        }
    }

    private val Expression.asIncCall: Statement
        get() = ExpressionStatement(CallExpression(inc, listOf(this)))

    private fun Expression.getType(): Type {
        return ExpressionTypes.getExpressionType(this@RefCountVisitor.compilationContext, this)
    }

    private fun Expression.isReturningPlusOne(): Boolean {
        if (this is CallExpression) {
            if (this.func == ListLib.newList) return true
            if (this.func.sourceAST.path == "string" && this.func.name == "split") return true

            return this.func !is StandardLibASTFunction && this.func !is InlineStandardLibFunction
        }
        if (this is DynamicCallExpression) {
            return true
        }
        if (this is NonNullAssertExpression) {
            return this.expression.isReturningPlusOne()
        }
        return false
    }

    private val Expression.simple: Boolean
        get() = when(this) {
            is BinaryExpression -> this.left.simple && this.right.simple
            is ConcatExpression -> this.left.simple && this.right.simple
            is UnaryExpression -> this.expression.simple
            is MemberExpression -> this.expression.simple
            is NonNullAssertExpression -> this.expression.simple

            is LocalVariableExpression -> true
            is ParameterExpression -> true
            is TemporaryLocalVariableIndexExpression -> true
            is TemporaryStackNameExpression -> true
            is TemporaryStackSizeExpression -> true
            is VariableExpression -> true
            is Literal -> true

            is TemporaryScratchExpr -> false
            is WhenExpression -> false
            is DynamicCallExpression -> false
            is CallExpression -> false
            is TemporaryHeapGetExpression -> false
        }
}