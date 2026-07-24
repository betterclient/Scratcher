package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.std.lib.ListLib
import java.math.BigInteger

class RefCountVisitor(
    val structDecs: Map<Struct, Function>,
    val inc: Function,
    val generateDecList: (ListType) -> Function,
    val compilationContext: CompilationContext
) : ASTVisitor() {

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        if (variable.type.isRefCounted() && !variable.name.startsWith("compiler@")) {
            //auto a = Struct()
            //inc(a)
            activeScopes.lastOrNull()?.add(variable)
            if (defaultValue != null) {
                addStatements(listOf(VariableStatement(defaultValue, variable)))
                if (defaultValue.isReturningPlusOne()) {
                    return null
                }
                return LocalVariableExpression(variable).asIncCall
            }
        }
        return super.visitVariableStatement(defaultValue, variable)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
        if (!variable.type.isRefCounted()) return super.visitLocalVariableAssignmentStatement(variable, assignment)

        val stmts = mutableListOf<Statement>()
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}", variable.type)

        stmts.add(VariableStatement(LocalVariableExpression(variable), tempOld))
        stmts.add(LocalVariableAssignmentStatement(variable, assignment))
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
            val tempTarget = LocalVariable("compiler@gc_target", target.getType())
            Pair(LocalVariableExpression(tempTarget), VariableStatement(target, tempTarget))
        } else {
            Pair(target, null)
        }

        targetStmt?.let { stmts.add(it) }

        val memberExpr = MemberExpression(targetExpr, variable, struct)
        val tempOld = LocalVariable("compiler@gc_old_member", variable.type)

        stmts.add(VariableStatement(memberExpr, tempOld))
        stmts.add(VariableAssignmentStatement(targetExpr, variable, struct, assignment))
        if (!assignment.isReturningPlusOne()) {
            stmts.add(memberExpr.asIncCall)
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

        val stmts = mutableListOf<Statement>()
        val tempOld = LocalVariable("compiler@gc_old_${variable.name}", variable.type)

        stmts.add(VariableStatement(VariableExpression(variable, sourceAST), tempOld))
        stmts.add(TLVariableAssignmentStatement(variable, sourceAST, assignment))
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
                    stmts.add(LocalVariableExpression(tempItem).asIncCall)
                    stmts.add(ExpressionStatement(CallExpression(ListLib.replace, listOf(listExpr, LocalVariableExpression(tempItem), indexExpr))))
                    getDecCall(elemType, LocalVariableExpression(tempOld))?.let { stmts.add(it) }

                    return CompositeStatement(stmts)

                } else if (expression.func == ListLib.add && expression.arguments.size == 2) {
                    val itemExpr = expression.arguments[1]
                    val tempItem = LocalVariable("compiler@gc_add_item_${getUniqueName()}", elemType)

                    return CompositeStatement(listOf(
                        VariableStatement(itemExpr, tempItem),
                        LocalVariableExpression(tempItem).asIncCall,
                        ExpressionStatement(CallExpression(ListLib.add, listOf(listExpr, LocalVariableExpression(tempItem))))
                    ))

                } else if (expression.func == ListLib.remove && expression.arguments.size == 2) {
                    val indexExpr = expression.arguments[1]

                    val tempOld = LocalVariable("compiler@gc_old_item_${getUniqueName()}", elemType)

                    return CompositeStatement(listOf(
                        VariableStatement(CallExpression(ListLib.itemAt, listOf(listExpr, indexExpr)), tempOld),
                        ExpressionStatement(expression),
                        getDecCall(elemType, LocalVariableExpression(tempOld))!!
                    ))

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

    private val activeScopes = mutableListOf<MutableSet<LocalVariable>>()
    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        activeScopes.add(mutableSetOf())
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
            if (expression !is LocalVariableExpression) {
                val tempRet = LocalVariable("compiler@gc_ret", expression.getType())
                stmts.add(VariableStatement(expression, tempRet))
                returnExpr = LocalVariableExpression(tempRet)
            }
            stmts.add(returnExpr.asIncCall)
        }

        for (scope in activeScopes.reversed()) {
            for (local in scope) {
                getDecCall(local.type, LocalVariableExpression(local))?.let { stmts.add(it) }
            }
        }

        stmts.add(ReturnStatement(returnExpr))
        return CompositeStatement(stmts)
    }

    private fun Type.isRefCounted(): Boolean {
        val nonNull = this.asNonNull()
        if (nonNull is ListType) return true
        if (nonNull is SimpleType) {
            return compilationContext.asts.values.flatMap { it.structs }.any { it.type.asNonNull() == nonNull } //enums are technically SimpleType
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
        get() {
            return ExpressionStatement(CallExpression(inc, listOf(this)))
        }

    private fun Expression.getType(): Type {
        return ExpressionTypes.getExpressionType(this@RefCountVisitor.compilationContext, this)
    }

    private fun Expression.isReturningPlusOne(): Boolean {
        if (this is CallExpression) {
            return this.func !is StandardLibASTFunction && this.func !is InlineStandardLibFunction
        }
        if (this is DynamicCallExpression) {
            return true
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