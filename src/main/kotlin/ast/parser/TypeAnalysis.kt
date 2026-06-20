package dev.betterclient.ast.parser

import dev.betterclient.ast.ASTFile
import dev.betterclient.ast.BinaryExpression
import dev.betterclient.ast.BinaryOperator
import dev.betterclient.ast.BooleanLiteral
import dev.betterclient.ast.CallExpression
import dev.betterclient.ast.ConcatExpression
import dev.betterclient.ast.Expression
import dev.betterclient.ast.ExpressionStatement
import dev.betterclient.ast.FloatLiteral
import dev.betterclient.ast.Function
import dev.betterclient.ast.IfElseStatement
import dev.betterclient.ast.IfStatement
import dev.betterclient.ast.IntLiteral
import dev.betterclient.ast.LocalVariableAssignmentStatement
import dev.betterclient.ast.LocalVariableExpression
import dev.betterclient.ast.MemberExpression
import dev.betterclient.ast.NewStructExpression
import dev.betterclient.ast.ParameterExpression
import dev.betterclient.ast.RepeatStatement
import dev.betterclient.ast.Statement
import dev.betterclient.ast.StringLiteral
import dev.betterclient.ast.TLVariableAssignmentStatement
import dev.betterclient.ast.Type
import dev.betterclient.ast.UnaryExpression
import dev.betterclient.ast.UnaryOperator
import dev.betterclient.ast.VariableAssignmentStatement
import dev.betterclient.ast.VariableExpression
import dev.betterclient.ast.VariableStatement
import dev.betterclient.ast.WhileStatement
import dev.betterclient.std.StandardLibASTGenerator

class TypeAnalysis(val ctx: CompilationContext, val ast: ASTFile) {
    fun run() {
        ast.completedTypeAnalysis = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedTypeAnalysis) {
                ast.completedTypeAnalysis = true
                TypeAnalysis(ctx, ast).run()
            }
        }

        if (StandardLibASTGenerator.lib.containsValue(ast)) return

        internalRun()
    }

    private fun internalRun() {
        for (variable in ast.variables) {
            variable.defaultValue?.let {
                val actualType = getActualTypeOrThrow(it)
                if (variable.type != actualType) throw UnsupportedOperationException("Tried to assign $actualType to ${variable.name}, which has type ${variable.type}")
            }
        }

        for (function in ast.functions) {
            checkCodeBlock(function, function.code.code)
        }
    }

    private fun checkCodeBlock(function: Function, code: MutableList<Statement>) {
        for (statement in code) {
            when(statement) {
                is ExpressionStatement -> { getActualTypeOrThrow(statement.expression) } //just check if the expressions inside are ok
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
            }
        }
    }

    fun checkType(expected: Type, found: Type, errorMessage: String) {
        if (expected != found) {
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