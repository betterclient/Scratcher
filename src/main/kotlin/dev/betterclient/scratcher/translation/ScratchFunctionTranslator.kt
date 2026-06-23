package dev.betterclient.scratcher.translation

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
import dev.betterclient.scratcher.ast.StandardLibASTFunction
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
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.codegen.ast.SBoolOperator
import dev.betterclient.scratcher.codegen.ast.SBoolParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchBoolExpression
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.std.MemoryLibrary
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class ScratchFunctionTranslator(
    val original: Function,
    val scratch: ScratchASTFunction,
    val lookup: (Function) -> ScratchASTFunction
) {
    fun run() {
        if (original is StandardLibASTFunction) return //these functions will get translated at call site

        scratch.code.addAll(original.code.code.map { translateStatement(it) })
    }

    private fun translateStatement(stmt: Statement): ScratchStatement {
        return when(stmt) {
            is TemporaryCallStatement -> {
                CallFunction(
                    func = lookup(stmt.func),
                    args = stmt.args.map { translateExpr(it) }
                )
            }
            is TemporaryHeapSetStatement -> {
                ListStatements.ReplaceItem(
                    list = MemoryLibrary.heap,
                    item = translateExpr(stmt.data),
                    index = translateExpr(stmt.index),
                )
            }
            is ReturnStatement -> ControlStatements.StopThisScript()
            is IfElseStatement -> ControlStatements.IfElse(
                condition = translateExpr(stmt.condition) as ScratchBoolExpression, //thank you static analysis!
                thenBlock = stmt.thenBlock.code.map { translateStatement(it) },
                elseBlock = stmt.elseBlock.code.map { translateStatement(it) }
            )
            is IfStatement -> ControlStatements.IfThen(
                condition = translateExpr(stmt.condition) as ScratchBoolExpression,
                block = stmt.thenBlock.code.map { translateStatement(it) }
            )
            is RepeatStatement -> ControlStatements.RepeatTimes(
                amount = translateExpr(stmt.amount),
                block = stmt.block.code.map { translateStatement(it) }
            )
            is WhileStatement -> ControlStatements.RepeatUntil(
                condition = BoolOperatorExpressions.SNotExpression(translateExpr(stmt.condition) as ScratchBoolExpression),
                block = stmt.block.code.map { translateStatement(it) },
            )

            is VariableAssignmentStatement -> TODO("struct...")
            is TLVariableAssignmentStatement -> TODO("top level...")

            is VariableStatement, is LocalVariableAssignmentStatement, is ExpressionStatement -> throw UnsupportedOperationException("unreachable")
        }
    }

    private fun translateExpr(expr: Expression): ScratchExpression {
        return when(expr) {
            is ConcatExpression -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.STRING_CONCAT
            )
            is BinaryExpression -> translateBinaryExpression(expr)
            is UnaryExpression -> when(expr.operator) {
                UnaryOperator.PLUS -> translateExpr(expr.expression)
                UnaryOperator.MINUS -> OperatorExpressions.BinaryExpression(
                    left = "0".scratch,
                    right = translateExpr(expr.expression),
                    operator = OperatorExpressions.BinaryOperator.SUBTRACT
                )
                UnaryOperator.NOT -> BoolOperatorExpressions.SNotExpression(translateExpr(expr.expression) as ScratchBoolExpression)
            }
            is TemporaryHeapGetExpression -> ListExpressions.ItemAtIndex(MemoryLibrary.heap, translateExpr(expr.index))
            is ParameterExpression -> if (expr.parameter.type == Type.bool) SBoolParameterExpression(
                scratch.args[original.parameters.indexOf(expr.parameter)]
            ) else {
                ScratchStringParameterExpression(scratch.args[original.parameters.indexOf(expr.parameter)])
            }

            is NewStructExpression -> TODO()
            is VariableExpression -> TODO()
            is MemberExpression -> TODO()

            is BooleanLiteral -> BoolOperatorExpressions.BinaryExpression(
                operand1 = "1".scratch,
                operand2 = (if (expr.value) "1" else "2").scratch,
                binaryOperator = SBinaryOperator.EQUALS
            )
            is FloatLiteral -> expr.value.toString().scratch
            is IntLiteral -> expr.value.toString().scratch
            is StringLiteral -> expr.value.scratch

            is CallExpression,
            is TemporaryLocalVariableIndexExpression,
            is LocalVariableExpression -> throw UnsupportedOperationException("unreachable")
        }
    }

    private fun translateBinaryExpression(expr: BinaryExpression): ScratchExpression {
        return when(expr.operator) {
            BinaryOperator.ADD -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.ADD
            )
            BinaryOperator.MULTIPLY -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.MULTIPLY
            )
            BinaryOperator.DIVIDE -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.DIVIDE
            )
            BinaryOperator.MODULO -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.MOD
            )
            BinaryOperator.SUBTRACT -> OperatorExpressions.BinaryExpression(
                left = translateExpr(expr.left),
                right = translateExpr(expr.right),
                operator = OperatorExpressions.BinaryOperator.SUBTRACT
            )

            BinaryOperator.LESS_THAN -> BoolOperatorExpressions.BinaryExpression(
                operand1 = translateExpr(expr.left),
                operand2 = translateExpr(expr.right),
                binaryOperator = SBinaryOperator.LT
            )
            BinaryOperator.GREATER_THAN -> BoolOperatorExpressions.BinaryExpression(
                operand1 = translateExpr(expr.left),
                operand2 = translateExpr(expr.right),
                binaryOperator = SBinaryOperator.GT
            )
            BinaryOperator.LESS_EQUAL -> BoolOperatorExpressions.BinaryExpression(
                operand1 = translateExpr(expr.left),
                operand2 = translateExpr(expr.right),
                binaryOperator = SBinaryOperator.LTE
            )
            BinaryOperator.GREATER_EQUAL -> BoolOperatorExpressions.BinaryExpression(
                operand1 = translateExpr(expr.left),
                operand2 = translateExpr(expr.right),
                binaryOperator = SBinaryOperator.GTE
            )

            BinaryOperator.EQUAL -> BoolOperatorExpressions.BinaryExpression(
                operand1 = translateExpr(expr.left),
                operand2 = translateExpr(expr.right),
                binaryOperator = SBinaryOperator.EQUALS
            )
            BinaryOperator.NOT_EQUAL -> BoolOperatorExpressions.SNotExpression(
                BoolOperatorExpressions.BinaryExpression(
                    operand1 = translateExpr(expr.left),
                    operand2 = translateExpr(expr.right),
                    binaryOperator = SBinaryOperator.EQUALS
                )
            )

            BinaryOperator.AND -> BoolOperatorExpressions.SBoolComparisonExpressions(
                operand1 = translateExpr(expr.left) as ScratchBoolExpression,
                operand2 = translateExpr(expr.right) as ScratchBoolExpression,
                operator = SBoolOperator.AND
            )
            BinaryOperator.OR -> BoolOperatorExpressions.SBoolComparisonExpressions(
                operand1 = translateExpr(expr.left) as ScratchBoolExpression,
                operand2 = translateExpr(expr.right) as ScratchBoolExpression,
                operator = SBoolOperator.OR
            )
        }
    }
}