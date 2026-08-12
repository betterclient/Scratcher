package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.code.StringBoxing
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

    override fun visitReturnStatement(expression: Expression?): Statement {
        if (doingReturnLowering) {
            val returnExpr = expression ?: NullExpression
            addStatements(listOf(
                TemporaryHeapSetStatement(ParameterExpression(returnIndexParameter), returnExpr)
            ))
            return ReturnStatement(null) //codegen still needs a return statement to generate stop(this-script)
        }

        return ReturnStatement(null)
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

    override fun visitStatementExpression(statements: List<Statement>, expression: Expression): Expression {
        addStatements(statements)

        if (isInWhileCondition) {
            conditionPrepended.addAll(statements)
        }

        return expression
    }

    override fun visitNonNullAssertExpression(expression: Expression): Expression {
        val type = ExpressionTypes.getExpressionType(context, expression)
        val checked = visit(CallExpression(
            func = ExceptionLib.assertNonNull,
            arguments = listOf(expression, StringLiteral(if (CompilationConstants.OBFUSCATION) {
                "Scratcher runtime error: NullPointerException"
            } else {
                "Scratcher runtime error: NullPointerException at ${func.name}"
            }))
        ))
        return if (type is NullableType && type.asNonNull().toString() == "str") {
            MemberExpression(
                checked,
                StringBoxing.stringBoxStruct.parameters.first { it.name == "str" },
                StringBoxing.stringBoxStruct
            )
        } else {
            checked
        }
    }

    override fun visitEnumLiteral(enum: ASTEnum, value: String, ordinal: Int): Expression {
        return IntLiteral(ordinal.toBigInteger())
    }

    //shit for ignoreReturn (in visitCallExpression)
    private var currentRootExpression: Expression? = null
    private val rootCallFlags = mutableListOf<Boolean>()

    //calling function inside while condition
    private var isInWhileCondition = false
    private val conditionPrepended = mutableListOf<Statement>()

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
        return super.shouldVisitCodeBlock(block)
    }

    override fun visitExpr(expression: Expression) {
        if (expression is CallExpression) {
            rootCallFlags.add(expression === currentRootExpression)
        }
    }
}