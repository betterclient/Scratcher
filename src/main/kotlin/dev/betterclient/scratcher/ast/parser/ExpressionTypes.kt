package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.except.GeneralCompilerException
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.std.lib.ListLib

fun isWhenExhaustive(expr: WhenExpression): Boolean {
    if (expr.branches.any { it.isElse }) return true
    val subjectType = (expr.subject as? VariableStatement)?.variable?.type ?: return false
    val enumDef = subjectType.sourceAST?.enums?.find { it.type.asNonNull() == subjectType.asNonNull() } ?: return false
    val covered = mutableSetOf<Int>()
    for (branch in expr.branches) {
        val inner = (branch.cond as? BinaryExpression)?.right ?: return false
        if (inner is EnumLiteral) {
            covered.add(inner.ordinal)
        } else {
            return false
        }
    }
    return covered.containsAll(enumDef.values.indices.toSet())
}

object ExpressionTypes {
    fun getExpressionType(context: CompilationContext, expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(context, expr)
            is CallExpression -> {
                if (expr.func.sourceAST.path == "list") {
                    ListLib.getActualReturnType(context, expr) { getExpressionType(context, it) }
                } else expr.func.returnType
            }
            is BooleanLiteral -> Type.bool
            is FloatLiteral -> Type.float
            is IntLiteral -> Type.int
            is StringLiteral -> Type.str
            is LocalVariableExpression -> expr.variable.type
            is MemberExpression -> expr.member.type
            is UnaryExpression -> getExpressionType(context, expr.expression)
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is EnumLiteral -> expr.enum.type
            is ConcatExpression -> Type.str
            is NullExpression -> Type.nullType
            is NonNullAssertExpression -> getExpressionType(context, expr.expression).asNonNull()
            is WhenExpression -> {
                val allBranchesReturnExpression = expr.branches.all {
                    it.block.code.lastOrNull() is ExpressionStatement
                }
                if (allBranchesReturnExpression && expr.branches.isNotEmpty()) {
                    val branchTypes = expr.branches.map {
                        getExpressionType(context, (it.block.code.last() as ExpressionStatement).expression)
                    }
                    val unifiedType = branchTypes.reduce { left, right ->
                        if (left.isAssignable(right)) left
                        else if (right.isAssignable(left)) right
                        else throw GeneralCompilerException("When branches return different types.")
                    }
                    if (unifiedType != Type.void && !isWhenExhaustive(expr) && expr.branches.none { it.isElse }) {
                        throw GeneralCompilerException("When expression used as value must have an else branch or be exhaustive")
                    }
                    unifiedType
                } else {
                    Type.void
                }
            }
            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun figureOutBinaryExprReturn(context: CompilationContext, expr: BinaryExpression): Type {
        val leftType = getExpressionType(context, expr.left)
        val rightType = getExpressionType(context, expr.right)

        return when (expr.operator) {
            BinaryOperator.ADD, BinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE,
            BinaryOperator.MODULO -> {
                when {
                    leftType == Type.float || rightType == Type.float -> Type.float
                    else -> Type.int
                }
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL,
            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.AND,
            BinaryOperator.OR -> Type.bool
        }
    }
}