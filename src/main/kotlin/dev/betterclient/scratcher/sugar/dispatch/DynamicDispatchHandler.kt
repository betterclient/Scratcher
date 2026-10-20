package dev.betterclient.scratcher.sugar.dispatch

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.sugar.CompilerSugar
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigInteger

object DynamicDispatchHandler : CompilerSugar() {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        val literalTypes = mutableMapOf<FunctionType, MutableSet<Function>>()
        graph.keys.forEach { countLiterals(it, literalTypes) }
        val dispatchers = literalTypes.keys.zip(
            literalTypes.map { (func, literals) ->
                createDispatcher(func, literals.toMutableList())
            }
        ).toMap()

        graph.keys.forEach {
            rewriteToUseDispatcher(it, dispatchers)
        }
    }

    private fun rewriteToUseDispatcher(func: Function, dispatchers: Map<FunctionType, DispatcherConfig>) {
        visit(func, object : ASTVisitor() {
            override fun visitDynamicCallExpression(
                function: Expression,
                args: List<Expression>,
                type: FunctionType,
                position: Position?
            ): Expression {
                val dispatcher = dispatchers.entries.find { (literalType, _) ->
                    literalType.isAssignable(type)
                }?.value ?: throw GeneralCompilerException("No compatible dynamic dispatch target found for signature: $type")

                return CallExpression(
                    func = dispatcher.dispatcher,
                    arguments = listOf(function) + args,
                    position = position
                )
            }

            override fun visitFunctionLiteral(func: Function, position: Position?): Expression {
                return IntLiteral(
                    dispatchers[FunctionType.from(func)]!!.targets.indexOf(func).toBigInteger(),
                    position
                )
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
                sourceAST = CompilationContext.std.dynamicDispatchLib,
                export = false,
                warp = true,
                private = false,
                operator = false,
                code = CodeBlock().also {
                    it.code.addAll(generateTree(
                        literals,
                        pars[0],
                        args
                    ))
                }
            ).also {
                CompilationContext.std.dynamicDispatchLib.functions.add(it)
            },
            targets = literals
        )
    }

    private fun countLiterals(func: Function, out: MutableMap<FunctionType, MutableSet<Function>>) {
        visit(func, object : ASTVisitor() {
            override fun visitFunctionLiteral(func: Function, position: Position?): Expression {
                out.computeIfAbsent(FunctionType.from(func)) { mutableSetOf() }.add(func)
                return super.visitFunctionLiteral(func, position)
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
            val call = CallExpression(
                literals[lo.toInt()],
                args.map { ParameterExpression(it, null) },
                null
            )
            return if (literals[lo.toInt()].returnType == PrimitiveType.Void) {
                listOf(
                    ExpressionStatement(call, null),
                    ReturnStatement(null, null)
                )
            } else {
                listOf(
                    ReturnStatement(call, null)
                )
            }
        }

        val mid = (lo + hi) / BigInteger.TWO

        return listOf(
            IfElseStatement(
                condition = BinaryExpression(
                    left = ParameterExpression(indexToFind, null),
                    right = IntLiteral(mid, null),
                    operator = BinaryOperator.LESS_EQUAL,
                    position = null
                ),
                thenBlock = CodeBlock().also {
                    it.code.addAll(generateTree(literals, indexToFind, args, lo, mid))
                },
                elseBlock = CodeBlock().also {
                    it.code.addAll(generateTree(literals, indexToFind, args, mid + BigInteger.ONE, hi))
                },
                position = null
            )
        )
    }

    data class DispatcherConfig(
        val dispatcher: Function,
        val targets: List<Function>
    )
}