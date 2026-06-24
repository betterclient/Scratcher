package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.obfuscate

class FunctionExpressionLowering(val func: Function) {
    val returnIndexParameter = Parameter(obfuscate("compiler@${func.name}Return"), Type.int)
    val doingReturnLowering = func.returnType != Type.void

    fun run() {
        if (func is StandardLibASTFunction) return //already lowered!!!
        if (doingReturnLowering) {
            func.parameters.add(returnIndexParameter)
        }

        lowerBlock(func.code)
    }

    private fun lowerBlock(code: CodeBlock) {
        val replacements = mutableMapOf<Statement, List<Statement>>()
        for(statement in code.code) {
            when(statement) {
                is ExpressionStatement -> {
                    if (statement.expression !is CallExpression) throw UnsupportedOperationException("Non CallExpression inside ExpressionStatement")
                    replacements[statement] = lowerCallExpr(statement.expression).prepend //ignore expression cause its top level
                }
                is IfElseStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.condition)
                    list.addAll(expr.prepend)
                    list.add(IfElseStatement(
                        expr.expression!!,
                        statement.thenBlock.also { lowerBlock(it) },
                        statement.elseBlock.also { lowerBlock(it) }
                    ))
                    replacements[statement] = list
                }
                is IfStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.condition)
                    list.addAll(expr.prepend)
                    list.add(IfStatement(
                        expr.expression!!,
                        statement.thenBlock.also { lowerBlock(it) },
                    ))
                    replacements[statement] = list
                }
                is LocalVariableAssignmentStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.assignment)
                    list.addAll(expr.prepend)
                    list.add(LocalVariableAssignmentStatement(
                        statement.variable,
                        expr.expression!!
                    ))
                    replacements[statement] = list
                }
                is RepeatStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.amount)
                    list.addAll(expr.prepend)
                    list.add(RepeatStatement(
                        expr.expression!!,
                        statement.block.also { lowerBlock(it) },
                    ))
                    replacements[statement] = list
                }
                is ReturnStatement -> {
                    if (doingReturnLowering) {
                        val list = mutableListOf<Statement>()
                        val expr = lowerExpr(statement.expression!!) //type checking stage guarantees this is non-null
                        list.addAll(expr.prepend)
                        list.add(TemporaryHeapSetStatement(ParameterExpression(returnIndexParameter), expr.expression!!))
                        list.add(ReturnStatement(null)) //codegen still needs a return statement to generate stop(this-script)
                        replacements[statement] = list
                    }
                }
                is TLVariableAssignmentStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.assignment)
                    list.addAll(expr.prepend)
                    list.add(TLVariableAssignmentStatement(
                        statement.variable,
                        statement.sourceAST,
                        expr.expression!!
                    ))
                    replacements[statement] = list
                }
                is VariableAssignmentStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.assignment)
                    list.addAll(expr.prepend)
                    list.add(VariableAssignmentStatement(
                        statement.variable,
                        statement.struct,
                        expr.expression!!
                    ))
                    replacements[statement] = list
                }
                is VariableStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.defaultValue)
                    list.addAll(expr.prepend)
                    list.add(VariableStatement(
                        expr.expression!!,
                        statement.variable
                    ))
                    replacements[statement] = list
                }
                is WhileStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.condition)
                    list.addAll(expr.prepend)
                    list.add(WhileStatement(
                        expr.expression!!,
                        statement.block.also { lowerBlock(it) },
                    ))
                    replacements[statement] = list
                }
                is TemporaryCallStatement -> throw UnsupportedOperationException("unreachable")
                is TemporaryHeapSetStatement -> throw UnsupportedOperationException("unreachable")
            }
        }

        val newCode = mutableListOf<Statement>()
        for (statement in code.code) {
            newCode += replacements[statement] ?: listOf(statement)
        }
        code.code.clear()
        code.code.addAll(newCode)
    }

    private fun lowerCallExpr(expression: CallExpression): ExpressionLowerResult {
        val isVoid = expression.func.returnType == Type.void
        val prepend = mutableListOf<Statement>()
        var expr: Expression? = null
        val argsMapped = expression.arguments.map {
            val lowered = lowerExpr(it)
            prepend.addAll(lowered.prepend)
            lowered.expression!!
        }
        if (isVoid) {
            prepend.add(TemporaryCallStatement(expression.func, argsMapped.toMutableList()))
        } else {
            val local = LocalVariable(obfuscate("returnFor${expression.func.name}"), expression.func.returnType)
            prepend.add(VariableStatement(IntLiteral(-1), local))
            prepend.add(TemporaryCallStatement(expression.func, (argsMapped + TemporaryLocalVariableIndexExpression(local)).toMutableList()))
            expr = LocalVariableExpression(local)
        }

        return ExpressionLowerResult(
            expression = expr,
            prepend = prepend
        )
    }

    private fun lowerExpr(expression: Expression): ExpressionLowerResult {
        return when(expression) {
            is CallExpression -> lowerCallExpr(expression)
            is BinaryExpression -> {
                val prepend = mutableListOf<Statement>()
                val left = lowerExpr(expression.left).also { prepend.addAll(it.prepend) }
                val right = lowerExpr(expression.right).also { prepend.addAll(it.prepend) }
                ExpressionLowerResult(
                    expression = BinaryExpression(left.expression!!, expression.operator, right.expression!!),
                    prepend = prepend
                )
            }
            is ConcatExpression -> {
                val prepend = mutableListOf<Statement>()
                val left = lowerExpr(expression.left).also { prepend.addAll(it.prepend) }
                val right = lowerExpr(expression.right).also { prepend.addAll(it.prepend) }
                ExpressionLowerResult(
                    expression = ConcatExpression(left.expression!!, right.expression!!),
                    prepend = prepend
                )
            }
            is MemberExpression -> {
                val prepend = mutableListOf<Statement>()
                val left = lowerExpr(expression.expression).also { prepend.addAll(it.prepend) }
                ExpressionLowerResult(
                    expression = MemberExpression(left.expression!!, expression.member, expression.struct),
                    prepend = prepend
                )
            }
            is NewStructExpression -> {
                val prepend = mutableListOf<Statement>()
                val args = expression.args.map { expr -> lowerExpr(expr).also { prepend.addAll(it.prepend) }.expression!! }
                ExpressionLowerResult(
                    expression = NewStructExpression(expression.struct, args),
                    prepend = prepend
                )
            }
            is ParameterExpression -> {
                ExpressionLowerResult(expression)
            }
            is UnaryExpression -> {
                val prepend = mutableListOf<Statement>()
                val left = lowerExpr(expression.expression).also { prepend.addAll(it.prepend) }
                ExpressionLowerResult(
                    expression = UnaryExpression(expression.operator, left.expression!!),
                    prepend = prepend
                )
            }
            is VariableExpression -> ExpressionLowerResult(expression)
            is BooleanLiteral -> ExpressionLowerResult(expression)
            is FloatLiteral -> ExpressionLowerResult(expression)
            is IntLiteral -> ExpressionLowerResult(expression)
            is StringLiteral -> ExpressionLowerResult(expression)
            is LocalVariableExpression -> ExpressionLowerResult(expression)

            is TemporaryLocalVariableIndexExpression -> throw UnsupportedOperationException("unreachable")
            is TemporaryHeapGetExpression -> throw UnsupportedOperationException("unreachable")
        }
    }
}

data class ExpressionLowerResult(
    val expression: Expression?,
    val prepend: List<Statement> = listOf()
)