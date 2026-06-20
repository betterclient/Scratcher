package dev.betterclient.ast

sealed class Statement

class ExpressionStatement(
    val expression: Expression
) : Statement()

class VariableStatement(
    val name: String,
    val mutable: Boolean,
    val defaultValue: Expression,
    val type: Type,
) : Statement()

class VariableAssignmentStatement(
    val variable: Expression,
    val assignment: Expression,
) : Statement()

class ReturnStatement(
    val expression: Expression
)

class IfStatement(
    val condition: Expression,
    val thenBlock: List<Statement>
) : Statement()

class IfElseStatement(
    val condition: Expression,
    val thenBlock: List<Statement>,
    val elseBlock: List<Statement>
)

class WhileStatement(
    val condition: Expression,
    val statements: List<Statement>
)

class RepeatStatement(
    val amount: Expression,
    val statements: List<Statement>
)