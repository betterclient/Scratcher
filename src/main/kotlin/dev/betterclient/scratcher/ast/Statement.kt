package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement

sealed class Statement

data class ExpressionStatement(
    val expression: Expression
) : Statement()

data class VariableStatement(
    val defaultValue: Expression,
    val variable: LocalVariable
) : Statement()

data class LocalVariableAssignmentStatement(
    val variable: LocalVariable,
    val assignment: Expression
) : Statement()

data class VariableAssignmentStatement(
    val target: Expression,
    val variable: Parameter,
    val struct: Struct,
    val assignment: Expression,
) : Statement()

data class TLVariableAssignmentStatement(
    val variable: TLVariable,
    val sourceAST: ASTFile,
    val assignment: Expression,
) : Statement()

data class ReturnStatement(
    val expression: Expression?
) : Statement()

data class IfStatement(
    val condition: Expression,
    val thenBlock: CodeBlock
) : Statement()

data class IfElseStatement(
    val condition: Expression,
    val thenBlock: CodeBlock,
    val elseBlock: CodeBlock
) : Statement()

data class WhileStatement(
    val condition: Expression,
    val block: CodeBlock
) : Statement()

data class RepeatStatement(
    val amount: Expression,
    val block: CodeBlock
) : Statement()

//ONLY USE FOR LOWERING PHASE in FunctionExpressionLowering.kt
sealed class TemporaryStatement : Statement()

data class TemporaryCallStatement(
    val func: Function,
    val args: MutableList<Expression>
) : TemporaryStatement()

data class TemporaryHeapSetStatement(
    val index: Expression,
    val data: Expression
) : TemporaryStatement()

data class TemporaryScratchStmt(
    val inputExprs: List<Expression>,
    val stmt: (List<ScratchExpression>) -> List<ScratchStatement>,
) : TemporaryStatement()