package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.std.lib.ArrayLib
import org.antlr.v4.kotlinruntime.ast.Position

class TypeAnalysis(val reporter: ErrorReporter, val ctx: CompilationContext, val ast: ASTFile) {
    private var loopDepth = 0
    fun run() {
        ast.completedTypeAnalysis = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedTypeAnalysis) {
                ast.completedTypeAnalysis = true
                TypeAnalysis(reporter, ctx, ast).run()
            }
        }

        (ast.flatImportNames.values + ast.wildcardImportSources).distinct().forEach { ast ->
            if (!ast.completedTypeAnalysis) {
                ast.completedTypeAnalysis = true
                TypeAnalysis(reporter, ctx, ast).run()
            }
        }

        if (CompilationContext.std.lib.containsValue(ast) && !CompilationContext.std.rawLibs.contains(ast)) {
            return
        }

        if (ast.simplePath == "list" || ast.path == "list") return

        internalRun()
    }

    private fun internalRun() {
        for (variable in ast.variables) {
            variable.defaultValue?.let {
                val actualType = getActualTypeOrThrow(it, null)
                if (variable.type == PrimitiveType.Void || variable.type == PrimitiveType.StaticList) {
                    this.error("Variable is of type void.", variable.position)
                }
                if (!actualType.isAssignable(variable.type)) {
                    this.error("Tried to assign $actualType to ${variable.name}, which has type ${variable.type}", it.position)
                }
            }
        }

        val allFunctionsToVerify = ast.functions + ast.templates

        for (function in allFunctionsToVerify) {
            checkCodeBlock(function, function.code.code)

            if (function.returnType != PrimitiveType.Void && !doesBlockGuaranteeReturn(function.code.code)) {
                this.error("Function does not have a guaranteed return", function.ctx?.position)
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
            is ContinueStatement -> true
            is BreakStatement -> true
            else -> false
        }
    }

    private fun checkCodeBlock(
        function: Function?,
        code: List<Statement>,
        expectedReturnType: Type? = function?.returnType,
        isWhenBranch: Boolean = false
    ) {
        for (statement in code) {
            when(statement) {
                is ExpressionStatement -> {
                    getActualTypeOrThrow(statement.expression, function)
                    if (!isWhenBranch && statement.expression !is CallExpression && statement.expression !is WhenExpression && statement.expression !is DynamicCallExpression && statement.expression !is StatementExpression) {
                        this.error("Unsupported top level expression", statement.expression.position)
                    }
                }
                is IfElseStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool as if statement condition", statement.position)
                    checkCodeBlock(function, statement.thenBlock.code, expectedReturnType, isWhenBranch)
                    checkCodeBlock(function, statement.elseBlock.code, expectedReturnType, isWhenBranch)
                }
                is IfStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool as if condition", statement.condition.position)
                    checkCodeBlock(function, statement.thenBlock.code, expectedReturnType, isWhenBranch)
                }
                is LocalVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Assignment type is not correct", statement.assignment.position)
                }
                is RepeatStatement -> {
                    checkType(PrimitiveType.Integer, getActualTypeOrThrow(statement.amount, function), "Repeat statement requires an integer amount", statement.amount.position)
                    loopDepth++
                    checkCodeBlock(function, statement.block.code, expectedReturnType, isWhenBranch)
                    loopDepth--
                }
                is TLVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Top level variable assignment type is not correct", statement.assignment.position)
                }
                is VariableAssignmentStatement -> {
                    checkType(statement.struct.type, getActualTypeOrThrow(statement.target, function), "Assigning to the wrong struct type.", statement.target.position)
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment, function), "Struct variable assignment type is not correct", statement.assignment.position)
                }
                is VariableStatement -> {
                    statement.defaultValue?.let {
                        checkType(statement.variable.type, getActualTypeOrThrow(it, function), "Local variable default value is not expected type", it.position)
                    }
                }
                is WhileStatement -> {
                    checkType(PrimitiveType.Bool, getActualTypeOrThrow(statement.condition, function), "Non bool used as while condition", statement.condition.position)
                    loopDepth++
                    checkCodeBlock(function, statement.block.code, expectedReturnType, isWhenBranch)
                    loopDepth--
                }
                is ReturnStatement -> {
                    val retType = expectedReturnType ?: function?.returnType
                    if (statement.expression == null) {
                        if (retType != null && retType != PrimitiveType.Void) {
                            this.error("Must return a value from a non-void function/lambda.", statement.position)
                        }
                    } else {
                        val exprType = getActualTypeOrThrow(statement.expression, function)
                        if (retType == PrimitiveType.Void) {
                            if (exprType != PrimitiveType.Void) {
                                this.error("Cannot return a value from a void function/lambda.", statement.expression.position)
                            }
                        } else if (retType != null) {
                            checkType(retType, exprType, "Return statement type", statement.expression.position)
                        }
                    }
                }
                is BreakStatement -> {
                    if (loopDepth <= 0) {
                        this.error("Not in a loop!", statement.position)
                    }
                }
                is ContinueStatement -> {
                    if (loopDepth <= 0) {
                        this.error("Not in a loop!", statement.position)
                    }
                }
                is StaticListSetStatement -> {
                    val index = getActualTypeOrThrow(statement.index, function)
                    checkType(PrimitiveType.Integer, index, "Static list add index type is not correct", statement.index.position)
                    val item = getActualTypeOrThrow(statement.value, function)
                    checkType(PrimitiveType.Str, item, "Static list add item type is not correct", statement.value.position)
                }
                is StaticListAddStatement -> {
                    checkType(PrimitiveType.Str, getActualTypeOrThrow(statement.item, function), "Static list add type is not correct", statement.item.position)
                }
                is StaticListClearStatement -> {}
                is StaticListInsertStatement -> {
                    val index = getActualTypeOrThrow(statement.index, function)
                    checkType(PrimitiveType.Integer, index, "Static list insert index type is not correct", statement.index.position)
                    val item = getActualTypeOrThrow(statement.value, function)
                    checkType(PrimitiveType.Str, item, "Static list insert item type is not correct", statement.value.position)
                }
                is StaticListRemoveStatement -> {
                    checkType(PrimitiveType.Integer, getActualTypeOrThrow(statement.index, function), "Static list remove type is not correct", statement.index.position)
                }
                is TemporaryStatement -> {}
            }
        }
    }

    fun checkType(expected: Type, found: Type, errorMessage: String, position: Position?) {
        if (!found.isAssignable(expected)) {
            position?.let { this.error("${errorMessage}, expected \"$expected\" but found \"$found\"", it) }?:
            throw GeneralCompilerException("Expected $expected but found $found, position null")
        }
    }

    private fun getActualTypeOrThrow(expr: Expression, function: Function?): Type {
        return when(expr) {
            is WhenExpression -> {
                figureOutWhenExpressionType(function, expr)
            }
            is LambdaExpression -> {
                figureOutLambdaExpressionType(function, expr)
            }
            is BinaryExpression -> figureOutBinaryExprReturn(expr, function)
            is UnaryExpression -> {
                val operandType = getActualTypeOrThrow(expr.expression, function)
                when (expr.operator) {
                    UnaryOperator.PLUS, UnaryOperator.MINUS -> {
                        if (operandType == PrimitiveType.Integer || operandType == PrimitiveType.Float) {
                            operandType
                        } else {
                            this.error("Cannot apply operator to type $operandType", expr.position)
                            PrimitiveType.Float
                        }
                    }
                    UnaryOperator.NOT -> {
                        if (operandType == PrimitiveType.Bool) {
                            PrimitiveType.Bool
                        } else {
                            this.error("Cannot apply operator to type $operandType", expr.position)
                            PrimitiveType.Bool
                        }
                    }
                }
            }
            is ConcatExpression -> {
                val leftType = getActualTypeOrThrow(expr.left, function)
                val rightType = getActualTypeOrThrow(expr.right, function)

                val leftBase = leftType.asNonNull()
                val rightBase = rightType.asNonNull()

                val leftOk = leftBase is PrimitiveType && leftBase != PrimitiveType.Void && leftBase != PrimitiveType.Null
                val rightOk = rightBase is PrimitiveType && rightBase != PrimitiveType.Void && rightBase != PrimitiveType.Null

                when {
                    leftOk && !rightOk -> {
                        this.error("Cannot concatenate $rightType", expr.right.position)
                    }
                    !leftOk && rightOk -> {
                        this.error("Cannot concatenate $leftType", expr.left.position)
                    }
                    !leftOk && !rightOk -> {
                        this.error("Cannot concatenate types together.", expr.position)
                    }
                }

                PrimitiveType.Str
            }
            is SafeDotExpression -> {
                val structType = getActualTypeOrThrow(expr.target, function)
                if (structType !is NullableType) {
                    this.error("Safe dot with non nullable receiver", expr.target.position)
                }
                checkType(expr.struct.type, structType.asNonNull(), "Safe dot expression struct type does not match expected type!", expr.target.position)

                expr.member.type.asNullable()
            }
            is TypeLiteral -> expr.type
            is CallExpression -> {
                if (ArrayLib.arrayFuncs.contains(expr.func)) {
                    ArrayLib.getActualReturnType(expr) { getActualTypeOrThrow(it, function) }
                } else {
                    expr.arguments.forEachIndexed { index, expression ->
                        checkType(expr.func.parameters[index].type, getActualTypeOrThrow(expression, function), "Call parameter type is not correct (calling ${expr.func.name})", expression.position)
                    }

                    expr.func.returnType
                }
            }
            is DynamicCallExpression -> {
                checkType(
                    expected = expr.type,
                    found = getActualTypeOrThrow(expr.function, function),
                    errorMessage = "Dynamic call expression is not the correct function type",
                    expr.function.position
                )
                expr.arguments.forEachIndexed { index, expression ->
                    checkType(expr.type.parameterTypes[index], getActualTypeOrThrow(expression, function), "Call parameter type is not correct", expression.position)
                }

                expr.type.returnType
            }
            is MemberExpression -> {
                checkType(expr.struct.type, getActualTypeOrThrow(expr.expression, function), "Struct type is not correct", expr.expression.position)
                expr.member.type
            }
            is NonNullAssertExpression -> {
                val innerType = getActualTypeOrThrow(expr.expression, function)
                if (innerType !is NullableType) {
                    this.error("Already non-nullable!", expr.position)
                }
                innerType.asNonNull()
            }
            is NonNullOrElseExpression -> {
                val op1 = getActualTypeOrThrow(expr.operand1, function)
                val op2 = getActualTypeOrThrow(expr.operand2, function)

                if (op1 !is NullableType) {
                    this.reporter.warn(ast, expr.position!!, "Left operand is non null, the right hand side will never run.")
                }

                val unified = unifyTypes(op1.asNonNull(), op2.asNonNull())

                if (unified == null) {
                    this.error("Operand types do not match, found $op1 and $op2", expr.position)
                    op2.asNonNull()
                } else unified
            }
            is StatementExpression -> {
                checkCodeBlock(
                    function = function,
                    code = expr.statements,
                    expectedReturnType = function?.returnType,
                    isWhenBranch = false
                )

                getActualTypeOrThrow(expr.expression, function)
            }

            //these already have their type determined
            is BooleanLiteral -> PrimitiveType.Bool
            is FloatLiteral -> PrimitiveType.Float
            is IntLiteral -> PrimitiveType.Integer
            is StringLiteral -> PrimitiveType.Str
            is NullExpression -> PrimitiveType.Null
            is CharLiteral -> PrimitiveType.Char
            is LocalVariableExpression -> expr.variable.type
            is VariableExpression -> expr.variable.type
            is EnumLiteral -> expr.enum.type
            is ParameterExpression -> expr.parameter.type
            is FunctionLiteral -> FunctionType.from(expr.function)
            is CheckSealedEnumTypeExpression -> {
                val leftType = getActualTypeOrThrow(expr.expr, function)
                val expectedSealed = leftType.asNonNull()
                if (expectedSealed != expr.sealedEnum.type) {
                    this.error("IS check left type $leftType does not match sealed enum ${expr.sealedEnum.type}", expr.position)
                }
                PrimitiveType.Bool
            }
            is SealedEnumCastExpression -> {
                val leftType = getActualTypeOrThrow(expr.expr, function)
                val expectedSealed = leftType.asNonNull()
                if (expectedSealed != expr.sealedEnum.type) {
                    this.error("AS cast left type $leftType does not match sealed enum ${expr.sealedEnum.type}", expr.position)
                }
                expr.targetVariant.type.let {
                    if (expr.safe) it.asNullable()
                    else it
                }
            }
            is SealedEnumConstructionExpression -> {
                if (expr.targetVariant.parameters.size != expr.arguments.size) {
                    this.error("Sealed enum variant ${expr.sealedEnum.name}.${
                        expr.targetVariant.name.substringAfter(".")
                    } expects ${
                        expr.targetVariant.parameters.size
                    } argument(s), got ${
                        expr.arguments.size
                    }", expr.position)
                } else {
                    expr.arguments.forEachIndexed { index, expression ->
                        val paramType = expr.targetVariant.parameters[index].type
                        checkType(paramType, getActualTypeOrThrow(expression, function), "Sealed enum variant parameter type is not correct", expression.position)
                    }
                }

                expr.sealedEnum.type
            }

            is StaticListExpression -> PrimitiveType.StaticList
            is StaticListItemExpression -> {
                checkType(
                    expected = PrimitiveType.Integer,
                    found = getActualTypeOrThrow(expr.index, function),
                    errorMessage = "Static list item expression index type not correct.",
                    position = expr.index.position
                )
                PrimitiveType.Str
            }
            is StaticListLengthExpression -> PrimitiveType.Integer
            is StaticListContainsExpression -> {
                checkType(
                    expected = PrimitiveType.Str,
                    found = getActualTypeOrThrow(expr.item, function),
                    errorMessage = "Static list contains item type not correct.",
                    position = expr.item.position
                )
                PrimitiveType.Bool
            }
            is StaticListItemIndexExpression -> {
                checkType(
                    expected = PrimitiveType.Str,
                    found = getActualTypeOrThrow(expr.item, function),
                    errorMessage = "Static list item index type not correct.",
                    position = expr.item.position
                )
                PrimitiveType.Integer
            }

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
                val isSealedEnum = baseType is SealedEnumType
                if ((isEnum || isSealedEnum) && !ExpressionTypes.isWhenExhaustive(expr) && expr.branches.none { it.isElse }) {
                    val kind = if (isSealedEnum) "sealed enum" else "enum"
                    this.error("When statement/expression on $kind must be exhaustive or have an else branch", expr.position)
                }
            }

            expr.branches.forEach { branch ->
                checkType(PrimitiveType.Bool, getActualTypeOrThrow(branch.cond, function), "Branch type is not correct", branch.cond.position)
                subjectType?.let { expected ->
                    if (!branch.isElse) {
                        when (val cond = branch.cond) {
                            is CheckSealedEnumTypeExpression -> {
                                val expectedSealed = expected.asNonNull()
                                if (expectedSealed != cond.sealedEnum.type) {
                                    this.error("Branch condition sealed enum type ${cond.sealedEnum.type} not compatible with subject type $expected", cond.position)
                                }
                            }
                            is BinaryExpression -> {
                                val actualBranchExpr = cond.right
                                val branchExprType = getActualTypeOrThrow(actualBranchExpr, function)
                                if (!branchExprType.isAssignable(expected)) {
                                    this.error("Branch condition type $branchExprType not compatible with subject type $expected", cond.position)
                                }
                            }
                            else -> {}
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
            val branchReturnExpressions = expr.branches.map {
                (it.block.code.last() as ExpressionStatement).expression
            }
            val branchTypes = branchReturnExpressions.map {
                getActualTypeOrThrow(it, function)
            }
            val unifiedType = branchTypes.reduceIndexed { index, left, right ->
                unifyTypes(left, right) ?: run {
                    this.error("When branches return different types.", branchReturnExpressions[index].position)
                    left
                }
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
            BinaryOperator.ADD,
            BinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY,
            BinaryOperator.MODULO -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    if (leftType == PrimitiveType.Float || rightType == PrimitiveType.Float) PrimitiveType.Float else PrimitiveType.Integer
                } else {
                    this.error("Operator cannot be applied to $leftType and $rightType", expr.position)
                    PrimitiveType.Float
                }
            }

            BinaryOperator.DIVIDE -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    this.error("Operator cannot be applied to $leftType and $rightType", expr.position)
                }

                PrimitiveType.Float
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    PrimitiveType.Bool
                } else {
                    this.error("Operator cannot be applied to $leftType and $rightType", expr.position)
                    PrimitiveType.Bool
                }
            }

            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.STRICT_EQUAL,
            BinaryOperator.STRICT_NOT_EQUAL -> {
                if (leftType.asNonNull() == rightType.asNonNull() ||
                    (isNumeric(leftType) && isNumeric(rightType)) ||
                    (leftType is NullableType && rightType == PrimitiveType.Null) ||
                    (rightType is NullableType && leftType == PrimitiveType.Null)
                ) {
                    PrimitiveType.Bool
                } else {
                    this.error("$leftType and $rightType cannot be compared for equality", expr.position)
                    PrimitiveType.Bool
                }
            }

            BinaryOperator.AND,
            BinaryOperator.OR -> {
                if (leftType == PrimitiveType.Bool && rightType == PrimitiveType.Bool) {
                    PrimitiveType.Bool
                } else {
                    this.error("$leftType and $rightType cannot be compared for equality, expected booleans.", expr.position)
                    PrimitiveType.Bool
                }
            }
        }
    }

    private fun isNumeric(type: Type): Boolean {
        return type == PrimitiveType.Integer || type == PrimitiveType.Float
    }

    private fun figureOutLambdaExpressionType(
        function: Function?,
        expr: LambdaExpression
    ): Type {
        val enclosingLoopDepth = loopDepth
        loopDepth = 0
        return try {
            val returnStatements = collectReturnStatements(expr.block.code)
            val returnTypes = returnStatements.map { stmt ->
                if (stmt.expression != null) {
                    getActualTypeOrThrow(stmt.expression, function)
                } else {
                    PrimitiveType.Void
                }
            }

            var returnType = returnTypes.reduceIndexedOrNull { index, left, right ->
                unifyTypes(left, right) ?: run {
                    this.error("Cannot unify $left and $right, lambda return type unknown.", returnStatements[index].position)
                    left
                }
            } ?: PrimitiveType.Void
            if (returnType == PrimitiveType.Null) {
                returnType = PrimitiveType.Void
            }

            checkCodeBlock(function, expr.block.code, expectedReturnType = returnType)

            if (returnType != PrimitiveType.Void && !doesBlockGuaranteeReturn(expr.block.code)) {
                this.error("Lambda does not have a guaranteed return.", expr.position)
            }
            pruneUnreachableCode(expr.block.code)

            FunctionType(
                expr.parameters.map { it.type },
                returnType
            )
        } finally {
            loopDepth = enclosingLoopDepth
        }
    }

    private fun collectReturnStatements(code: List<Statement>): List<ReturnStatement> {
        val returns = mutableListOf<ReturnStatement>()
        for (statement in code) {
            when (statement) {
                is ReturnStatement -> returns.add(statement)
                is IfStatement -> returns.addAll(collectReturnStatements(statement.thenBlock.code))
                is IfElseStatement -> {
                    returns.addAll(collectReturnStatements(statement.thenBlock.code))
                    returns.addAll(collectReturnStatements(statement.elseBlock.code))
                }
                is WhileStatement -> returns.addAll(collectReturnStatements(statement.block.code))
                is RepeatStatement -> returns.addAll(collectReturnStatements(statement.block.code))
                else -> {}
            }
        }
        return returns
    }

    private fun error(msg: String, position: Position?) {
        position?.let {
            this.reporter.logError(ast, position, msg)
        }?: throw NotImplementedException(msg)
    }
}