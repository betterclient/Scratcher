package dev.betterclient.ast

sealed class Statement

data class ExpressionStatement(
    val expression: Expression
) : Statement()

data class VariableStatement(
    val defaultValue: Expression,
    val variable: LocalVariable
) : Statement()

data class LocalVariableAssignmentStatement(
    val value: LocalVariable,
    val assignment: Expression
) : Statement()

data class VariableAssignmentStatement(
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
    val expression: Expression
)

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