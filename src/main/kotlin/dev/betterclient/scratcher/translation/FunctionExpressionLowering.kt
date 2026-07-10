package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.except.GeneralCompilerException
import dev.betterclient.scratcher.except.UnreachableException
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.lib.ExceptionLib

class FunctionExpressionLowering(val context: CompilationContext, val func: Function) {
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
                    if (statement.expression !is CallExpression && statement.expression !is WhenExpression) throw GeneralCompilerException("Non CallExpression inside ExpressionStatement, found ${statement.expression}")
                    if (statement.expression is CallExpression) {
                        replacements[statement] = lowerCallExpr(statement.expression, ignoreReturn = true).prepend
                    } else {
                        replacements[statement] = lowerExpr(statement.expression).prepend
                    }
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
                    val targetExpr = lowerExpr(statement.target)
                    val expr = lowerExpr(statement.assignment)
                    list.addAll(targetExpr.prepend)
                    list.addAll(expr.prepend)
                    list.add(VariableAssignmentStatement(
                        targetExpr.expression!!,
                        statement.variable,
                        statement.struct,
                        expr.expression!!
                    ))
                    replacements[statement] = list
                }
                is VariableStatement -> {
                    if (statement.defaultValue == null) {
                        replacements[statement] = listOf(VariableStatement(null, statement.variable))
                    } else {
                        val list = mutableListOf<Statement>()
                        val expr = lowerExpr(statement.defaultValue)
                        list.addAll(expr.prepend)
                        list.add(VariableStatement(
                            expr.expression!!,
                            statement.variable
                        ))
                        replacements[statement] = list
                    }
                }
                is WhileStatement -> {
                    val list = mutableListOf<Statement>()
                    val expr = lowerExpr(statement.condition)

                    val declarations = expr.prepend.filterIsInstance<VariableStatement>()
                    val updates = expr.prepend.filter { it !is VariableStatement }

                    list.addAll(declarations)
                    list.addAll(updates)

                    val loweredBlock = statement.block.also { lowerBlock(it) }
                    loweredBlock.code.addAll(updates)

                    list.add(WhileStatement(
                        expr.expression!!,
                        loweredBlock,
                    ))
                    replacements[statement] = list
                }
                is TemporaryStatement -> throw UnreachableException()
            }
        }

        val newCode = mutableListOf<Statement>()
        for (statement in code.code) {
            newCode += replacements[statement] ?: listOf(statement)
        }
        code.code.clear()
        code.code.addAll(newCode)
    }

    private fun lowerCallExpr(expression: CallExpression, ignoreReturn: Boolean = false): ExpressionLowerResult {
        val isVoid = expression.func.returnType == Type.void
        val prepend = mutableListOf<Statement>()
        var expr: Expression? = null
        val argsMapped = expression.arguments.map {
            val lowered = lowerExpr(it)
            prepend.addAll(lowered.prepend)
            lowered.expression!!
        }
        if (expression.func is InlineStandardLibFunction) {
            //special handling!!!
            val func = expression.func
            return if (isVoid) {
                val code = func.realCode(argsMapped)
                ExpressionLowerResult(
                    null, prepend + code.prepend
                )
            } else {
                if (func.useLocal) {
                    val local = LocalVariable(obfuscate("returnFor${expression.func.name}"), expression.func.returnType)
                    prepend.add(VariableStatement(null, local))

                    val code = func.realCode(argsMapped + TemporaryLocalVariableIndexExpression(local))

                    prepend.addAll(code.prepend)
                    prepend.add(LocalVariableAssignmentStatement(local, code.expression!!))

                    ExpressionLowerResult(
                        LocalVariableExpression(local), prepend
                    )
                } else {
                    val code = func.realCode(argsMapped)
                    ExpressionLowerResult(
                        code.expression, prepend + code.prepend
                    )
                }
            }
        }

        if (isVoid) {
            prepend.add(TemporaryCallStatement(expression.func, argsMapped.toMutableList()))
        } else {
            if (ignoreReturn) {
                prepend.add(TemporaryCallStatement(expression.func, (argsMapped + IntLiteral((-1).toBigInteger())).toMutableList()))
            } else {
                val local = LocalVariable(obfuscate("returnFor${expression.func.name}"), expression.func.returnType)
                prepend.add(VariableStatement(null, local))
                prepend.add(TemporaryCallStatement(expression.func, (argsMapped + TemporaryLocalVariableIndexExpression(local)).toMutableList()))
                expr = LocalVariableExpression(local)
            }
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
            is NonNullAssertExpression -> {
                val callExpr = CallExpression(
                    func = ExceptionLib.assertNonNull,
                    arguments = listOf(expression.expression, StringLiteral(if (CompilationConstants.OBFUSCATION) {
                        "Scratcher runtime error: NullPointerException"
                    } else {
                        "Scratcher runtime error: NullPointerException at ${func.name}"
                    }))
                )
                lowerCallExpr(callExpr)
            }
            is WhenExpression -> lowerWhenExpr(expression)
            is VariableExpression -> ExpressionLowerResult(expression)
            is BooleanLiteral -> ExpressionLowerResult(expression)
            is FloatLiteral -> ExpressionLowerResult(expression)
            is IntLiteral -> ExpressionLowerResult(expression)
            is StringLiteral -> ExpressionLowerResult(expression)
            is LocalVariableExpression -> ExpressionLowerResult(expression)
            is NullExpression -> ExpressionLowerResult(expression)

            is TemporaryExpression -> throw UnreachableException()
            is EnumLiteral -> ExpressionLowerResult(IntLiteral(expression.ordinal.toBigInteger()))
        }
    }

    private fun lowerWhenExpr(expression: WhenExpression): ExpressionLowerResult {
        val prepend = mutableListOf<Statement>()
        val returnType = ExpressionTypes.getExpressionType(context, expression)

        val tempVar = if (returnType != Type.void) {
            val tv = LocalVariable("whenResult@${getUniqueName()}", returnType)
            func.code.localVariables.add(tv)
            tv
        } else null

        if (expression.subject != null) {
            when (expression.subject) {
                is VariableStatement -> {
                    func.code.localVariables.add(expression.subject.variable)
                    val loweredSubject = lowerExpr(expression.subject.defaultValue!!)
                    prepend.addAll(loweredSubject.prepend)
                    if (loweredSubject.expression != null) {
                        prepend.add(VariableStatement(loweredSubject.expression!!, expression.subject.variable))
                    }
                }
                is TLVariableAssignmentStatement -> {
                    val loweredSubject = lowerExpr(expression.subject.assignment)
                    prepend.addAll(loweredSubject.prepend)
                    if (loweredSubject.expression != null) {
                        prepend.add(TLVariableAssignmentStatement(expression.subject.variable, expression.subject.sourceAST, loweredSubject.expression!!))
                    }
                }
                else -> throw UnreachableException()
            }
        }

        if (tempVar != null) {
            prepend.add(VariableStatement(null, tempVar))
        }

        val ifChain = buildIfChain(expression.branches, 0, tempVar)
        if (ifChain != null) {
            prepend.add(ifChain)
        }

        return ExpressionLowerResult(
            expression = if (tempVar != null) LocalVariableExpression(tempVar) else NullExpression,
            prepend = prepend
        )
    }

    private fun buildIfChain(
        branches: List<WhenBranch>,
        index: Int,
        tempVar: LocalVariable?
    ): Statement? {
        if (index >= branches.size) return null

        val branch = branches[index]
        val condResult = lowerExpr(branch.cond)

        val branchBlock = CodeBlock()
        branchBlock.localVariables.addAll(branch.block.localVariables)
        branchBlock.code.addAll(branch.block.code)

        if (tempVar != null && branchBlock.code.isNotEmpty()) {
            val lastIndex = branchBlock.code.lastIndex
            val lastStmt = branchBlock.code[lastIndex]
            if (lastStmt is ExpressionStatement) {
                branchBlock.code[lastIndex] = LocalVariableAssignmentStatement(tempVar, lastStmt.expression)
            }
        }

        lowerBlock(branchBlock)

        val condStatements = condResult.prepend
        val isLastBranch = index == branches.lastIndex
        val isElseBranch = branch.isElse

        val ifStmt: Statement = if (isLastBranch && isElseBranch) {
            if (branchBlock.code.size == 1) {
                branchBlock.code[0]
            } else {
                CompositeStatement(branchBlock.code)
            }
        } else if (!isLastBranch && branches[index + 1].isElse) {
            val elseBranch = branches[index + 1]
            val elseBlock = CodeBlock()
            elseBlock.localVariables.addAll(elseBranch.block.localVariables)
            elseBlock.code.addAll(elseBranch.block.code)
            if (tempVar != null && elseBlock.code.isNotEmpty()) {
                val lastIndex = elseBlock.code.lastIndex
                val lastStmt = elseBlock.code[lastIndex]
                if (lastStmt is ExpressionStatement) {
                    elseBlock.code[lastIndex] = LocalVariableAssignmentStatement(tempVar, lastStmt.expression)
                }
            }
            lowerBlock(elseBlock)
            IfElseStatement(condResult.expression!!, branchBlock, elseBlock)
        } else if (!isLastBranch) {
            val nextStmt = buildIfChain(branches, index + 1, tempVar)
            val elseBlock = CodeBlock()
            elseBlock.code.add(nextStmt!!)
            IfElseStatement(condResult.expression!!, branchBlock, elseBlock)
        } else {
            IfStatement(condResult.expression!!, branchBlock)
        }

        return if (condStatements.isNotEmpty()) {
            CompositeStatement(condStatements + ifStmt)
        } else {
            ifStmt
        }
    }
}

data class ExpressionLowerResult(
    val expression: Expression?,
    val prepend: List<Statement> = listOf()
)