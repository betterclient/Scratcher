package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.LambdaExpression
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.VisitMode
import dev.betterclient.scratcher.optimize.visit

object LambdaParameterCapture : ASTVisitor() {
    fun run(func: Function) {
        rewrite(func)
        visit(func, this)
    }

    private fun rewrite(function: Function) {
        val capturedParams = mutableSetOf<Parameter>()

        val collector = object : ASTVisitor() {
            private var lambdaDepth = 0

            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

            override fun visitLambdaExpression(
                block: CodeBlock,
                arguments: List<LocalVariable>,
                captured: MutableSet<LocalVariable>
            ): Expression {
                lambdaDepth++
                super.visitLambdaExpression(block, arguments, captured)
                lambdaDepth--
                return LambdaExpression(arguments, block, captured)
            }

            override fun visitParameterExpression(parameter: Parameter): Expression {
                if (lambdaDepth > 0 && function.parameters.contains(parameter)) {
                    capturedParams.add(parameter)
                }
                return super.visitParameterExpression(parameter)
            }
        }
        visit(function, collector)

        if (capturedParams.isEmpty()) return

        val paramToLocal = capturedParams.associateWith { param ->
            LocalVariable("captured@param@${param.name}", param.type)
        }

        val rewriter = object : ASTVisitor() {
            override fun visitParameterExpression(parameter: Parameter): Expression {
                paramToLocal[parameter]?.let { local ->
                    return LocalVariableExpression(local)
                }
                return super.visitParameterExpression(parameter)
            }
        }
        visit(function, rewriter)

        val declarations = paramToLocal.map { (param, local) ->
            VariableStatement(ParameterExpression(param), local)
        }
        function.code.code.addAll(0, declarations)
        function.code.localVariables.addAll(paramToLocal.values)
    }

    override fun visitLambdaExpression(
        block: CodeBlock,
        arguments: List<LocalVariable>,
        captured: MutableSet<LocalVariable>
    ): Expression {
        val capturedLambdaArgs = mutableSetOf<LocalVariable>()

        val collector = object : ASTVisitor() {
            private var innerLambdaDepth = 0

            override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

            override fun visitLambdaExpression(
                block: CodeBlock,
                arguments: List<LocalVariable>,
                captured: MutableSet<LocalVariable>
            ): Expression {
                innerLambdaDepth++
                super.visitLambdaExpression(block, arguments, captured)
                innerLambdaDepth--
                return LambdaExpression(arguments, block, captured)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                if (innerLambdaDepth > 0 && arguments.contains(variable)) {
                    capturedLambdaArgs.add(variable)
                }
                return super.visitLocalVariableExpression(variable)
            }
        }
        visit(block, collector)

        if (capturedLambdaArgs.isNotEmpty()) {
            val argToLocal = capturedLambdaArgs.associateWith { arg ->
                LocalVariable("captured@lambda@${arg.name}", arg.type)
            }

            val rewriter = object : ASTVisitor() {
                override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                    argToLocal[variable]?.let { local ->
                        return LocalVariableExpression(local)
                    }
                    return super.visitLocalVariableExpression(variable)
                }
            }
            visit(block, rewriter)

            val declarations = argToLocal.map { (arg, local) ->
                VariableStatement(LocalVariableExpression(arg), local)
            }
            block.code.addAll(0, declarations)
            block.localVariables.addAll(argToLocal.values)
        }

        return super.visitLambdaExpression(block, arguments, captured)
    }
}