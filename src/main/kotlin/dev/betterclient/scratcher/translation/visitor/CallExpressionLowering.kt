package dev.betterclient.scratcher.translation.visitor

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.code.StringBoxing
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.ExceptionLib
import org.antlr.v4.kotlinruntime.ast.Position

class CallExpressionLowering(
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

    override fun visitExpressionStatement(expression: Expression, position: Position?): Statement? {
        return null //expr is lowered, and the "prepend" is already done, so just remove this statement...
    }

    override fun visitReturnStatement(expression: Expression?, position: Position?): Statement {
        if (doingReturnLowering) {
            val returnExpr = expression ?: NullExpression
            addStatements(listOf(
                TemporaryHeapSetStatement(ParameterExpression(returnIndexParameter, null), returnExpr, null)
            ))
            return ReturnStatement(null, position) //codegen still needs a return statement to generate stop(this-script)
        }

        return ReturnStatement(null, position)
    }

    override fun visitCallExpression(func: Function, args: List<Expression>, position: Position?): Expression {
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
                    prepend.add(VariableStatement(null, local, null))

                    val code = func.realCode(args + TemporaryLocalVariableIndexExpression(local, null))

                    prepend.addAll(code.prepend)
                    prepend.add(LocalVariableAssignmentStatement(local, code.expression!!, null))

                    addStatements(prepend)
                    LocalVariableExpression(local, position)
                } else {
                    val code = func.realCode(args)
                    addStatements(code.prepend)
                    code.expression
                }
            }?: NullExpression
        }

        if (isVoid) {
            prepend.add(TemporaryCallStatement(func, args.toMutableList(), position))
        } else {
            if (ignoreReturn) {
                prepend.add(TemporaryCallStatement(func, (args + IntLiteral((-1).toBigInteger(), null)).toMutableList(), position))
            } else {
                val local = LocalVariable(obfuscate("returnFor${func.name}"), func.returnType)
                prepend.add(VariableStatement(null, local, null))
                prepend.add(TemporaryCallStatement(func, (args + TemporaryLocalVariableIndexExpression(local, null)).toMutableList(), position))
                expr = LocalVariableExpression(local, position)
            }
        }

        addStatements(prepend)
        return expr?: NullExpression
    }

    override fun visitStatementExpression(statements: List<Statement>, expression: Expression, position: Position?): Expression {
        addStatements(statements)
        return expression
    }

    override fun visitNonNullAssertExpression(expression: Expression, position: Position?): Expression {
        val type = ExpressionTypes.getExpressionType(expression)
        val checked = visit(CallExpression(
            func = ExceptionLib.assertNonNull,
            arguments = listOf(expression, StringLiteral(if (CompilationContext.options.obfuscation.enabled) {
                "Scratcher runtime error: NullPointerException"
            } else {
                "Scratcher runtime error: NullPointerException at ${func.name}"
            }, position)),
            position = position
        ))
        return if (type is NullableType && type.asNonNull().toString() == "str") {
            MemberExpression(
                checked,
                StringBoxing.stringBoxStruct.parameters.first { it.name == "str" },
                StringBoxing.stringBoxStruct,
                position
            )
        } else {
            checked
        }
    }

    override fun visitEnumLiteral(enum: ASTEnum, value: String, ordinal: Int, position: Position?): Expression {
        return IntLiteral(ordinal.toBigInteger(), position)
    }

    //shit for ignoreReturn (in visitCallExpression)
    private var currentRootExpression: Expression? = null
    private val rootCallFlags = mutableListOf<Boolean>()

    override fun visitStatement(statement: Statement) {
        currentRootExpression = if (statement is ExpressionStatement) {
            statement.expression
        } else {
            null
        }
    }

    override fun visitExpr(expression: Expression) {
        if (expression is CallExpression) {
            rootCallFlags.add(expression === currentRootExpression)
        }
    }
}