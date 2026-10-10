package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.simple
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ArrayLib
import org.antlr.v4.kotlinruntime.ast.Position

class RefCountVisitor(
    val structDecs: Map<Struct, Function>,
    val sealedDecs: Map<SealedEnum, Function>,
    val inc: Function,
    val generateDecList: (ArrayType) -> Function,
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

    override fun visitWhileStatement(condition: Expression, block: CodeBlock, position: Position?): Statement {
        val condTemps = if (whileCondTempsStack.isNotEmpty()) {
            whileCondTempsStack.removeAt(whileCondTempsStack.lastIndex)
        } else {
            emptyList()
        }

        val whileStmt = WhileStatement(condition, block, position)

        condTemps.forEach { (temp, originalExpr) ->
            val tempOld = LocalVariable("compiler@gc_old_cond_${getUniqueName()}", temp.type)
            block.code.add(VariableStatement(LocalVariableExpression(temp, null), tempOld, null))
            block.code.add(LocalVariableAssignmentStatement(temp, originalExpr, null))
            getDecCall(temp.type, LocalVariableExpression(tempOld, null))?.let { block.code.add(it) }
        }

        val afterLoopCleanups = condTemps.mapNotNull { (temp, _) ->
            getDecCall(temp.type, LocalVariableExpression(temp, null))
        }

        return if (afterLoopCleanups.isNotEmpty()) {
            CompositeStatement(listOf(whileStmt) + afterLoopCleanups, position)
        } else {
            whileStmt
        }
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable, position: Position?): Statement? {
        if (variable.type.isRefCounted() && !variable.name.startsWith("compiler@")) {
            activeScopes.lastOrNull()?.add(variable)
            if (defaultValue != null) {
                addStatements(listOf(VariableStatement(defaultValue, variable, position)))
                if (!defaultValue.isReturningPlusOne()) {
                    addStatements(listOf(LocalVariableExpression(variable, null).asIncCall))
                }
                return null
            }
            return super.visitVariableStatement(null, variable, position)
        }
        return super.visitVariableStatement(defaultValue, variable, position)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression, position: Position?): Statement? {
        if (!variable.type.isRefCounted()) return super.visitLocalVariableAssignmentStatement(variable, assignment, position)

        val stmts = mutableListOf<Statement>()
        val tempRhs = LocalVariable("compiler@gc_new_${variable.name}_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs, null))
        stmts.add(VariableStatement(LocalVariableExpression(variable, null), tempOld, null))
        stmts.add(LocalVariableAssignmentStatement(variable, LocalVariableExpression(tempRhs, null), null))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(LocalVariableExpression(variable, null).asIncCall)
        }

        getDecCall(variable.type, LocalVariableExpression(tempOld, null))?.let { stmts.add(it) }

        return CompositeStatement(stmts, position)
    }

    override fun visitVariableAssignmentStatement(
        target: Expression,
        variable: Parameter,
        struct: Struct,
        assignment: Expression,
        position: Position?
    ): Statement? {
        if (!variable.type.isRefCounted()) {
            return super.visitVariableAssignmentStatement(target, variable, struct, assignment, position)
        }

        val stmts = mutableListOf<Statement>()
        val (targetExpr, targetStmt) = if (!target.simple) {
            val tempTarget = LocalVariable("compiler@gc_target_${getUniqueName()}", target.getType())
            activeScopes.lastOrNull()?.add(tempTarget)
            val targetStmts = mutableListOf<Statement>(VariableStatement(target, tempTarget, null))
            if (!target.isReturningPlusOne()) {
                targetStmts.add(LocalVariableExpression(tempTarget, null).asIncCall)
            }
            Pair(LocalVariableExpression(tempTarget, null), CompositeStatement(targetStmts, null))
        } else {
            Pair(target, null)
        }

        targetStmt?.let { stmts.add(it) }

        val memberExpr = MemberExpression(targetExpr, variable, struct, null)
        val tempRhs = LocalVariable("compiler@gc_new_member_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_member_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs, null))
        stmts.add(VariableStatement(memberExpr, tempOld, null))
        stmts.add(VariableAssignmentStatement(targetExpr, variable, struct, LocalVariableExpression(tempRhs, null), null))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(LocalVariableExpression(tempRhs, null).asIncCall)
        }
        getDecCall(variable.type, LocalVariableExpression(tempOld, null))?.let { stmts.add(it) }

        return CompositeStatement(stmts, position)
    }

    override fun visitTLVariableAssignmentStatement(
        variable: TLVariable,
        sourceAST: ASTFile,
        assignment: Expression,
        position: Position?
    ): Statement? {
        if (!variable.type.isRefCounted()) {
            return super.visitTLVariableAssignmentStatement(variable, sourceAST, assignment, position)
        }
        if (assignment is StringLiteral && assignment.value == "null") {
            return TLVariableAssignmentStatement(variable, sourceAST, assignment, position)
        }

        val stmts = mutableListOf<Statement>()
        val tempRhs = LocalVariable("compiler@gc_new_${variable.name}_${getUniqueName()}", variable.type)
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}_${getUniqueName()}", variable.type)

        stmts.add(VariableStatement(assignment, tempRhs, null))
        stmts.add(VariableStatement(VariableExpression(variable, sourceAST, null), tempOld, null))
        stmts.add(TLVariableAssignmentStatement(variable, sourceAST, LocalVariableExpression(tempRhs, null), null))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(VariableExpression(variable, sourceAST, null).asIncCall)
        }
        getDecCall(variable.type, LocalVariableExpression(tempOld, null))?.let { stmts.add(it) }

        return CompositeStatement(stmts, position)
    }

    override fun visitExpressionStatement(expression: Expression, position: Position?): Statement? {
        if (expression is CallExpression &&
            expression.func == ArrayLib.replace &&
            expression.arguments.size == 3
        ) {
            val listExpr = expression.arguments[0]
            val arrayType = listExpr.getType().asNonNull() as? ArrayType

            if (arrayType != null && arrayType.elementType.isRefCounted()) {
                val elemType = arrayType.elementType
                val itemExpr = expression.arguments[1]
                val indexExpr = expression.arguments[2]

                val stmts = mutableListOf<Statement>()

                var spilledList: LocalVariable? = null
                val effectiveListExpr = if (!listExpr.simple) {
                    val tempList = LocalVariable("compiler@gc_list_${getUniqueName()}", listExpr.getType())
                    spilledList = tempList
                    stmts.add(VariableStatement(listExpr, tempList, null))
                    if (!listExpr.isReturningPlusOne()) {
                        stmts.add(LocalVariableExpression(tempList, null).asIncCall)
                    }
                    LocalVariableExpression(tempList, null)
                } else {
                    listExpr
                }
                val effectiveIndexExpr = if (!indexExpr.simple) {
                    val tempIndex = LocalVariable("compiler@gc_index_${getUniqueName()}", indexExpr.getType())
                    stmts.add(VariableStatement(indexExpr, tempIndex, null))
                    LocalVariableExpression(tempIndex, null)
                } else {
                    indexExpr
                }

                val tempOld = LocalVariable("compiler@gc_old_item_${getUniqueName()}", elemType)
                val tempItem = LocalVariable("compiler@gc_new_item_${getUniqueName()}", elemType)

                stmts.add(VariableStatement(itemExpr, tempItem, null))
                stmts.add(VariableStatement(CallExpression(ArrayLib.itemAt, listOf(effectiveListExpr, effectiveIndexExpr), null), tempOld, null))
                if (!itemExpr.isReturningPlusOne()) {
                    stmts.add(LocalVariableExpression(tempItem, null).asIncCall)
                }
                stmts.add(ExpressionStatement(CallExpression(ArrayLib.replace, listOf(effectiveListExpr, LocalVariableExpression(tempItem, null), effectiveIndexExpr), null), null))
                getDecCall(elemType, LocalVariableExpression(tempOld, null))?.let { stmts.add(it) }

                spilledList?.let { tempList ->
                    getDecCall(tempList.type, LocalVariableExpression(tempList, null))?.let { stmts.add(it) }
                }

                return CompositeStatement(stmts, position)
            }
        }

        val type = expression.getType()
        if (type.isRefCounted() && expression.isReturningPlusOne()) {
            val tempRet = LocalVariable("compiler@gc_ignored_ret_${getUniqueName()}", type)
            val stmts = mutableListOf<Statement>()
            stmts.add(VariableStatement(expression, tempRet, null))
            getDecCall(type, LocalVariableExpression(tempRet, null))?.let { stmts.add(it) }
            return CompositeStatement(stmts, position)
        }

        return super.visitExpressionStatement(expression, position)
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val isMainBlock = block === currentFunction?.code
        activeScopes.add(mutableSetOf())

        if (isMainBlock) {
            val paramInits = currentFunction.parameters.mapNotNull { param ->
                if (param.type.isRefCounted()) {
                    val paramLocal = LocalVariable(param.name, param.type)
                    VariableStatement(ParameterExpression(param, null), paramLocal, null)
                } else null
            }
            if (paramInits.isNotEmpty()) {
                block.code.addAll(0, paramInits)
            }
        }

        val visited = super.visitCodeBlock(block)

        val blockLocals = activeScopes.removeAt(activeScopes.lastIndex)
        for (local in blockLocals) {
            getDecCall(local.type, LocalVariableExpression(local, null))?.let {
                visited.code.add(it)
            }
        }
        return visited
    }

    override fun visitParameterExpression(parameter: Parameter, position: Position?): Expression {
        if (parameter.type.isRefCounted()) {
            val local = activeScopes.flatten().find { it.name == parameter.name }
            if (local != null) {
                return LocalVariableExpression(local, position)
            }
        }
        return super.visitParameterExpression(parameter, position)
    }

    override fun visitReturnStatement(expression: Expression?, position: Position?): Statement {
        val stmts = mutableListOf<Statement>()

        var returnExpr = expression
        val transfersConstructorReference = currentFunction?.userAccessible == false &&
                currentFunction.name.startsWith("new") &&
                expression is LocalVariableExpression
        if (expression != null && expression.getType().isRefCounted()) {
            if (!(currentFunction?.userAccessible == false && currentFunction.name.startsWith("new"))) {
                if (expression !is LocalVariableExpression) {
                    val tempRet = LocalVariable("compiler@gc_ret_${getUniqueName()}", expression.getType())
                    stmts.add(VariableStatement(expression, tempRet, null))
                    returnExpr = LocalVariableExpression(tempRet, null)
                }
                if (!expression.isReturningPlusOne()) {
                    stmts.add(returnExpr.asIncCall)
                }
            }
        }
        else if (expression != null &&
            expression !is LocalVariableExpression &&
            expression !is NullExpression &&
            currentFunction?.returnType != PrimitiveType.Void
        ) {
            val tempRet = LocalVariable("compiler@gc_ret_${getUniqueName()}", expression.getType())
            stmts.add(VariableStatement(expression, tempRet, null))
            returnExpr = LocalVariableExpression(tempRet, null)
        }

        for (condTempsLevel in whileCondTempsStack) {
            for ((temp, _) in condTempsLevel) {
                getDecCall(temp.type, LocalVariableExpression(temp, null))?.let { stmts.add(it) }
            }
        }

        for (scope in activeScopes.reversed()) {
            for (local in scope) {
                if (!transfersConstructorReference || local != (returnExpr as? LocalVariableExpression)?.variable) {
                    getDecCall(local.type, LocalVariableExpression(local, null))?.let { stmts.add(it) }
                }
            }
        }

        stmts.add(ReturnStatement(returnExpr, position))
        return CompositeStatement(stmts, position)
    }

    override fun visitCallExpression(func: Function, args: List<Expression>, position: Position?): Expression {
        val isConstructor = func.sourceAST == StandardLibASTGenerator.memLib && func.name.startsWith("new")
        val processedArgs = args.map { arg ->
            val type = arg.getType()
            if (type.isRefCounted() && arg.isReturningPlusOne() && arg !is LocalVariableExpression && !isConstructor) {
                val temp = LocalVariable("compiler@gc_arg_${getUniqueName()}", type)
                addStatements(listOf(VariableStatement(arg, temp, null)))
                if (isInWhileCondition && whileCondTempsStack.isNotEmpty()) {
                    whileCondTempsStack.last().add(temp to arg)
                } else {
                    activeScopes.lastOrNull()?.add(temp)
                }
                LocalVariableExpression(temp, null)
            } else if (isConstructor && type.isRefCounted() && !arg.isReturningPlusOne() && arg !is LocalVariableExpression) {
                val temp = LocalVariable("compiler@gc_ctor_arg_${getUniqueName()}", type)
                addStatements(listOf(VariableStatement(arg, temp, null)))
                addStatements(listOf(LocalVariableExpression(temp, null).asIncCall))
                LocalVariableExpression(temp, null)
            } else if (isConstructor && type.isRefCounted() && !arg.isReturningPlusOne() && arg is LocalVariableExpression) {
                addStatements(listOf(arg.asIncCall))
                arg
            } else {
                arg
            }
        }
        return super.visitCallExpression(func, processedArgs, position)
    }

    override fun visitDynamicCallExpression(
        function: Expression,
        args: List<Expression>,
        type: FunctionType,
        position: Position?
    ): Expression {
        val processedArgs = args.map { arg ->
            val argType = arg.getType()
            if (argType.isRefCounted() && arg.isReturningPlusOne() && arg !is LocalVariableExpression) {
                val temp = LocalVariable("compiler@gc_arg_${getUniqueName()}", argType)
                addStatements(listOf(VariableStatement(arg, temp, null)))
                if (isInWhileCondition && whileCondTempsStack.isNotEmpty()) {
                    whileCondTempsStack.last().add(temp to arg)
                } else {
                    activeScopes.lastOrNull()?.add(temp)
                }
                LocalVariableExpression(temp, null)
            } else {
                arg
            }
        }
        return super.visitDynamicCallExpression(function, processedArgs, type, position)
    }

    private fun Type.isRefCounted(): Boolean {
        val nonNull = this.asNonNull()
        if (nonNull is ArrayType) return true
        if (nonNull is SealedEnumType) return true
        if (nonNull is SimpleType) {
            return (compilationContext.asts.values.flatMap { it.structs } +
                    StandardLibASTGenerator.compilerLib.structs +
                    StandardLibASTGenerator.lambdaLib.structs +
                    StandardLibASTGenerator.listPoolLib.structs).any { it.type.asNonNull() == nonNull }
        }
        return false
    }

    private fun getDecCall(type: Type, expr: Expression): Statement? {
        return when (val targetType = type.asNonNull()) {
            is SimpleType -> {
                val struct = targetType.sourceAST.structs.find { it.name == targetType.name } ?: return null
                val decFunc = structDecs[struct] ?: return null
                ExpressionStatement(CallExpression(decFunc, listOf(expr), null), null)
            }
            is ArrayType -> {
                val decFunc = generateDecList(targetType)
                ExpressionStatement(CallExpression(decFunc, listOf(expr), null), null)
            }
            is SealedEnumType -> {
                val decFunc = sealedDecs.entries.find { it.key.type == targetType }?.value ?: return null
                ExpressionStatement(CallExpression(decFunc, listOf(expr), null), null)
            }
            else -> null
        }
    }

    private val Expression.asIncCall: Statement
        get() = ExpressionStatement(CallExpression(inc, listOf(this), null), null)

    private fun Expression.getType(): Type {
        return ExpressionTypes.getExpressionType(this)
    }

    private fun Expression.isReturningPlusOne(): Boolean {
        if (this is CallExpression) {
            if (this.func == ArrayLib.newArray) return true

            return this.func !is StandardLibASTFunction && this.func !is InlineStandardLibFunction
        }
        if (this is DynamicCallExpression) {
            return true
        }
        if (this is NonNullAssertExpression) {
            return this.expression.isReturningPlusOne() &&
                this.expression !is LocalVariableExpression &&
                this.expression !is ParameterExpression
        }
        return false
    }
}