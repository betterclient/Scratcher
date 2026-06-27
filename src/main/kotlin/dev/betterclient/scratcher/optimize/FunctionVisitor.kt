package dev.betterclient.scratcher.optimize

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
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

    private val statementBuffer = mutableListOf<MutableList<Statement>>()
    fun addStatements(list: List<Statement>) {
        val buffer = statementBuffer.lastOrNull()
        if (buffer != null) {
            buffer.addAll(list)
        } else {
            currentBlock?.code?.addAll(list)
        }
    }

    private fun visitStatementWithBuffer(statement: Statement): List<Statement> {
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

    fun visitCodeBlock(block: CodeBlock): CodeBlock {
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
    fun visitExpressionStatement(expression: Expression): Statement? = ExpressionStatement(expression)
    fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? = VariableStatement(defaultValue, variable)
    fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? = LocalVariableAssignmentStatement(variable, assignment)
    fun visitVariableAssignmentStatement(target: Expression, variable: Parameter, struct: Struct, assignment: Expression): Statement? = VariableAssignmentStatement(target, variable, struct, assignment)
    fun visitTLVariableAssignmentStatement(variable: TLVariable, sourceAST: ASTFile, assignment: Expression): Statement? = TLVariableAssignmentStatement(variable, sourceAST, assignment)
    fun visitReturnStatement(expression: Expression?): Statement? = ReturnStatement(expression)
    fun visitIfStatement(condition: Expression, thenBlock: CodeBlock): Statement? = IfStatement(condition, thenBlock)
    fun visitIfElseStatement(condition: Expression, thenBlock: CodeBlock, elseBlock: CodeBlock): Statement? = IfElseStatement(condition, thenBlock, elseBlock)
    fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? = WhileStatement(condition, block)
    fun visitRepeatStatement(amount: Expression, block: CodeBlock): Statement? = RepeatStatement(amount, block)
    fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? = TemporaryCallStatement(func, args)
    fun visitTemporaryHeapSetStatement(index: Expression, data: Expression): Statement? = TemporaryHeapSetStatement(index, data)
    fun visitTemporaryScratchStmt(inputExprs: List<Expression>, stmt: (List<ScratchExpression>) -> List<ScratchStatement>): Statement? = TemporaryScratchStmt(inputExprs, stmt)
}

interface BaseExpressionVisitor {
    fun visitBinaryExpression(left: Expression, right: Expression, operator: BinaryOperator): Expression = BinaryExpression(left, operator, right)
    fun visitCallExpression(func: Function, args: List<Expression>): Expression = CallExpression(func, args)
    fun visitIntLiteral(value: BigInteger): Expression = IntLiteral(value)
    fun visitLocalVariableExpression(variable: LocalVariable): Expression = LocalVariableExpression(variable)
    fun visitMemberExpression(expression: Expression, member: Parameter, struct: Struct): Expression = MemberExpression(expression, member, struct)
    fun visitConcatExpression(left: Expression, right: Expression): Expression = ConcatExpression(left, right)
    fun visitUnaryExpression(operator: UnaryOperator, expression: Expression): Expression = UnaryExpression(operator, expression)
    fun visitNonNullAssertExpression(expression: Expression): Expression = NonNullAssertExpression(expression)
    fun visitParameterExpression(parameter: Parameter): Expression = ParameterExpression(parameter)
    fun visitVariableExpression(variable: TLVariable, sourceAST: ASTFile): Expression = VariableExpression(variable, sourceAST)
    fun visitFloatLiteral(value: BigDecimal): Expression = FloatLiteral(value)
    fun visitBooleanLiteral(value: Boolean): Expression = BooleanLiteral(value)
    fun visitStringLiteral(value: String): Expression = StringLiteral(value)
    fun visitNullExpression(): Expression = NullExpression
    fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression = TemporaryLocalVariableIndexExpression(variable)
    fun visitTemporaryHeapGetExpression(index: Expression): Expression = TemporaryHeapGetExpression(index)
    fun visitTemporaryScratchExpr(inputExprs: List<Expression>, expression: (List<ScratchExpression>) -> ScratchExpression): Expression = TemporaryScratchExpr(inputExprs, expression)
}

object EmptyVisitor : ASTVisitor()

fun visit(
    func: Function,
    visitor: ASTVisitor = EmptyVisitor
) {
    visitor.visitCodeBlock(func.code)
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

fun BaseExpressionVisitor.visit(expression: Expression): Expression {
    return when(expression) {
        is BinaryExpression -> this.visitBinaryExpression(visit(expression.left), visit(expression.right), expression.operator)
        is CallExpression -> this.visitCallExpression(expression.func, expression.arguments.map { visit(it) })
        is ConcatExpression -> this.visitConcatExpression(visit(expression.left), visit(expression.right))
        is BooleanLiteral -> this.visitBooleanLiteral(expression.value)
        is FloatLiteral -> this.visitFloatLiteral(expression.value)
        is IntLiteral -> this.visitIntLiteral(expression.value)
        NullExpression -> this.visitNullExpression()
        is StringLiteral -> this.visitStringLiteral(expression.value)
        is LocalVariableExpression -> this.visitLocalVariableExpression(expression.variable)
        is MemberExpression -> this.visitMemberExpression(visit(expression.expression), expression.member, expression.struct)
        is NonNullAssertExpression -> this.visitNonNullAssertExpression(visit(expression.expression))
        is ParameterExpression -> this.visitParameterExpression(expression.parameter)
        is TemporaryHeapGetExpression -> this.visitTemporaryHeapGetExpression(visit(expression.index))
        is TemporaryLocalVariableIndexExpression -> this.visitTemporaryLocalVariableIndexExpression(expression.variable)
        is TemporaryScratchExpr -> this.visitTemporaryScratchExpr(expression.inputExprs.map { visit(it) }, expression.expression)
        is UnaryExpression -> this.visitUnaryExpression(expression.operator, visit(expression.expression))
        is VariableExpression -> this.visitVariableExpression(expression.variable, expression.sourceAST)
    }
}

fun ASTVisitor.visit(statement: Statement): Statement? {
    return when(statement) {
        is ExpressionStatement -> this.visitExpressionStatement(visit(statement.expression))
        is VariableStatement -> this.visitVariableStatement(statement.defaultValue?.let { visit(it) }, statement.variable)
        is LocalVariableAssignmentStatement -> this.visitLocalVariableAssignmentStatement(statement.variable, visit(statement.assignment))
        is VariableAssignmentStatement -> this.visitVariableAssignmentStatement(visit(statement.target), statement.variable, statement.struct, visit(statement.assignment))
        is TLVariableAssignmentStatement -> this.visitTLVariableAssignmentStatement(statement.variable, statement.sourceAST, visit(statement.assignment))
        is ReturnStatement -> this.visitReturnStatement(statement.expression?.let { visit(it) })
        is IfStatement -> this.visitIfStatement(visit(statement.condition), this.visitCodeBlock(statement.thenBlock))
        is IfElseStatement -> this.visitIfElseStatement(visit(statement.condition), this.visitCodeBlock(statement.thenBlock), this.visitCodeBlock(statement.elseBlock))
        is WhileStatement -> this.visitWhileStatement(visit(statement.condition), this.visitCodeBlock(statement.block))
        is RepeatStatement -> this.visitRepeatStatement(visit(statement.amount), this.visitCodeBlock(statement.block))
        is TemporaryCallStatement -> this.visitTemporaryCallStatement(statement.func, statement.args.map { visit(it) }.toMutableList())
        is TemporaryHeapSetStatement -> this.visitTemporaryHeapSetStatement(visit(statement.index), visit(statement.data))
        is TemporaryScratchStmt -> this.visitTemporaryScratchStmt(statement.inputExprs.map { visit(it) }, statement.stmt)
        is CompositeStatement -> statement
    }
}