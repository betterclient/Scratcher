package dev.betterclient.scratcher.sugar.other

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes.type
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.sugar.CompilerSugar

object CaseSensitiveEquals : CompilerSugar() {
    override fun apply(
        func: Function,
        graph: TCallGraph,
        context: CompilationContext
    ) {
        visit(func, object : ASTVisitor() {
            override fun visitBinaryExpression(
                left: Expression,
                right: Expression,
                operator: BinaryOperator
            ): Expression {
                if (operator == BinaryOperator.STRICT_EQUAL) {
                    return strictEqual(left, right)
                } else if (operator == BinaryOperator.STRICT_NOT_EQUAL) {
                    return UnaryExpression(
                        operator = UnaryOperator.NOT,
                        expression = strictEqual(left, right),
                    )
                }

                return super.visitBinaryExpression(left, right, operator)
            }
        })
    }

    private fun strictEqual(
        left: Expression,
        right: Expression
    ): Expression {
        val leftType = left.type
        val rightType = right.type

        return when {
            leftType == PrimitiveType.Char && rightType == PrimitiveType.Char -> {
                CallExpression(
                    func = StandardLibASTGenerator.strict_equals.functions.find {
                        it.name == "equalsCC"
                    }!!,
                    arguments = listOf(left, right)
                )
            }
            else -> {
                CallExpression(
                    func = StandardLibASTGenerator.strict_equals.functions.find {
                        it.name == "equalsSS"
                    }!!,
                    arguments = listOf(left, right)
                )
            }
        }
    }
}