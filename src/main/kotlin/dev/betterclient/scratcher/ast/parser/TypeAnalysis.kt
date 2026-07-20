package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.TypeAnalysisException
import dev.betterclient.scratcher.ast.TypeException
import dev.betterclient.scratcher.ast.UnreachableException
import dev.betterclient.scratcher.ast.VoidVariableException
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ListLib

class TypeAnalysis(val ctx: CompilationContext, val ast: ASTFile) {
    fun run() {
        ast.completedTypeAnalysis = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedTypeAnalysis) {
                ast.completedTypeAnalysis = true
                TypeAnalysis(ctx, ast).run()
            }
        }

        if (StandardLibASTGenerator.lib.containsValue(ast) && !StandardLibASTGenerator.rawLibs.contains(ast)) {
            return
        }

        internalRun()
    }

    private fun internalRun() {
        for (variable in ast.variables) {
            variable.defaultValue?.let {
                val actualType = getActualTypeOrThrow(it, null)
                if (variable.type == PrimitiveType.Void) throw VoidVariableException("Variable ${ast.simplePath}::${variable.name} is of type void.")
                if (!actualType.isAssignable(variable.type)) throw TypeAnalysisException("Tried to assign $actualType to ${variable.name}, which has type ${variable.type}")
            }
        }

        val allFunctionsToVerify = ast.functions + ast.templates

        for (function in allFunctionsToVerify) {
            checkCodeBlock(function, function.code.code)

            if (function.returnType != PrimitiveType.Void && !doesBlockGuaranteeReturn(function.code.code)) {
                throw TypeAnalysisException("Function/Template ${ast.simplePath}::${function.name} does not have a guaranteed return")
            }
            pruneUnreachableCode(function.code.code)
        }
    }

    private fun doesBlockGuaranteeReturn(code: List<Statement>): Boolean {
        for (statement in code) {
            if (doesStatementGuaranteeReturn(statement)) {
                return true
            }
        }
        return false
    }

    private fun pruneUnreachableCode(code: MutableList<Statement>) {
        var hasReturn = false
        code.removeIf { statement ->
            when (statement) {
                is IfElseStatement -> {
                    pruneUnreachableCode(statement.thenBlock.code)
                    pruneUnreachableCode(statement.elseBlock.code)
                }
                is IfStatement -> {
                    pruneUnreachableCode(statement.thenBlock.code)
                }
                is WhileStatement -> {
                    pruneUnreachableCode(statement.block.code)
                }
                is RepeatStatement -> {
                    pruneUnreachableCode(statement.block.code)
                }
                else -> {}
            }

            if (doesStatementGuaranteeReturn(statement)) {
                hasReturn = true
                return@removeIf false
            }

            return@removeIf hasReturn
        }
    }

    private fun doesStatementGuaranteeReturn(statement: Statement): Boolean {
        return when (statement) {
            is ReturnStatement -> true
            is IfElseStatement -> {
                doesBlockGuaranteeReturn(statement.thenBlock.code) && doesBlockGuaranteeReturn(statement.elseBlock.code)
            }
            else -> false
        }
    }

    private fun checkCodeBlock(function: Function, code: MutableList<Statement>, isWhenBranch: Boolean = false) {
        for (statement in code) {
            when(statement) {
                is ExpressionStatement -> {
                    getActualTypeOrThrow(statement.expression, function)
                    if (!isWhenBranch && statement.expression !is CallExpression && statement.expression !is WhenExpression && statement.expression !is DynamicCallExpression) {
                        throw TypeAnalysisException("Unsupported expression as top level. ${statement.expression}")
                    }
                }
                is IfElseStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool as if statement condition")
                    checkCodeBlock(function, statement.thenBlock.code, isWhenBranch)
                    checkCodeBlock(function, statement.elseBlock.code, isWhenBranch)
                }
                is IfStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool as if condition")
                    checkCodeBlock(function, statement.thenBlock.code, isWhenBranch)
                }
                is LocalVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Local variable assignment type is not correct")
                }
                is RepeatStatement -> {
                    checkType(PrimitiveType.Integer, getActualTypeOrThrow(statement.amount, function), "Repeat statement requires an integer amount")
                    checkCodeBlock(function, statement.block.code, isWhenBranch)
                }
                is TLVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Top level variable assignment type is not correct")
                }
                is VariableAssignmentStatement -> {
                    checkType(statement.struct.type, getActualTypeOrThrow(statement.target, function), "Assigning to the wrong struct type.")
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Struct variable assignment type is not correct")
                }
                is VariableStatement -> {
                    statement.defaultValue?.let {
                        checkType(statement.variable.type, getActualTypeOrThrow(it, function), "Local variable default value is not expected type")
                    }
                }
                is WhileStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool used as while condition")
                    checkCodeBlock(function, statement.block.code, isWhenBranch)
                }
                is ReturnStatement -> {
                    if (statement.expression == null) {
                        if (function.returnType != PrimitiveType.Void) {
                            throw TypeAnalysisException("Must return a value from a non-void function.")
                        }
                    } else {
                        if (function.returnType == PrimitiveType.Void) {
                            throw TypeAnalysisException("Cannot return a value from a void function.")
                        }
                        checkType(function.returnType, getActualTypeOrThrow(statement.expression, function), "Return statement type")
                    }
                }
                is TemporaryStatement -> {}
            }
        }
    }

    fun checkType(expected: Type, found: Type, errorMessage: String) {
        if (!found.isAssignable(expected)) {
            throw TypeException(expected, found, errorMessage)
        }
    }

    private fun getActualTypeOrThrow(expr: Expression, function: Function?): Type {
        return when(expr) {
            is WhenExpression -> {
                figureOutWhenExpressionType(function, expr)
            }
            is BinaryExpression -> figureOutBinaryExprReturn(expr, function)
            is UnaryExpression -> {
                val operandType = getActualTypeOrThrow(expr.expression, function)
                when (expr.operator) {
                    UnaryOperator.PLUS, UnaryOperator.MINUS -> {
                        if (operandType == PrimitiveType.Integer || operandType == PrimitiveType.Float) {
                            operandType
                        } else {
                            throw TypeAnalysisException("Unary operator '${expr.operator.symbol}' cannot be applied to type $operandType")
                        }
                    }
                    UnaryOperator.NOT -> {
                        if (operandType == PrimitiveType.Bool) {
                            PrimitiveType.Bool
                        } else {
                            throw TypeAnalysisException("Unary operator '${expr.operator.symbol}' cannot be applied to type $operandType")
                        }
                    }
                }
            }
            is ConcatExpression -> {
                val leftType = getActualTypeOrThrow(expr.left, function)
                val rightType = getActualTypeOrThrow(expr.right, function)

                val leftOk = leftType.isPrimitive && leftType != PrimitiveType.Void && leftType != PrimitiveType.Null
                val rightOk = rightType.isPrimitive && rightType != PrimitiveType.Void && rightType != PrimitiveType.Null
                if (!leftOk || !rightOk) throw TypeAnalysisException("Either $leftType or $rightType isn't concattable")

                PrimitiveType.Str
            }
            is CallExpression -> {
                if (ListLib.listFuncs.contains(expr.func)) {
                    ListLib.getActualReturnType(this.ctx, expr) { getActualTypeOrThrow(it, function) }
                } else {
                    expr.arguments.forEachIndexed { index, expression ->
                        checkType(expr.func.parameters[index].type, getActualTypeOrThrow(expression, function), "Call parameter type is not correct")
                    }

                    expr.func.returnType
                }
            }
            is DynamicCallExpression -> {
                checkType(
                    expected = expr.type,
                    found = getActualTypeOrThrow(expr.function, function),
                    errorMessage = "Dynamic call expression is not the correct function type"
                )
                expr.arguments.forEachIndexed { index, expression ->
                    checkType(expr.type.parameterTypes[index], getActualTypeOrThrow(expression, function), "Call parameter type is not correct")
                }

                expr.type.returnType
            }
            is MemberExpression -> {
                checkType(expr.struct.type, getActualTypeOrThrow(expr.expression, function), "Struct type is not correct")
                expr.member.type
            }
            is NonNullAssertExpression -> {
                val innerType = getActualTypeOrThrow(expr.expression, function)
                if (innerType !is NullableType) {
                    throw TypeAnalysisException("Cannot assert non-null with '!!' on a type that is already non-nullable: $innerType")
                }
                innerType.asNonNull()
            }

            //these already have their type determined
            is BooleanLiteral -> PrimitiveType.Bool
            is FloatLiteral -> PrimitiveType.Float
            is IntLiteral -> PrimitiveType.Integer
            is StringLiteral -> PrimitiveType.Str
            is NullExpression -> PrimitiveType.Null
            is LocalVariableExpression -> expr.variable.type
            is VariableExpression -> expr.variable.type
            is EnumLiteral -> expr.enum.type
            is ParameterExpression -> expr.parameter.type
            is FunctionLiteral -> FunctionType.from(expr.function)

            is TemporaryExpression -> throw UnreachableException()
        }
    }

    private fun figureOutWhenExpressionType(
        function: Function?,
        expr: WhenExpression
    ): Type {
        if (function != null) {
            val subjectType = ExpressionTypes.getSubjectType(expr.subject)
            expr.subject?.let { checkCodeBlock(function, mutableListOf(it)) }

            if (subjectType != null) {
                val baseType = subjectType.asNonNull()
                val isEnum = (baseType as? SimpleType)?.sourceAST?.enums?.any { it.type.asNonNull() == baseType } ?: false
                if (isEnum && !ExpressionTypes.isWhenExhaustive(expr) && expr.branches.none { it.isElse }) {
                    throw TypeAnalysisException("When statement/expression on enum must be exhaustive or have an else branch")
                }
            }

            expr.branches.forEach { branch ->
                checkType(PrimitiveType.Bool, getActualTypeOrThrow(branch.cond, function), "Branch type is not correct")
                subjectType?.let { expected ->
                    if (!branch.isElse) {
                        val actualBranchExpr = (branch.cond as BinaryExpression).right
                        val branchExprType = getActualTypeOrThrow(actualBranchExpr, function)
                        if (!branchExprType.isAssignable(expected)) {
                            throw TypeAnalysisException("Branch condition type $branchExprType not compatible with subject type $expected")
                        }
                    }
                }
                checkCodeBlock(function, branch.block.code, isWhenBranch = true)
            }
        }

        val allBranchesReturnExpression = expr.branches.all {
            it.block.code.lastOrNull() is ExpressionStatement
        }

        return if (allBranchesReturnExpression && expr.branches.isNotEmpty()) {
            val branchTypes = expr.branches.map {
                val branchExpr = (it.block.code.last() as ExpressionStatement).expression
                getActualTypeOrThrow(branchExpr, function)
            }
            val unifiedType = branchTypes.reduce { left, right ->
                unifyTypes(left, right) ?: throw TypeAnalysisException("When branches return different types.")
            }
            unifiedType
        } else {
            PrimitiveType.Void
        }
    }

    private fun figureOutBinaryExprReturn(expr: BinaryExpression, function: Function?): Type {
        val leftType = getActualTypeOrThrow(expr.left, function)
        val rightType = getActualTypeOrThrow(expr.right, function)

        return when (expr.operator) {
            BinaryOperator.ADD -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    if (leftType == PrimitiveType.Float || rightType == PrimitiveType.Float) PrimitiveType.Float else PrimitiveType.Integer
                } else {
                    throw TypeAnalysisException("Operator '+' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY,
            BinaryOperator.DIVIDE,
            BinaryOperator.MODULO -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    if (leftType == PrimitiveType.Float || rightType == PrimitiveType.Float) PrimitiveType.Float else PrimitiveType.Integer
                } else {
                    throw TypeAnalysisException("Operator '${expr.operator.symbol}' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    PrimitiveType.Bool
                } else {
                    throw TypeAnalysisException("Comparison operator '${expr.operator.symbol}' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL -> {
                if (leftType.asNonNull() == rightType.asNonNull() ||
                    (isNumeric(leftType) && isNumeric(rightType)) ||
                    (leftType is NullableType && rightType == PrimitiveType.Null) ||
                    (rightType is NullableType && leftType == PrimitiveType.Null)
                ) {
                    PrimitiveType.Bool
                } else {
                    throw TypeAnalysisException("Cannot compare $leftType and $rightType for equality")
                }
            }

            BinaryOperator.AND,
            BinaryOperator.OR -> {
                if (leftType == PrimitiveType.Bool && rightType == PrimitiveType.Bool) {
                    PrimitiveType.Bool
                } else {
                    throw TypeAnalysisException("Logical operator '${expr.operator.symbol}' requires boolean operands, but got $leftType and $rightType")
                }
            }
        }
    }

    private fun isNumeric(type: Type): Boolean {
        return type == PrimitiveType.Integer || type == PrimitiveType.Float
    }
}