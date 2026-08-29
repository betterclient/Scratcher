package dev.betterclient.scratcher.sugar

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ExceptionLib
import dev.betterclient.scratcher.std.lib.MemoryLib

object SealedEnumDesugaring : CompilerSugar() {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        visit(func, object : ASTVisitor() {
            override fun visitCheckSealedEnumTypeExpression(expr: Expression, targetVariant: Struct, sealedEnum: SealedEnum, tag: Int): Expression {
                val visitedExpr = visit(expr)
                val tagOffset = if (CompilationConstants.REFCOUNT_GC) 1 else 0
                val tagHeap = TemporaryHeapGetExpression(
                    if (tagOffset == 0) visitedExpr
                    else BinaryExpression(visitedExpr, BinaryOperator.ADD, IntLiteral(tagOffset.toBigInteger()))
                )
                return BinaryExpression(tagHeap, BinaryOperator.EQUAL, IntLiteral(tag.toBigInteger()))
            }

            override fun visitSealedEnumCastExpression(expr: Expression, targetVariant: Struct, sealedEnum: SealedEnum, tag: Int): Expression {
                val visitedExpr = visit(expr)
                val tagOffset = if (CompilationConstants.REFCOUNT_GC) 1 else 0
                val ptrOffset = tagOffset + 1
                val enumValue = LocalVariable(
                    "sealedEnumCast@${getUniqueName()}",
                    ExpressionTypes.getExpressionType(visitedExpr)
                )
                val enumValueExpression = LocalVariableExpression(enumValue)
                val tagExpression = TemporaryHeapGetExpression(
                    BinaryExpression(enumValueExpression, BinaryOperator.ADD, IntLiteral(tagOffset.toBigInteger()))
                )

                return StatementExpression(
                    statements = listOf(
                        VariableStatement(visitedExpr, enumValue),
                        IfStatement(
                            condition = BinaryExpression(
                                tagExpression,
                                BinaryOperator.NOT_EQUAL,
                                IntLiteral(tag.toBigInteger())
                            ),
                            thenBlock = CodeBlock().also {
                                it.code.add(
                                    ExpressionStatement(
                                        CallExpression(
                                            ExceptionLib.panic,
                                            listOf(StringLiteral(if (CompilationConstants.OBFUSCATION) {
                                                "Scratcher runtime error: Cast"
                                            } else {
                                                "Scratcher runtime error: Type error when casting to: ${targetVariant.sourceAST.simplePath}::${targetVariant.name}"
                                            }))
                                        )
                                    )
                                )
                            }
                        )
                    ),
                    expression = TemporaryHeapGetExpression(
                        BinaryExpression(enumValueExpression, BinaryOperator.ADD, IntLiteral(ptrOffset.toBigInteger()))
                    )
                )
            }

            override fun visitSealedEnumConstructionExpression(sealedEnum: SealedEnum, targetVariant: Struct, args: List<Expression>): Expression {
                val visitedArgs = args.map { visit(it) }
                val wrapper = MemoryLib.ensureVariantAllocFunc(StandardLibASTGenerator.memLib, sealedEnum, targetVariant)
                return CallExpression(wrapper, visitedArgs)
            }
        })
    }
}
