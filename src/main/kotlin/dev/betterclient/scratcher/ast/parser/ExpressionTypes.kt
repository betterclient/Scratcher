package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.UnreachableException
import dev.betterclient.scratcher.std.lib.ListLib

object ExpressionTypes {
    fun getExpressionType(context: CompilationContext, expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(context, expr)
            is CallExpression -> {
                if (expr.func.sourceAST.path == "list") {
                    ListLib.getActualReturnType(context, expr) { getExpressionType(context, it) }
                } else expr.func.returnType
            }
            is BooleanLiteral -> PrimitiveType.Bool
            is FloatLiteral -> PrimitiveType.Float
            is IntLiteral -> PrimitiveType.Integer
            is StringLiteral -> PrimitiveType.Str
            is LocalVariableExpression -> expr.variable.type
            is MemberExpression -> expr.member.type
            is UnaryExpression -> getExpressionType(context, expr.expression)
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is EnumLiteral -> expr.enum.type
            is ConcatExpression -> PrimitiveType.Str
            is NullExpression -> PrimitiveType.Null
            is NonNullAssertExpression -> getExpressionType(context, expr.expression).asNonNull()
            is NonNullOrElseExpression -> {
                val op1 = getExpressionType(context, expr.operand1)
                val op2 = getExpressionType(context, expr.operand2)
                if (op2 is NullableType) op1 else op2
            }
            is WhenExpression -> {
                parseWhenExpressionType(expr, context)
            }
            is FunctionLiteral -> FunctionType.from(expr.function)
            is DynamicCallExpression -> expr.type.returnType
            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun parseWhenExpressionType(
        expr: WhenExpression,
        context: CompilationContext
    ): Type {
        val subjectType = getSubjectType(expr.subject)
        if (subjectType != null) {
            val baseType = subjectType.asNonNull()
            val isEnum = (baseType as? SimpleType)?.sourceAST?.enums?.any { it.type.asNonNull() == baseType } ?: false
            if (isEnum && !isWhenExhaustive(expr) && expr.branches.none { it.isElse }) {
                throw GeneralCompilerException("When statement/expression on enum must be exhaustive or have an else branch")
            }
        }

        val allBranchesReturnExpression = expr.branches.all {
            it.block.code.lastOrNull() is ExpressionStatement
        }
        return if (allBranchesReturnExpression && expr.branches.isNotEmpty()) {
            val branchTypes = expr.branches.map {
                getExpressionType(context, (it.block.code.last() as ExpressionStatement).expression)
            }
            val unifiedType = branchTypes.reduce { left, right ->
                unifyTypes(left, right) ?: throw GeneralCompilerException("When branches return different types.")
            }
            unifiedType
        } else {
            PrimitiveType.Void
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
                    leftType == PrimitiveType.Float || rightType == PrimitiveType.Float -> PrimitiveType.Float
                    else -> PrimitiveType.Integer
                }
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL,
            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.AND,
            BinaryOperator.OR -> PrimitiveType.Bool
        }
    }

    fun isWhenExhaustive(expr: WhenExpression): Boolean {
        if (expr.branches.any { it.isElse }) return true

        val subjectType = getSubjectType(expr.subject) ?: return false
        val baseType = subjectType.asNonNull()
        val isNullable = subjectType is NullableType

        val enumDef = (baseType as? SimpleType)?.sourceAST?.enums?.find { it.type.asNonNull() == baseType } ?: return false
        val covered = mutableSetOf<Int>()
        var coversNull = false

        for (branch in expr.branches) {
            val inner = (branch.cond as? BinaryExpression)?.right ?: return false
            when (inner) {
                is EnumLiteral -> {
                    covered.add(inner.ordinal)
                }
                is NullExpression -> {
                    coversNull = true
                }
                else -> {
                    return false
                }
            }
        }

        val enumsCovered = covered.containsAll(enumDef.values.indices.toSet())
        return if (isNullable) {
            enumsCovered && coversNull
        } else {
            enumsCovered
        }
    }

    fun getSubjectType(subject: Statement?): Type? {
        return when (subject) {
            is VariableStatement -> subject.variable.type
            is TLVariableAssignmentStatement -> subject.variable.type
            else -> null
        }
    }
}