package dev.betterclient.scratcher.optimize.impl.dynamic

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.impl.control.FunctionInlining
import dev.betterclient.scratcher.optimize.visit

object LambdaInlining : Optimization("Lambda inlining") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        var modified = false
        visit(func, object : ASTVisitor() {
            override fun visitDynamicCallExpression(
                function: Expression,
                args: List<Expression>,
                type: FunctionType
            ): Expression {
                if (function is LambdaExpression) {
                    modified = true

                    return inlineLambda(function, args, func, type)
                }

                return super.visitDynamicCallExpression(function, args, type)
            }
        })
        return modified
    }

    private fun inlineLambda(
        function: LambdaExpression,
        args: List<Expression>,
        func: Function,
        type: FunctionType
    ): Expression {
        val argsPars = function.parameters.map { Parameter(it.name, it.type, false) }
        val argsZip = function.parameters.zip(argsPars).toMap()

        //rewrite lambda with args into func
        visit(function.block, object : ASTVisitor() {
            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                argsZip[variable]?.let {
                    return ParameterExpression(it)
                }

                return super.visitLocalVariableExpression(variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                if (argsZip[variable] != null) {
                    throw GeneralCompilerException("Lambda parameter ${variable.name} cannot be re-assigned")
                }

                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }
        })

        val outFunc = Function(
            name = "inlinedLambda@${getUniqueName()}",
            parameters = argsPars.toMutableList(),
            returnType = type.returnType,
            code = function.block,
            export = false,
            warp = true,
            operator = false,
            userAccessible = false,
            sourceAST = func.sourceAST,
            isEventListener = false,
            isReceiver = false,
            private = false,
        )

        return FunctionInlining.inline(outFunc, args)
    }
}