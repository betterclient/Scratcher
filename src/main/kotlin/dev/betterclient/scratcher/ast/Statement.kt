package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import org.antlr.v4.kotlinruntime.ast.Position

sealed class Statement {
    abstract val position: Position?
}

data class ExpressionStatement(
    val expression: Expression,
    override val position: Position?
) : Statement()

data class VariableStatement(
    val defaultValue: Expression?,
    val variable: LocalVariable,
    override val position: Position?
) : Statement()

data class LocalVariableAssignmentStatement(
    val variable: LocalVariable,
    val assignment: Expression,
    override val position: Position?
) : Statement()

data class VariableAssignmentStatement(
    val target: Expression,
    val variable: Parameter,
    val struct: Struct,
    val assignment: Expression,
    override val position: Position?
) : Statement()

data class TLVariableAssignmentStatement(
    val variable: TLVariable,
    val sourceAST: ASTFile,
    val assignment: Expression,
    override val position: Position?
) : Statement()

data class ReturnStatement(
    val expression: Expression?,
    override val position: Position?
) : Statement()

data class IfStatement(
    val condition: Expression,
    val thenBlock: CodeBlock,
    override val position: Position?
) : Statement()

data class IfElseStatement(
    val condition: Expression,
    val thenBlock: CodeBlock,
    val elseBlock: CodeBlock,
    override val position: Position?
) : Statement()

data class WhileStatement(
    val condition: Expression,
    val block: CodeBlock,
    override val position: Position?
) : Statement()

data class RepeatStatement(
    val amount: Expression,
    val block: CodeBlock,
    override val position: Position?
) : Statement()

class BreakStatement(
    override val position: Position?
) : Statement()

class ContinueStatement(
    override val position: Position?
) : Statement()

data class StaticListSetStatement(
    val list: TLStaticList,
    val index: Expression,
    val value: Expression,
    override val position: Position?
) : Statement()

data class StaticListAddStatement(
    val list: TLStaticList,
    val item: Expression,
    override val position: Position?
) : Statement()

data class StaticListRemoveStatement(
    val list: TLStaticList,
    val index: Expression,
    override val position: Position?
) : Statement()

data class StaticListClearStatement(
    val list: TLStaticList,
    override val position: Position?
) : Statement()

data class StaticListInsertStatement(
    val list: TLStaticList,
    val index: Expression,
    val value: Expression,
    override val position: Position?
) : Statement()

//ONLY USE FOR LOWERING PHASE
sealed class TemporaryStatement : Statement()

class CompositeStatement(
    val statements: List<Statement>,
    override val position: Position?
) : TemporaryStatement()

data class TemporaryCallStatement(
    val func: Function,
    val args: MutableList<Expression>,
    override val position: Position?
) : TemporaryStatement()

data class TemporaryHeapSetStatement(
    val index: Expression,
    val data: Expression,
    override val position: Position?
) : TemporaryStatement()

data class TemporaryScratchStmt(
    val inputExprs: List<Expression>,
    val stmt: (List<ScratchExpression>) -> List<ScratchStatement>,
    override val position: Position?
) : TemporaryStatement()