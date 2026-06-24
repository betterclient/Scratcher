package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.ConcatExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.FloatLiteral
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NewStructExpression
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.TemporaryHeapSetStatement
import dev.betterclient.scratcher.ast.TemporaryLocalVariableIndexExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.UnaryExpression
import dev.betterclient.scratcher.ast.UnaryOperator
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.std.StandardLibASTGenerator

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
                val actualType = getActualTypeOrThrow(it)
                if (variable.type == Type.void) throw UnsupportedOperationException("Variable ${ast.simplePath}::${variable.name} is of type void.")
                if (variable.type != actualType) throw UnsupportedOperationException("Tried to assign $actualType to ${variable.name}, which has type ${variable.type}")
            }
        }

        for (function in ast.functions) {
            checkCodeBlock(function, function.code.code)

            if (function.returnType != Type.void && !doesBlockGuaranteeReturn(function.code.code)) {
                throw UnsupportedOperationException("Function ${ast.simplePath}::${function.name} does not have a guaranteed return")
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

    private fun checkCodeBlock(function: Function, code: MutableList<Statement>) {
        for (statement in code) {
            when(statement) {
                is ExpressionStatement -> {
                    //just check if the expressions inside are ok
                    getActualTypeOrThrow(statement.expression)
                    if (statement.expression is NewStructExpression) {
                        throw UnsupportedOperationException("Unused new struct in ${ast.simplePath}::${function.name}")
                    } else if (statement.expression !is CallExpression) {
                        throw UnsupportedOperationException("Unsupported expression as top level. ${statement.expression}")
                    }
                }
                is IfElseStatement -> {
                    checkType(Type.bool, getActualTypeOrThrow(statement.condition), "Non bool as if statement condition")
                    checkCodeBlock(function, statement.thenBlock.code)
                    checkCodeBlock(function, statement.elseBlock.code)
                }
                is IfStatement -> {
                    checkType(Type.bool, getActualTypeOrThrow(statement.condition), "Non bool as if condition")
                    checkCodeBlock(function, statement.thenBlock.code)
                }
                is LocalVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment), "Local variable assignment type is not correct")
                }
                is RepeatStatement -> {
                    checkType(Type.int, getActualTypeOrThrow(statement.amount), "Repeat statement requires an integer amount")
                }
                is TLVariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment), "Top level variable assignment type is not correct")
                }
                is VariableAssignmentStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.assignment), "Struct variable assignment type is not correct")
                }
                is VariableStatement -> {
                    checkType(statement.variable.type, getActualTypeOrThrow(statement.defaultValue), "Local variable default value is not expected type")
                }
                is WhileStatement -> {
                    checkType(Type.bool, getActualTypeOrThrow(statement.condition), "Non bool used as while condition")
                    checkCodeBlock(function, statement.block.code)
                }
                is ReturnStatement -> {
                    if (statement.expression == null) {
                        if (function.returnType != Type.void) {
                            throw UnsupportedOperationException("Must return a value from a non-void function.")
                        }
                    } else {
                        if (function.returnType == Type.void) {
                            throw UnsupportedOperationException("Cannot return a value from a void function.")
                        }
                        checkType(function.returnType, getActualTypeOrThrow(statement.expression), "Return statement type")
                    }
                }

                //unreachable
                is TemporaryCallStatement -> {}
                is TemporaryHeapSetStatement -> {}
            }
        }
    }

    fun checkType(expected: Type, found: Type, errorMessage: String) {
        if (!found.isAssignable(expected)) {
            throw UnsupportedOperationException("$errorMessage, expected $expected, found $found")
        }
    }

    private fun getActualTypeOrThrow(expr: Expression): Type {
        return when(expr) {
            is BinaryExpression -> figureOutBinaryExprReturn(expr)
            is UnaryExpression -> {
                val operandType = getActualTypeOrThrow(expr.expression)
                when (expr.operator) {
                    UnaryOperator.PLUS, UnaryOperator.MINUS -> {
                        if (operandType == Type.int || operandType == Type.float) {
                            operandType
                        } else {
                            throw UnsupportedOperationException("Unary operator '${expr.operator.symbol}' cannot be applied to type $operandType")
                        }
                    }
                    UnaryOperator.NOT -> {
                        if (operandType == Type.bool) {
                            Type.bool
                        } else {
                            throw UnsupportedOperationException("Unary operator '${expr.operator.symbol}' cannot be applied to type $operandType")
                        }
                    }
                }
            }
            is ConcatExpression -> {
                val leftType = getActualTypeOrThrow(expr.left)
                val rightType = getActualTypeOrThrow(expr.right)

                val leftOk = leftType.isPrimitive && leftType != Type.void
                val rightOk = rightType.isPrimitive && rightType != Type.void
                if (!leftOk || !rightOk) throw UnsupportedOperationException("Either $leftType or $rightType isn't concattable")

                Type.str
            }
            is CallExpression -> {
                expr.arguments.forEachIndexed {
                        index, expression -> checkType(expr.func.parameters[index].type, getActualTypeOrThrow(expression), "Call parameter type is not correct")
                }

                expr.func.returnType
            }
            is MemberExpression -> {
                checkType(expr.struct.type, getActualTypeOrThrow(expr.expression), "Struct type is not correct")
                expr.member.type
            }

            //these already have their type determined
            is BooleanLiteral -> Type.bool
            is FloatLiteral -> Type.float
            is IntLiteral -> Type.int
            is StringLiteral -> Type.str
            is LocalVariableExpression -> expr.variable.type
            is VariableExpression -> expr.variable.type
            is ParameterExpression -> expr.parameter.type
            is NewStructExpression -> expr.struct.type
            is TemporaryLocalVariableIndexExpression -> throw UnsupportedOperationException("unreachable")
            is TemporaryHeapGetExpression -> throw UnsupportedOperationException("unreachable")
        }
    }

    private fun figureOutBinaryExprReturn(expr: BinaryExpression): Type {
        val leftType = getActualTypeOrThrow(expr.left)
        val rightType = getActualTypeOrThrow(expr.right)

        return when (expr.operator) {
            BinaryOperator.ADD -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    if (leftType == Type.float || rightType == Type.float) Type.float else Type.int
                } else {
                    throw IllegalArgumentException("Operator '+' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY,
            BinaryOperator.DIVIDE,
            BinaryOperator.MODULO -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    if (leftType == Type.float || rightType == Type.float) Type.float else Type.int
                } else {
                    throw IllegalArgumentException("Operator '${expr.operator.symbol}' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.LESS_THAN,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_EQUAL -> {
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    Type.bool
                } else {
                    throw IllegalArgumentException("Comparison operator '${expr.operator.symbol}' cannot be applied to $leftType and $rightType")
                }
            }

            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL -> {
                // Allows comparison between identical types, or mixed numeric types (int and float)
                if (leftType == rightType || (isNumeric(leftType) && isNumeric(rightType))) {
                    Type.bool
                } else {
                    throw IllegalArgumentException("Cannot compare $leftType and $rightType for equality")
                }
            }

            BinaryOperator.AND,
            BinaryOperator.OR -> {
                if (leftType == Type.bool && rightType == Type.bool) {
                    Type.bool
                } else {
                    throw IllegalArgumentException("Logical operator '${expr.operator.symbol}' requires boolean operands, but got $leftType and $rightType")
                }
            }
        }
    }

    private fun isNumeric(type: Type): Boolean {
        return type == Type.int || type == Type.float
    }
}