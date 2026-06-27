package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CompositeStatement
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
import dev.betterclient.scratcher.ast.NonNullAssertExpression
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.TemporaryHeapSetStatement
import dev.betterclient.scratcher.ast.TemporaryLocalVariableIndexExpression
import dev.betterclient.scratcher.ast.TemporaryScratchExpr
import dev.betterclient.scratcher.ast.TemporaryScratchStmt
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
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.codegen.ast.VariableStatements
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.codegen.wrapper.ScratchVariableValue
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.std.lib.MemoryLib

class ScratchFunctionTranslator(
    val original: Function,
    val scratch: ScratchASTFunction,
    val lookup: (Function) -> ScratchASTFunction,
    val lookupVar: (TLVariable) -> ScratchVariable
) {
    fun run() {
        if (original is StandardLibASTFunction) return //these functions will get translated at call site

        scratch.code.addAll(original.code.code.flatMap { translateStatement(it) })
    }

    private fun translateStatement(stmt: Statement): List<ScratchStatement> {
        val single = when (stmt) {
            is TemporaryCallStatement -> {
                val func = lookup(stmt.func)
                CallFunction(
                    func = func,
                    args = stmt.args.mapIndexed { index, expression ->
                        if (func.args[index].type == ScratchType.BOOL)
                            translateExpr(expression).asBool()
                        else
                            translateExpr(expression)
                    }
                )
            }

            is TemporaryHeapSetStatement -> {
                ListStatements.ReplaceItem(
                    list = MemoryLib.heap,
                    item = translateExpr(stmt.data),
                    index = translateExpr(stmt.index),
                )
            }

            is ReturnStatement -> ControlStatements.Stop(StopMode.THIS_SCRIPT)
            is IfElseStatement -> ControlStatements.IfElse(
                condition = translateExpr(stmt.condition).asBool(),
                thenBlock = stmt.thenBlock.code.flatMap { translateStatement(it) },
                elseBlock = stmt.elseBlock.code.flatMap { translateStatement(it) }
            )

            is IfStatement -> ControlStatements.IfThen(
                condition = translateExpr(stmt.condition).asBool(),
                block = stmt.thenBlock.code.flatMap { translateStatement(it) }
            )

            is RepeatStatement -> ControlStatements.RepeatTimes(
                amount = translateExpr(stmt.amount),
                block = stmt.block.code.flatMap { translateStatement(it) }
            )

            is WhileStatement -> ControlStatements.RepeatUntil(
                condition = BoolOperatorExpressions.SNotExpression(translateExpr(stmt.condition).asBool()),
                block = stmt.block.code.flatMap { translateStatement(it) },
            )

            is TemporaryScratchStmt -> return stmt.stmt(stmt.inputExprs.map { translateExpr(it) })

            is TLVariableAssignmentStatement -> VariableStatements.SetVariableTo(
                lookupVar(stmt.variable),
                translateExpr(stmt.assignment)
            )

            is VariableAssignmentStatement -> throw UnsupportedOperationException("unreachable")
            is VariableStatement, is LocalVariableAssignmentStatement, is ExpressionStatement, is CompositeStatement -> throw UnsupportedOperationException(
                "unreachable"
            )
        }
        return listOf(single)
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
                UnaryOperator.NOT -> BoolOperatorExpressions.SNotExpression(translateExpr(expr.expression).asBool())
            }
            is TemporaryHeapGetExpression -> ListExpressions.ItemAtIndex(MemoryLib.heap, translateExpr(expr.index))
            is ParameterExpression -> if (expr.parameter.type == Type.bool) SBoolParameterExpression(
                scratch.args[original.parameters.indexOf(expr.parameter)]
            ) else {
                ScratchStringParameterExpression(scratch.args[original.parameters.indexOf(expr.parameter)])
            }
            is NullExpression -> "-1".scratch

            is VariableExpression -> ListExpressions.Variable(lookupVar(expr.variable))

            is BooleanLiteral -> {
                val target = if (CompilationConstants.OBFUSCATION) getUniqueName() else "1"
                BoolOperatorExpressions.BinaryExpression(
                    operand1 = target.scratch,
                    operand2 = (if (expr.value) target else {if (CompilationConstants.OBFUSCATION) getUniqueName() else "2"}).scratch,
                    binaryOperator = SBinaryOperator.EQUALS
                )
            }
            is FloatLiteral -> expr.value.toString().scratch
            is IntLiteral -> expr.value.toString().scratch
            is StringLiteral -> expr.value.scratch
            is TemporaryScratchExpr -> {
                val args = expr.inputExprs.map { translateExpr(it) }
                expr.expression(args)
            }

            is CallExpression,
            is MemberExpression,
            is TemporaryLocalVariableIndexExpression,
            is LocalVariableExpression,
            is NonNullAssertExpression -> throw UnsupportedOperationException("unreachable")
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
                operand1 = translateExpr(expr.left).asBool(),
                operand2 = translateExpr(expr.right).asBool(),
                operator = SBoolOperator.AND
            )
            BinaryOperator.OR -> BoolOperatorExpressions.SBoolComparisonExpressions(
                operand1 = translateExpr(expr.left).asBool(),
                operand2 = translateExpr(expr.right).asBool(),
                operator = SBoolOperator.OR
            )
        }
    }

    fun ScratchExpression.asBool(): ScratchBoolExpression {
        return this as? ScratchBoolExpression ?: BoolOperatorExpressions.BinaryExpression(
            operand1 = this,
            operand2 = "true".scratch,
            binaryOperator = SBinaryOperator.EQUALS
        )
    }
}