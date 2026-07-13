package dev.betterclient.scratcher.optimize.impl.dynamic

import dev.betterclient.scratcher.ast.BinaryExpression
import dev.betterclient.scratcher.ast.BinaryOperator
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.Optimization
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import java.math.BigInteger

object DynamicDispatchHandler : Optimization("Dynamic dispatch rewriting") {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext): Boolean {
        val literalTypes = mutableMapOf<FunctionType, MutableList<Function>>()
        graph.keys.forEach { countLiterals(it, literalTypes) }
        val dispatchers = literalTypes.keys.zip(
            literalTypes.map { (func, literals) ->
                createDispatcher(func, literals)
            }
        ).toMap()

        graph.keys.forEach {
            rewriteToUseDispatcher(it, dispatchers)
        }

        return true
    }

    private fun rewriteToUseDispatcher(func: Function, dispatchers: Map<FunctionType, DispatcherConfig>) {
        visit(func, object : ASTVisitor() {
            override fun visitDynamicCallExpression(
                function: Expression,
                args: List<Expression>,
                type: FunctionType
            ): Expression {
                val dispatcher = dispatchers[type]!!

                return CallExpression(
                    func = dispatcher.dispatcher,
                    arguments = listOf(function) + args
                )
            }

            override fun visitFunctionLiteral(func: Function): Expression {
                return IntLiteral(dispatchers[FunctionType.from(func)]!!.targets.indexOf(func).toBigInteger())
            }
        })
    }

    private fun createDispatcher(
        func: FunctionType,
        literals: MutableList<Function>
    ): DispatcherConfig {
        val args = func.parameterTypes.mapIndexed { i, it -> Parameter("par$i", it) }
        val pars = listOf(Parameter("funcIndex", PrimitiveType.Integer)) + args

        return DispatcherConfig(
            dispatcher = Function(
                "dispatcher$${getUniqueName()}",
                parameters = pars.toMutableList(),
                returnType = func.returnType,
                sourceAST = StandardLibASTGenerator.dynamicDispatchLib,
                export = false,
                warp = true,
                code = CodeBlock().also {
                    it.code.addAll(generateTree(
                        literals,
                        pars[0],
                        args
                    ))
                }
            ).also {
                StandardLibASTGenerator.dynamicDispatchLib.functions.add(it)
            },
            targets = literals
        )
    }

    private fun countLiterals(func: Function, out: MutableMap<FunctionType, MutableList<Function>>) {
        visit(func, object : ASTVisitor() {
            override fun visitFunctionLiteral(func: Function): Expression {
                out.computeIfAbsent(FunctionType.from(func)) { mutableListOf() }.add(func)
                return super.visitFunctionLiteral(func)
            }
        })
    }

    private fun generateTree(
        literals: List<Function>,
        indexToFind: Parameter,
        args: List<Parameter>,
        lo: BigInteger = 0.toBigInteger(),
        hi: BigInteger = literals.lastIndex.toBigInteger()
    ): List<Statement> {
        if (lo == hi) {
            return if(literals[lo.toInt()].returnType == PrimitiveType.Void) {
                listOf(
                    ExpressionStatement(CallExpression(literals[lo.toInt()], args.map { ParameterExpression(it) })),
                    ReturnStatement(null)
                )
            } else {
                listOf(
                    ReturnStatement(CallExpression(literals[lo.toInt()], args.map { ParameterExpression(it) }))
                )
            }
        }

        val mid = (lo + hi) / BigInteger.TWO

        return listOf(
            IfElseStatement(
                condition = BinaryExpression(
                    left = ParameterExpression(indexToFind),
                    right = IntLiteral(mid),
                    operator = BinaryOperator.LESS_EQUAL
                ),
                thenBlock = CodeBlock().also {
                    it.code.addAll(generateTree(literals, indexToFind, args, lo, mid))
                },
                elseBlock = CodeBlock().also {
                    it.code.addAll(generateTree(literals, indexToFind, args, mid + BigInteger.ONE, hi))
                }
            )
        )
    }

    data class DispatcherConfig(
        val dispatcher: Function,
        val targets: List<Function>
    )
}