package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.ast.NotFoundException
import dev.betterclient.scratcher.ast.UnreachableException
import dev.betterclient.scratcher.gc.findGC
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.std.lib.MemoryLib

class ScratchFunctionTranslator(
    val compilationContext: CompilationContext,
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
                if (stmt.func == ListLib.newList) {
                    val elementType = (stmt.args[0] as TypeLiteral).type
                    val gcString = if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        val lCount = elementType.toString().count { it == '[' } + 1
                        "${"l".repeat(lCount)}${findGC(elementType)}"
                    } else null

                    return listOf(CallFunction(
                        func = lookup(stmt.func),
                        args = listOfNotNull(translateExpr(stmt.args.last()), gcString?.scratch)
                    ))
                }

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

            is VariableAssignmentStatement -> throw UnreachableException()
            is VariableStatement, is LocalVariableAssignmentStatement, is ExpressionStatement, is CompositeStatement -> throw UnreachableException()
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
            is ParameterExpression -> if (expr.parameter.type == PrimitiveType.Bool) SBoolParameterExpression(
                scratch.args[original.parameters.indexOf(expr.parameter)]
            ) else {
                ScratchStringParameterExpression(scratch.args[original.parameters.indexOf(expr.parameter)])
            }
            is NullExpression -> "null".scratch

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
            is DynamicCallExpression,
            is MemberExpression,
            is TemporaryLocalVariableIndexExpression,
            is TemporaryStackNameExpression,
            is TemporaryStackSizeExpression,
            is LocalVariableExpression,
            is EnumLiteral,
            is WhenExpression,
            is FunctionLiteral,
            is NonNullOrElseExpression,
            is SafeDotExpression,
            is TypeLiteral,
            is StatementExpression,
            is NonNullAssertExpression -> throw UnreachableException("$expr")
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