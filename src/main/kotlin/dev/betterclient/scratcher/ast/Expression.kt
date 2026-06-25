package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.codegen.ast.ScratchExpression

sealed class Expression

data class MemberExpression(
    val expression: Expression,
    val member: Parameter,
    val struct: Struct
) : Expression()

data class CallExpression(
    val func: Function,
    val arguments: List<Expression>
) : Expression()

data class ConcatExpression(
    val left: Expression,
    val right: Expression
) : Expression()

enum class UnaryOperator(val symbol: String) {
    PLUS("+"),
    MINUS("-"),
    NOT("!")
}

data class UnaryExpression(
    val operator: UnaryOperator,
    val expression: Expression
) : Expression()

enum class BinaryOperator(val symbol: String) {
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%"),
    ADD("+"),
    SUBTRACT("-"),
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_EQUAL("<="),
    GREATER_EQUAL(">="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    AND("&&"),
    OR("||")
}

data class BinaryExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression
) : Expression()

data class LocalVariableExpression(
    val variable: LocalVariable
) : Expression()

data class ParameterExpression(
    val parameter: Parameter
) : Expression()

data class VariableExpression(
    val variable: TLVariable,
    val sourceAST: ASTFile
) : Expression()

sealed class Literal : Expression()
data class IntLiteral(val value: Int) : Literal()
data class FloatLiteral(val value: Float) : Literal()
data class BooleanLiteral(val value: Boolean) : Literal()
data class StringLiteral(val value: String) : Literal()

//ONLY USE FOR LOWERING PHASE in FunctionExpressionLowering.kt
sealed class TemporaryExpression : Expression()
data class TemporaryLocalVariableIndexExpression(
    val variable: LocalVariable
) : TemporaryExpression()

data class TemporaryHeapGetExpression(
    val index: Expression,
) : TemporaryExpression()

data class TemporaryScratchExpr(
    val inputExprs: List<Expression>,
    val expression: (List<ScratchExpression>) -> ScratchExpression
) : TemporaryExpression()