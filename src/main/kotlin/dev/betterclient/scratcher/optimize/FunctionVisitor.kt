package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigDecimal
import java.math.BigInteger

enum class VisitMode {
    NONE,
    READ_ONLY,
    ALL,
    COPY
}

abstract class ASTVisitor : BaseExpressionVisitor, BaseStatementVisitor {
    open fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.ALL

    open fun afterVisit(expression: Expression, result: Expression) {}

    val statementBuffer = mutableListOf<MutableList<Statement>>()
    fun addStatements(list: List<Statement>) {
        val buffer = statementBuffer.lastOrNull()
        if (buffer != null) {
            buffer.addAll(list)
        } else {
            currentBlock?.code?.addAll(list)
        }
    }

    fun visitStatementWithBuffer(statement: Statement): List<Statement> {
        val buffer = mutableListOf<Statement>()
        statementBuffer.add(buffer)

        val visited = visit(statement)

        statementBuffer.removeAt(statementBuffer.lastIndex)

        val result = mutableListOf<Statement>()
        result.addAll(buffer)
        if (visited != null) {
            result.addAll(flattenStatement(visited))
        }
        return result
    }

    var currentBlock: CodeBlock? = null

    open fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val mode = shouldVisitCodeBlock(block)
        return when(mode) {
            VisitMode.NONE -> block
            VisitMode.READ_ONLY -> {
                val before = currentBlock
                currentBlock = block
                block.code.forEach { visitStatementWithBuffer(it) }
                currentBlock = before
                block
            }
            VisitMode.ALL -> {
                val before = currentBlock
                currentBlock = block
                val updatedCode = block.code.flatMap { visitStatementWithBuffer(it) }
                block.code.clear()
                block.code.addAll(updatedCode)
                currentBlock = before
                block
            }
            VisitMode.COPY -> {
                val before = currentBlock
                val out = CodeBlock()
                currentBlock = out
                out.code.addAll(block.code.flatMap { visitStatementWithBuffer(it) })
                currentBlock = before
                out
            }
        }
    }

    private fun flattenStatement(statement: Statement?): List<Statement> {
        return when (statement) {
            null -> emptyList()
            is CompositeStatement -> statement.statements.flatMap { flattenStatement(it) }
            else -> listOf(statement)
        }
    }
}

interface BaseStatementVisitor {
    fun visitExpressionStatement(expression: Expression, position: Position?): Statement? = ExpressionStatement(expression, position)
    fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable, position: Position?): Statement? = VariableStatement(defaultValue, variable, position)
    fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression, position: Position?): Statement? = LocalVariableAssignmentStatement(variable, assignment, position)
    fun visitVariableAssignmentStatement(target: Expression, variable: Parameter, struct: Struct, assignment: Expression, position: Position?): Statement? = VariableAssignmentStatement(target, variable, struct, assignment, position)
    fun visitTLVariableAssignmentStatement(variable: TLVariable, sourceAST: ASTFile, assignment: Expression, position: Position?): Statement? = TLVariableAssignmentStatement(variable, sourceAST, assignment, position)
    fun visitReturnStatement(expression: Expression?, position: Position?): Statement? = ReturnStatement(expression, position)
    fun visitIfStatement(condition: Expression, thenBlock: CodeBlock, position: Position?): Statement? = IfStatement(condition, thenBlock, position)
    fun visitIfElseStatement(condition: Expression, thenBlock: CodeBlock, elseBlock: CodeBlock, position: Position?): Statement? = IfElseStatement(condition, thenBlock, elseBlock, position)
    fun visitWhileStatement(condition: Expression, block: CodeBlock, position: Position?): Statement? = WhileStatement(condition, block, position)
    fun visitRepeatStatement(amount: Expression, block: CodeBlock, position: Position?): Statement? = RepeatStatement(amount, block, position)
    fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>, position: Position?): Statement? = TemporaryCallStatement(func, args, position)
    fun visitTemporaryHeapSetStatement(index: Expression, data: Expression, position: Position?): Statement? = TemporaryHeapSetStatement(index, data, position)
    fun visitTemporaryScratchStmt(inputExprs: List<Expression>, stmt: (List<ScratchExpression>) -> List<ScratchStatement>, position: Position?): Statement? = TemporaryScratchStmt(inputExprs, stmt, position)
    fun visitBreakStatement(position: Position?): Statement? = BreakStatement(position)
    fun visitContinueStatement(position: Position?): Statement? = ContinueStatement(position)
    fun visitStaticListSetStatement(list: TLStaticList, index: Expression, item: Expression, position: Position?): Statement? = StaticListSetStatement(list, index, item, position)
    fun visitStaticListAddStatement(list: TLStaticList, item: Expression, position: Position?): Statement? = StaticListAddStatement(list, item, position)
    fun visitStaticListRemoveStatement(list: TLStaticList, index: Expression, position: Position?): Statement? = StaticListRemoveStatement(list, index, position)
    fun visitStaticListClearStatement(list: TLStaticList, position: Position?): Statement? = StaticListClearStatement(list, position)
    fun visitStaticListInsertStatement(list: TLStaticList, index: Expression, value: Expression, position: Position?): Statement? = StaticListInsertStatement(list, index, value, position)

    fun visitStatement(statement: Statement) {}
}

interface BaseExpressionVisitor {
    fun visitBinaryExpression(left: Expression, right: Expression, operator: BinaryOperator, position: Position?): Expression = BinaryExpression(left, operator, right, position)
    fun visitCallExpression(func: Function, args: List<Expression>, position: Position?): Expression = CallExpression(func, args, position)
    fun visitIntLiteral(value: BigInteger, position: Position?): Expression = IntLiteral(value, position)
    fun visitLocalVariableExpression(variable: LocalVariable, position: Position?): Expression = LocalVariableExpression(variable, position)
    fun visitMemberExpression(expression: Expression, member: Parameter, struct: Struct, position: Position?): Expression = MemberExpression(expression, member, struct, position)
    fun visitConcatExpression(left: Expression, right: Expression, position: Position?): Expression = ConcatExpression(left, right, position)
    fun visitUnaryExpression(operator: UnaryOperator, expression: Expression, position: Position?): Expression = UnaryExpression(operator, expression, position)
    fun visitNonNullAssertExpression(expression: Expression, position: Position?): Expression = NonNullAssertExpression(expression, position)
    fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression, position: Position?): Expression = NonNullOrElseExpression(operand1, (this as ASTVisitor).visit(operand2), position)
    fun visitSafeDotExpression(target: Expression, member: Parameter, struct: Struct, position: Position?): Expression = SafeDotExpression(struct, target, member, position)
    fun visitParameterExpression(parameter: Parameter, position: Position?): Expression = ParameterExpression(parameter, position)
    fun visitVariableExpression(variable: TLVariable, sourceAST: ASTFile, position: Position?): Expression = VariableExpression(variable, sourceAST, position)
    fun visitFloatLiteral(value: BigDecimal, position: Position?): Expression = FloatLiteral(value, position)
    fun visitBooleanLiteral(value: Boolean, position: Position?): Expression = BooleanLiteral(value, position)
    fun visitStringLiteral(value: String, position: Position?): Expression = StringLiteral(value, position)
    fun visitCharLiteral(value: Char, position: Position?): Expression = CharLiteral(value, position)
    fun visitNullExpression(): Expression = NullExpression
    fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable, position: Position?): Expression = TemporaryLocalVariableIndexExpression(variable, position)
    fun visitTemporaryHeapGetExpression(index: Expression, type: Type? = null, position: Position?): Expression = TemporaryHeapGetExpression(index, type, position)
    fun visitTemporaryScratchExpr(inputExprs: List<Expression>, expression: (List<ScratchExpression>) -> ScratchExpression, position: Position?): Expression = TemporaryScratchExpr(inputExprs, expression, position)
    fun visitEnumLiteral(enum: ASTEnum, value: String, ordinal: Int, position: Position?): Expression = EnumLiteral(enum, value, ordinal, position)
    fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?, position: Position?): Expression = WhenExpression(subject, branches, position)
    fun visitTemporaryStackSizeExpression(func: Function, includeGCHeader: Boolean, position: Position?): Expression = TemporaryStackSizeExpression(func, includeGCHeader, position)
    fun visitTemporaryStackNameExpression(func: Function, position: Position?): Expression = TemporaryStackNameExpression(func, position)
    fun visitFunctionLiteral(func: Function, position: Position?): Expression = FunctionLiteral(func, position)
    fun visitDynamicCallExpression(function: Expression, args: List<Expression>, type: FunctionType, position: Position?): Expression = DynamicCallExpression(type, function, args, position)
    fun visitTypeLiteral(type: Type, position: Position?): Expression = TypeLiteral(type, position)
    fun visitStatementExpression(statements: List<Statement>, expression: Expression, position: Position?): Expression = StatementExpression(statements, expression, position)
    fun visitLambdaExpression(block: CodeBlock, arguments: List<LocalVariable>, captured: MutableSet<LocalVariable>, position: Position?): Expression = LambdaExpression(arguments, (this as ASTVisitor).visitCodeBlock(block), captured, position)
    fun visitCheckSealedEnumTypeExpression(expr: Expression, targetVariant: Struct, sealedEnum: SealedEnum, tag: Int, position: Position?): Expression = CheckSealedEnumTypeExpression((this as ASTVisitor).visit(expr), targetVariant, sealedEnum, tag, position)
    fun visitSealedEnumCastExpression(expr: Expression, targetVariant: Struct, sealedEnum: SealedEnum, tag: Int, safe: Boolean, position: Position?): Expression = SealedEnumCastExpression((this as ASTVisitor).visit(expr), targetVariant, sealedEnum, tag, safe, position)
    fun visitSealedEnumConstructionExpression(sealedEnum: SealedEnum, targetVariant: Struct, args: List<Expression>, position: Position?): Expression = SealedEnumConstructionExpression(sealedEnum, targetVariant, args.map { (this as ASTVisitor).visit(it) }, position)
    fun visitStaticListExpression(list: TLStaticList, position: Position?): Expression = StaticListExpression(list, position)
    fun visitStaticListItemExpression(list: TLStaticList, item: Expression, position: Position?): Expression = StaticListItemExpression(list, item, position)
    fun visitStaticListLengthExpression(list: TLStaticList, position: Position?): Expression = StaticListLengthExpression(list, position)
    fun visitStaticListContainsExpression(list: TLStaticList, item: Expression, position: Position?): Expression = StaticListContainsExpression(list, item, position)
    fun visitStaticListItemIndexExpression(list: TLStaticList, item: Expression, position: Position?): Expression = StaticListItemIndexExpression(list, item, position)
    fun visitErrorExpression(): Expression = ErrorExpression

    fun visitExpr(expression: Expression) {}
}

object EmptyVisitor : ASTVisitor()

fun visit(
    func: Function,
    visitor: ASTVisitor = EmptyVisitor
) {
    val out = visitor.visitCodeBlock(func.code)
    if (out == func.code) return
    func.code.code.clear()
    func.code.code.addAll(out.code)
}

fun visitCopy(
    func: Function,
    visitor: ASTVisitor = EmptyVisitor
): CodeBlock {
    return visitor.visitCodeBlock(func.code)
}

fun visit(
    block: CodeBlock,
    visitor: ASTVisitor = EmptyVisitor
) {
    visitor.visitCodeBlock(block)
}

fun ASTVisitor.visit(expression: Expression): Expression {
    visitExpr(expression)
    val result = when(expression) {
        is BinaryExpression -> this.visitBinaryExpression(visit(expression.left), visit(expression.right), expression.operator, expression.position)
        is CallExpression -> this.visitCallExpression(expression.func, expression.arguments.map { visit(it) }, expression.position)
        is ConcatExpression -> this.visitConcatExpression(visit(expression.left), visit(expression.right), expression.position)
        is BooleanLiteral -> this.visitBooleanLiteral(expression.value, expression.position)
        is FloatLiteral -> this.visitFloatLiteral(expression.value, expression.position)
        is IntLiteral -> this.visitIntLiteral(expression.value, expression.position)
        is CharLiteral -> this.visitCharLiteral(expression.value, expression.position)
        NullExpression -> this.visitNullExpression()
        is StringLiteral -> this.visitStringLiteral(expression.value, expression.position)
        is LocalVariableExpression -> this.visitLocalVariableExpression(expression.variable, expression.position)
        is MemberExpression -> this.visitMemberExpression(visit(expression.expression), expression.member, expression.struct, expression.position)
        is NonNullAssertExpression -> this.visitNonNullAssertExpression(visit(expression.expression), expression.position)
        is SafeDotExpression -> this.visitSafeDotExpression(visit(expression.target), expression.member, expression.struct, expression.position)
        is ParameterExpression -> this.visitParameterExpression(expression.parameter, expression.position)
        is TemporaryHeapGetExpression -> this.visitTemporaryHeapGetExpression(visit(expression.index), expression.type, expression.position)
        is TemporaryLocalVariableIndexExpression -> this.visitTemporaryLocalVariableIndexExpression(expression.variable, expression.position)
        is TemporaryScratchExpr -> this.visitTemporaryScratchExpr(expression.inputExprs.map { visit(it) }, expression.expression, expression.position)
        is UnaryExpression -> this.visitUnaryExpression(expression.operator, visit(expression.expression), expression.position)
        is VariableExpression -> this.visitVariableExpression(expression.variable, expression.sourceAST, expression.position)
        is EnumLiteral -> this.visitEnumLiteral(expression.enum, expression.value, expression.ordinal, expression.position)
        is WhenExpression -> this.visitWhenExpr(expression.branches.map {
            WhenBranch(
                visit(it.cond),
                visitCodeBlock(it.block),
                it.isElse,
                it.position
            )
        }, expression.subject?.let { visit(it) }, expression.position)
        is NonNullOrElseExpression -> this.visitNonNullOrElseExpression(visit(expression.operand1), expression.operand2, expression.position)
        is TemporaryStackNameExpression -> this.visitTemporaryStackNameExpression(expression.function, expression.position)
        is TemporaryStackSizeExpression -> this.visitTemporaryStackSizeExpression(expression.function, expression.includeGcHeader, expression.position)
        is FunctionLiteral -> this.visitFunctionLiteral(expression.function, expression.position)
        is DynamicCallExpression -> this.visitDynamicCallExpression(visit(expression.function), expression.arguments.map { visit(it) }, expression.type, expression.position)
        is TypeLiteral -> this.visitTypeLiteral(expression.type, expression.position)
        is StatementExpression -> {
            val visitedStatements = expression.statements.flatMap { visitStatementWithBuffer(it) }
            val exprBuffer = mutableListOf<Statement>()
            statementBuffer.add(exprBuffer)
            val visitedExpr = visit(expression.expression)
            statementBuffer.removeAt(statementBuffer.lastIndex)
            this.visitStatementExpression(visitedStatements + exprBuffer, visitedExpr, expression.position)
        }
        is LambdaExpression -> this.visitLambdaExpression(expression.block, expression.parameters, expression.capturedVariables, expression.position)
        is CheckSealedEnumTypeExpression -> this.visitCheckSealedEnumTypeExpression(this.visit(expression.expr), expression.targetVariant, expression.sealedEnum, expression.tag, expression.position)
        is SealedEnumCastExpression -> this.visitSealedEnumCastExpression(this.visit(expression.expr), expression.targetVariant, expression.sealedEnum, expression.tag, expression.safe, expression.position)
        is SealedEnumConstructionExpression -> this.visitSealedEnumConstructionExpression(expression.sealedEnum, expression.targetVariant, expression.arguments.map { visit(it) }, expression.position)
        is StaticListExpression -> this.visitStaticListExpression(expression.list, expression.position)
        is StaticListLengthExpression -> this.visitStaticListLengthExpression(expression.list, expression.position)
        is StaticListItemExpression -> this.visitStaticListItemExpression(expression.list, visit(expression.index), expression.position)
        is StaticListContainsExpression -> this.visitStaticListContainsExpression(expression.list, visit(expression.item), expression.position)
        is StaticListItemIndexExpression -> this.visitStaticListItemIndexExpression(expression.list, visit(expression.item), expression.position)
        ErrorExpression -> this.visitErrorExpression()
    }
    afterVisit(expression, result)
    return result
}

fun ASTVisitor.visit(statement: Statement): Statement? {
    visitStatement(statement)
    return when(statement) {
        is ExpressionStatement -> this.visitExpressionStatement(visit(statement.expression), statement.position)
        is VariableStatement -> this.visitVariableStatement(statement.defaultValue?.let { visit(it) }, statement.variable, statement.position)
        is LocalVariableAssignmentStatement -> this.visitLocalVariableAssignmentStatement(statement.variable, visit(statement.assignment), statement.position)
        is VariableAssignmentStatement -> this.visitVariableAssignmentStatement(visit(statement.target), statement.variable, statement.struct, visit(statement.assignment), statement.position)
        is TLVariableAssignmentStatement -> this.visitTLVariableAssignmentStatement(statement.variable, statement.sourceAST, visit(statement.assignment), statement.position)
        is ReturnStatement -> this.visitReturnStatement(statement.expression?.let { visit(it) }, statement.position)
        is IfStatement -> this.visitIfStatement(visit(statement.condition), this.visitCodeBlock(statement.thenBlock), statement.position)
        is IfElseStatement -> this.visitIfElseStatement(visit(statement.condition), this.visitCodeBlock(statement.thenBlock), this.visitCodeBlock(statement.elseBlock), statement.position)
        is WhileStatement -> this.visitWhileStatement(visit(statement.condition), this.visitCodeBlock(statement.block), statement.position)
        is RepeatStatement -> this.visitRepeatStatement(visit(statement.amount), this.visitCodeBlock(statement.block), statement.position)
        is TemporaryCallStatement -> this.visitTemporaryCallStatement(statement.func, statement.args.map { visit(it) }.toMutableList(), statement.position)
        is TemporaryHeapSetStatement -> this.visitTemporaryHeapSetStatement(visit(statement.index), visit(statement.data), statement.position)
        is TemporaryScratchStmt -> this.visitTemporaryScratchStmt(statement.inputExprs.map { visit(it) }, statement.stmt, statement.position)
        is CompositeStatement -> CompositeStatement(statement.statements.mapNotNull { visit(it) }, statement.position)
        is BreakStatement -> this.visitBreakStatement(statement.position)
        is ContinueStatement -> this.visitContinueStatement(statement.position)
        is StaticListSetStatement -> this.visitStaticListSetStatement(statement.list, visit(statement.index), visit(statement.value), statement.position)
        is StaticListAddStatement -> this.visitStaticListAddStatement(statement.list, visit(statement.item), statement.position)
        is StaticListRemoveStatement -> this.visitStaticListRemoveStatement(statement.list, visit(statement.index), statement.position)
        is StaticListClearStatement -> this.visitStaticListClearStatement(statement.list, statement.position)
        is StaticListInsertStatement -> this.visitStaticListInsertStatement(statement.list, visit(statement.index), visit(statement.value), statement.position)
    }
}