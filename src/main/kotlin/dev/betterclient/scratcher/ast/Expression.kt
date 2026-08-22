package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import java.math.BigDecimal
import java.math.BigInteger

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

data class DynamicCallExpression(
    val type: FunctionType,
    val function: Expression,
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

data class NonNullAssertExpression(
    val expression: Expression
) : Expression()

data class NonNullOrElseExpression(
    val operand1: Expression,
    val operand2: Expression
) : Expression()

data class SafeDotExpression(
    val struct: Struct,
    val target: Expression,
    val member: Parameter
) : Expression()

data class WhenExpression(
    val subject: Statement?, //if this is not null, we have a subject expr, and we need to prepend this statement before the thingy that causes when
    val branches: List<WhenBranch>
) : Expression()

data class WhenBranch(
    val cond: Expression,
    val block: CodeBlock,
    val isElse: Boolean = false
)

data class StatementExpression(
    val statements: List<Statement>,
    val expression: Expression
) : Expression()

data class LambdaExpression(
    val parameters: List<LocalVariable>,
    val block: CodeBlock,
    val capturedVariables: MutableSet<LocalVariable> = mutableSetOf()
) : Expression()

data class CheckSealedEnumTypeExpression(
    val expr: Expression,
    val targetVariant: Struct,
    val sealedEnum: SealedEnum,
    val tag: Int
) : Expression()

data class SealedEnumCastExpression(
    val expr: Expression,
    val targetVariant: Struct,
    val sealedEnum: SealedEnum,
    val tag: Int
) : Expression()

data class SealedEnumConstructionExpression(
    val sealedEnum: SealedEnum,
    val targetVariant: Struct,
    val arguments: List<Expression>
) : Expression()

sealed class Literal : Expression()
data class IntLiteral(val value: BigInteger) : Literal()
data class FloatLiteral(val value: BigDecimal) : Literal()
data class BooleanLiteral(val value: Boolean) : Literal()
data class StringLiteral(val value: String) : Literal()
data class CharLiteral(val value: Char) : Literal()
data class EnumLiteral(val enum: ASTEnum, val value: String, val ordinal: Int) : Literal() //I would use just a normal IntLiteral, but we need to know the enum for type checking
data class FunctionLiteral(val function: Function) : Literal()
data class TypeLiteral(val type: Type) : Literal() //do not let the user use this ever ever ever ever ever
object NullExpression : Literal()

//ONLY USE FOR LOWERING PHASE
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

data class TemporaryStackSizeExpression(
    val function: Function,
    val includeGcHeader: Boolean = false
) : TemporaryExpression()

data class TemporaryStackNameExpression(
    val function: Function
) : TemporaryExpression()