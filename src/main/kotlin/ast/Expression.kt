package dev.betterclient.ast

sealed class Expression

class ParenthesizedExpression(
    val expression: Expression
) : Expression()

class MemberExpression(
    val expression: Expression,
    val member: String
) : Expression()

class CallExpression(
    val func: Function,
    val arguments: List<Expression>
) : Expression()

enum class UnaryOperator(val symbol: String) {
    PLUS("+"),
    MINUS("-"),
    NOT("!")
}

class UnaryExpression(
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

class BinaryExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression
) : Expression()

class IdentifierExpression(
    val name: String
) : Expression()

sealed class Literal : Expression()
class IntLiteral(val value: Int) : Literal()
class FloatLiteral(val value: Float) : Literal()
class PlainStringLiteral(val value: String) : Literal()
class InterpolatedStringLiteral(val value: String) : Literal()
class BooleanLiteral(val value: Boolean) : Literal()