package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigDecimal
import java.math.BigInteger

sealed class Expression {
    abstract val position: Position?
}

data class MemberExpression(
    val expression: Expression,
    val member: Parameter,
    val struct: Struct,
    override val position: Position?
) : Expression()

data class CallExpression(
    val func: Function,
    val arguments: List<Expression>,
    override val position: Position?
) : Expression()

data class DynamicCallExpression(
    val type: FunctionType,
    val function: Expression,
    val arguments: List<Expression>,
    override val position: Position?
) : Expression()

data class ConcatExpression(
    val left: Expression,
    val right: Expression,
    override val position: Position?
) : Expression()

enum class UnaryOperator(val symbol: String) {
    PLUS("+"),
    MINUS("-"),
    NOT("!")
}

data class UnaryExpression(
    val operator: UnaryOperator,
    val expression: Expression,
    override val position: Position?
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
    OR("||"),
    STRICT_EQUAL("==="),
    STRICT_NOT_EQUAL("!==")
}

data class BinaryExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    override val position: Position?
) : Expression()

data class LocalVariableExpression(
    val variable: LocalVariable,
    override val position: Position?
) : Expression()

data class ParameterExpression(
    val parameter: Parameter,
    override val position: Position?
) : Expression()

data class VariableExpression(
    val variable: TLVariable,
    val sourceAST: ASTFile,
    override val position: Position?
) : Expression()

data class NonNullAssertExpression(
    val expression: Expression,
    override val position: Position?
) : Expression()

data class NonNullOrElseExpression(
    val operand1: Expression,
    val operand2: Expression,
    override val position: Position?
) : Expression()

data class SafeDotExpression(
    val struct: Struct,
    val target: Expression,
    val member: Parameter,
    override val position: Position?
) : Expression()

data class WhenExpression(
    val subject: Statement?, //if this is not null, we have a subject expr, and we need to prepend this statement before the thingy that causes when
    val branches: List<WhenBranch>,
    override val position: Position?
) : Expression()

data class WhenBranch(
    val cond: Expression,
    val block: CodeBlock,
    val isElse: Boolean = false,
    val position: Position?
)

data class StatementExpression(
    val statements: List<Statement>,
    val expression: Expression,
    override val position: Position?
) : Expression()

data class LambdaExpression(
    val parameters: List<LocalVariable>,
    val block: CodeBlock,
    val capturedVariables: MutableSet<LocalVariable> = mutableSetOf(),
    override val position: Position?
) : Expression()

data class CheckSealedEnumTypeExpression(
    val expr: Expression,
    val targetVariant: Struct,
    val sealedEnum: SealedEnum,
    val tag: Int,
    override val position: Position?
) : Expression()

data class SealedEnumCastExpression(
    val expr: Expression,
    val targetVariant: Struct,
    val sealedEnum: SealedEnum,
    val tag: Int,
    val safe: Boolean,
    override val position: Position?
) : Expression()

data class SealedEnumConstructionExpression(
    val sealedEnum: SealedEnum,
    val targetVariant: Struct,
    val arguments: List<Expression>,
    override val position: Position?
) : Expression()

data class StaticListItemExpression(
    val list: TLStaticList,
    val index: Expression,
    override val position: Position?
) : Expression()

data class StaticListLengthExpression(
    val list: TLStaticList,
    override val position: Position?
) : Expression()

data class StaticListExpression(
    val list: TLStaticList,
    override val position: Position?
) : Expression()

data class StaticListContainsExpression(
    val list: TLStaticList,
    val item: Expression,
    override val position: Position?
) : Expression()

data class StaticListItemIndexExpression(
    val list: TLStaticList,
    val item: Expression,
    override val position: Position?
) : Expression()

sealed class Literal : Expression()

data class IntLiteral(
    val value: BigInteger,
    override val position: Position?
) : Literal()

data class FloatLiteral(
    val value: BigDecimal,
    override val position: Position?
) : Literal()

data class BooleanLiteral(
    val value: Boolean,
    override val position: Position?
) : Literal()

data class StringLiteral(
    val value: String,
    override val position: Position?
) : Literal()

data class CharLiteral(
    val value: Char,
    override val position: Position?
) : Literal()

data class EnumLiteral(
    val enum: ASTEnum,
    val value: String,
    val ordinal: Int,
    override val position: Position?
) : Literal() //I would use just a normal IntLiteral, but we need to know the enum for type checking

data class FunctionLiteral(
    val function: Function,
    override val position: Position?
) : Literal()

data class TypeLiteral(
    val type: Type,
    override val position: Position?
) : Literal() //do not let the user use this ever ever ever ever ever

object NullExpression : Literal() {
    override val position: Position? = null
}

//ONLY USE FOR LOWERING PHASE
sealed class TemporaryExpression : Expression()

data class TemporaryLocalVariableIndexExpression(
    val variable: LocalVariable,
    override val position: Position?
) : TemporaryExpression()

data class TemporaryHeapGetExpression(
    val index: Expression,
    val type: Type? = null,
    override val position: Position?
) : TemporaryExpression()

data class TemporaryScratchExpr(
    val inputExprs: List<Expression>,
    val expression: (List<ScratchExpression>) -> ScratchExpression,
    override val position: Position?
) : TemporaryExpression()

data class TemporaryStackSizeExpression(
    val function: Function,
    val includeGcHeader: Boolean = false,
    override val position: Position?
) : TemporaryExpression()

data class TemporaryStackNameExpression(
    val function: Function,
    override val position: Position?
) : TemporaryExpression()

object ErrorExpression : TemporaryExpression() {
    override val position: Position? = null
}