package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.UnreachableException
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.ExceptionLib

class CallExpressionLowering(
    val context: CompilationContext,
    val func: Function
) : ASTVisitor() {
    val doingReturnLowering = func.returnType != PrimitiveType.Void
    val returnIndexParameter = Parameter(obfuscate("compiler@return"), PrimitiveType.Integer)

    fun run() {
        if (func is StandardLibASTFunction) return //already lowered!!!

        if (doingReturnLowering) {
            func.parameters.add(returnIndexParameter)
        }
        visit(func, this)
    }

    override fun visitExpressionStatement(expression: Expression): Statement? {
        return null //expr is lowered, and the "prepend" is already done, so just remove this statement...
    }

    override fun visitReturnStatement(expression: Expression?): Statement? {
        if (expression == null || expression == NullExpression) return ReturnStatement(null)

        if (doingReturnLowering) {
            addStatements(listOf(
                TemporaryHeapSetStatement(ParameterExpression(returnIndexParameter), expression)
            ))
            return ReturnStatement(null) //codegen still needs a return statement to generate stop(this-script)
        }

        return super.visitReturnStatement(expression)
    }

    override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
        fun flatten(statement: Statement): List<Statement> {
            return when (statement) {
                is CompositeStatement -> statement.statements.flatMap { flatten(it) }
                else -> listOf(statement)
            }
        }

        val updates = conditionPrepended.flatMap { flatten(it) }.filter { it !is VariableStatement }
        block.code.addAll(updates)

        return super.visitWhileStatement(condition, block)
    }

    override fun visitCallExpression(func: Function, args: List<Expression>): Expression {
        val ignoreReturn = rootCallFlags.removeAt(rootCallFlags.lastIndex)
        val isVoid = func.returnType == PrimitiveType.Void
        val prepend = mutableListOf<Statement>()
        var expr: Expression? = null

        if (func is InlineStandardLibFunction) {
            return if (isVoid) {
                val code = func.realCode(args)
                addStatements(code.prepend)
                null
            } else {
                if (func.useLocal) {
                    val local = LocalVariable(obfuscate("returnFor${func.name}"), func.returnType)
                    prepend.add(VariableStatement(null, local))

                    val code = func.realCode(args + TemporaryLocalVariableIndexExpression(local))

                    prepend.addAll(code.prepend)
                    prepend.add(LocalVariableAssignmentStatement(local, code.expression!!))

                    addStatements(prepend)
                    LocalVariableExpression(local)
                } else {
                    val code = func.realCode(args)
                    addStatements(code.prepend)
                    code.expression
                }
            }?: NullExpression
        }

        if (isVoid) {
            prepend.add(TemporaryCallStatement(func, args.toMutableList()))
        } else {
            if (ignoreReturn) {
                prepend.add(TemporaryCallStatement(func, (args + IntLiteral((-1).toBigInteger())).toMutableList()))
            } else {
                val local = LocalVariable(obfuscate("returnFor${func.name}"), func.returnType)
                prepend.add(VariableStatement(null, local))
                prepend.add(TemporaryCallStatement(func, (args + TemporaryLocalVariableIndexExpression(local)).toMutableList()))
                expr = LocalVariableExpression(local)
            }
        }

        addStatements(prepend)
        if (isInWhileCondition) {
            conditionPrepended.addAll(prepend)
        }
        return expr?: NullExpression
    }

    override fun visitNonNullAssertExpression(expression: Expression): Expression {
        return visit(CallExpression(
            func = ExceptionLib.assertNonNull,
            arguments = listOf(expression, StringLiteral(if (CompilationConstants.OBFUSCATION) {
                "Scratcher runtime error: NullPointerException"
            } else {
                "Scratcher runtime error: NullPointerException at ${func.name}"
            }))
        ))
    }

    override fun visitNonNullOrElseExpression(operand1: Expression, operand2: Expression): Expression {
        val lhs = LocalVariable("nonNullOrElse@LHS@${getUniqueName()}", PrimitiveType.Integer)
        addStatements(listOf(
            VariableStatement(operand1, lhs),
            IfStatement(BinaryExpression(
                operator = BinaryOperator.EQUAL,
                left = LocalVariableExpression(lhs),
                right = NullExpression
            ), thenBlock = CodeBlock().also { cb ->
                val b = currentBlock.also { currentBlock = cb }
                cb.code.add(
                    LocalVariableAssignmentStatement(lhs, visit(operand2))
                )
                currentBlock = b
            })
        ))

        return LocalVariableExpression(lhs)
    }

    override fun visitEnumLiteral(enum: ASTEnum, value: String, ordinal: Int): Expression {
        return IntLiteral(ordinal.toBigInteger())
    }

    override fun visitWhenExpr(branches: List<WhenBranch>, subject: Statement?): Expression {
        val prepend = mutableListOf<Statement>()

        val tempWhenExpr = WhenExpression(subject, branches)
        val returnType = ExpressionTypes.getExpressionType(context, tempWhenExpr)

        val tempVar = if (returnType != PrimitiveType.Void) {
            val tv = LocalVariable("whenResult@${getUniqueName()}", returnType)
            func.code.localVariables.add(tv)
            tv
        } else null

        isInsideWhenExpressionBranch = false

        val loweredBranches = branches.map { branch ->
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

            visitCodeBlock(branchBlock)

            WhenBranch(
                branch.cond,
                branchBlock,
                branch.isElse
            )
        }

        if (subject != null) {
            when (subject) {
                is VariableStatement -> {
                    func.code.localVariables.add(subject.variable)
                    val loweredSubject = visit(subject.defaultValue!!)
                    if (loweredSubject != NullExpression) {
                        prepend.add(VariableStatement(loweredSubject, subject.variable))
                    }
                }
                is TLVariableAssignmentStatement -> {
                    val loweredSubject = visit(subject.assignment)
                    if (loweredSubject != NullExpression) {
                        prepend.add(TLVariableAssignmentStatement(subject.variable, subject.sourceAST, loweredSubject))
                    }
                }
                else -> throw UnreachableException()
            }
        }

        if (tempVar != null) {
            prepend.add(VariableStatement(null, tempVar))
        }

        val ifChain = buildIfChain(loweredBranches, 0, tempVar)
        if (ifChain != null) {
            prepend.add(ifChain)
        }
        addStatements(prepend)

        if (isInWhileCondition) {
            conditionPrepended.addAll(prepend)
        }

        return if (tempVar != null) LocalVariableExpression(tempVar) else NullExpression
    }

    private fun buildIfChain(
        branches: List<WhenBranch>,
        index: Int,
        tempVar: LocalVariable?
    ): Statement? {
        if (index >= branches.size) return null

        val branch = branches[index]
        val condResult = branch.cond
        val branchBlock = branch.block

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
            val elseBlock = elseBranch.block
            IfElseStatement(condResult, branchBlock, elseBlock)
        } else if (!isLastBranch) {
            val nextStmt = buildIfChain(branches, index + 1, tempVar)
            val elseBlock = CodeBlock()
            elseBlock.code.add(nextStmt!!)
            IfElseStatement(condResult, branchBlock, elseBlock)
        } else {
            IfStatement(condResult, branchBlock)
        }

        return ifStmt
    }

    //shit for ignoreReturn (in visitCallExpression)
    private var currentRootExpression: Expression? = null
    private val rootCallFlags = mutableListOf<Boolean>()

    //calling function inside while condition
    private var isInWhileCondition = false
    private val conditionPrepended = mutableListOf<Statement>()

    //when expression...
    private var isInsideWhenExpressionBranch = false

    override fun visitStatement(statement: Statement) {
        currentRootExpression = if (statement is ExpressionStatement) {
            statement.expression
        } else {
            null
        }

        if (statement is WhileStatement) {
            isInWhileCondition = true
            conditionPrepended.clear()
        }
    }

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode {
        isInWhileCondition = false
        if (isInsideWhenExpressionBranch) {
            return VisitMode.NONE
        }
        return super.shouldVisitCodeBlock(block)
    }

    override fun visitExpr(expression: Expression) {
        if (expression is WhenExpression) {
            isInsideWhenExpressionBranch = true
        }
        if (expression is CallExpression) {
            rootCallFlags.add(expression === currentRootExpression)
        }
    }
}