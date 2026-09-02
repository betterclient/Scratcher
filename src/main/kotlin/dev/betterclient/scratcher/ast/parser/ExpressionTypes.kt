package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.ArrayLib

object ExpressionTypes {
    fun getExpressionType(expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(expr)
            is CallExpression -> {
                if (ArrayLib.arrayFuncs.contains(expr.func)) {
                    ArrayLib.getActualReturnType(expr) { getExpressionType(it) }
                } else expr.func.returnType
            }
            is TypeLiteral -> expr.type
            is BooleanLiteral -> PrimitiveType.Bool
            is FloatLiteral -> PrimitiveType.Float
            is IntLiteral -> PrimitiveType.Integer
            is StringLiteral -> PrimitiveType.Str
            is CharLiteral -> PrimitiveType.Char
            is LocalVariableExpression -> expr.variable.type
            is MemberExpression -> expr.member.type
            is UnaryExpression -> getExpressionType(expr.expression)
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is EnumLiteral -> expr.enum.type
            is ConcatExpression -> PrimitiveType.Str
            is NullExpression -> PrimitiveType.Null
            is NonNullAssertExpression -> getExpressionType(expr.expression).asNonNull()
            is NonNullOrElseExpression -> {
                val op1 = getExpressionType(expr.operand1)
                val op2 = getExpressionType(expr.operand2)
                unifyTypes(op1.asNonNull(), op2.asNonNull()) ?: op2
            }
            is SafeDotExpression -> expr.member.type.asNullable()
            is WhenExpression -> {
                parseWhenExpressionType(expr)
            }
            is FunctionLiteral -> FunctionType.from(expr.function)
            is DynamicCallExpression -> expr.type.returnType
            is StatementExpression -> getExpressionType(expr.expression)
            is LambdaExpression -> parseLambdaType(expr)
            is CheckSealedEnumTypeExpression -> PrimitiveType.Bool
            is SealedEnumCastExpression -> expr.targetVariant.type
            is SealedEnumConstructionExpression -> expr.sealedEnum.type
            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun parseWhenExpressionType(
        expr: WhenExpression
    ): Type {
        val subjectType = getSubjectType(expr.subject)
        if (subjectType != null) {
            val baseType = subjectType.asNonNull()
            val isEnum = (baseType as? SimpleType)?.sourceAST?.enums?.any { it.type.asNonNull() == baseType } ?: false
            if (isEnum && !isWhenExhaustive(expr) && expr.branches.none { it.isElse }) {
                throw GeneralCompilerException("When statement/expression on enum must be exhaustive or have an else branch")
            }
        }

        val branchValues = expr.branches.map {
            getWhenBranchValue(it)
        }
        return if (branchValues.isNotEmpty() && branchValues.all { it != null }) {
            val branchTypes = branchValues.map {
                getExpressionType(it!!)
            }
            val unifiedType = branchTypes.reduce { left, right ->
                unifyTypes(left, right) ?: throw GeneralCompilerException("When branches return different types, $left and $right")
            }
            unifiedType
        } else {
            PrimitiveType.Void
        }
    }

    fun getWhenBranchValue(branch: WhenBranch): Expression? {
        val code = branch.block.code
        if (code.isEmpty()) return null

        val last = code.last()
        if (last is ExpressionStatement) {
            val type = getExpressionType(last.expression)
            if (type != PrimitiveType.Void) return last.expression
        }

        for (stmt in code.asReversed()) {
            if (stmt is VariableStatement && stmt.defaultValue != null &&
                stmt.variable.name.startsWith("compiler@gc_ignored_ret_")
            ) {
                val type = getExpressionType(stmt.defaultValue)
                if (type != PrimitiveType.Void) return LocalVariableExpression(stmt.variable)
            }
        }

        return null
    }

    private fun figureOutBinaryExprReturn(expr: BinaryExpression): Type {
        val leftType = getExpressionType(expr.left)
        val rightType = getExpressionType(expr.right)

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

        if (baseType is SealedEnumType) {
            val sealed = baseType.sourceAST.sealedEnums.find { it.name == baseType.name }
                ?: baseType.sourceAST.imports.values.flatMap { it.sealedEnums }.find { it.name == baseType.name }
                ?: return false
            val covered = mutableSetOf<Int>()
            var coversNull = false
            for (branch in expr.branches) {
                val cond = branch.cond
                when (cond) {
                    is CheckSealedEnumTypeExpression -> covered.add(cond.tag)
                    is NullExpression -> coversNull = true
                    else -> return false
                }
            }
            val allTags = sealed.types.mapIndexed { idx, struct -> if (struct.parameters.isEmpty()) -idx-1 else idx }.toSet()
            val enumsCovered = covered.containsAll(allTags)
            return if (isNullable) enumsCovered && coversNull else enumsCovered
        }

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

    private fun parseLambdaType(
        expr: LambdaExpression
    ): Type {
        val returnTypes = mutableListOf<Type>()
        val block = expr.block
        visit(block, ParseLambdaTypes(returnTypes))
        val returnType = returnTypes.reduceOrNull { left, right ->
            unifyTypes(left, right)?: throw GeneralCompilerException("Cannot unify $left and $right, lambda result unknown")
        }?: PrimitiveType.Void

        return FunctionType(
            expr.parameters.map { it.type },
            if (returnType == PrimitiveType.Null) PrimitiveType.Void else returnType
        )
    }

    private class ParseLambdaTypes(
        val returnTypes: MutableList<Type>
    ) : ASTVisitor() {
        override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
        override fun visitReturnStatement(expression: Expression?): Statement? {
            if (expression != null) {
                returnTypes.add(getExpressionType(expression))
            } else {
                returnTypes.add(PrimitiveType.Void)
            }
            return super.visitReturnStatement(expression)
        }

        override fun visitLambdaExpression(
            block: CodeBlock,
            arguments: List<LocalVariable>,
            captured: MutableSet<LocalVariable>
        ): Expression {
            return LambdaExpression(arguments, block, captured) //don't visit block
        }
    }
}