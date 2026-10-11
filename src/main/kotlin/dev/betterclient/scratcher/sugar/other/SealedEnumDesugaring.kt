package dev.betterclient.scratcher.sugar.other

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
import dev.betterclient.scratcher.sugar.CompilerSugar
import org.antlr.v4.kotlinruntime.ast.Position

object SealedEnumDesugaring : CompilerSugar() {
    override fun apply(func: Function, graph: TCallGraph, context: CompilationContext) {
        visit(func, object : ASTVisitor() {
            override fun visitCheckSealedEnumTypeExpression(
                expr: Expression,
                targetVariant: Struct,
                sealedEnum: SealedEnum,
                tag: Int,
                position: Position?
            ): Expression {
                val visitedExpr = visit(expr)
                val tagOffset = if (CompilationContext.options.gc.refCountGC) 1 else 0
                val tagHeap = TemporaryHeapGetExpression(
                    index = if (tagOffset == 0) visitedExpr
                    else BinaryExpression(visitedExpr, BinaryOperator.ADD, IntLiteral(tagOffset.toBigInteger(), position), position),
                    type = null,
                    position = position
                )
                return BinaryExpression(tagHeap, BinaryOperator.EQUAL, IntLiteral(tag.toBigInteger(), position), position)
            }

            override fun visitSealedEnumCastExpression(
                expr: Expression,
                targetVariant: Struct,
                sealedEnum: SealedEnum,
                tag: Int,
                safe: Boolean,
                position: Position?
            ): Expression {
                val visitedExpr = visit(expr)
                val tagOffset = if (CompilationContext.options.gc.refCountGC) 1 else 0
                val ptrOffset = tagOffset + 1
                val enumValue = LocalVariable(
                    "sealedEnumCast@${getUniqueName()}",
                    ExpressionTypes.getExpressionType(visitedExpr)
                )
                val enumValueExpression = LocalVariableExpression(enumValue, position)
                val tagExpression = TemporaryHeapGetExpression(
                    index = BinaryExpression(enumValueExpression, BinaryOperator.ADD, IntLiteral(tagOffset.toBigInteger(), position), position),
                    type = null,
                    position = position
                )

                if (safe) {
                    val outValue = LocalVariable("sealedEnumCast@${getUniqueName()}", targetVariant.type.asNullable())

                    return StatementExpression(
                        statements = listOf(
                            VariableStatement(visitedExpr, enumValue, position),
                            VariableStatement(NullExpression, outValue, position),
                            IfStatement(
                                condition = BinaryExpression(
                                    tagExpression,
                                    BinaryOperator.EQUAL,
                                    IntLiteral(tag.toBigInteger(), position),
                                    position
                                ),
                                thenBlock = CodeBlock().also {
                                    it.code.add(
                                        LocalVariableAssignmentStatement(
                                            variable = outValue,
                                            assignment = TemporaryHeapGetExpression(
                                                index = BinaryExpression(enumValueExpression, BinaryOperator.ADD, IntLiteral(ptrOffset.toBigInteger(), position), position),
                                                type = targetVariant.type,
                                                position = position
                                            ),
                                            position = position
                                        )
                                    )
                                },
                                position = position
                            )
                        ),
                        expression = LocalVariableExpression(outValue, position),
                        position = position
                    )
                }

                return StatementExpression(
                    statements = listOf(
                        VariableStatement(visitedExpr, enumValue, position),
                        IfStatement(
                            condition = BinaryExpression(
                                tagExpression,
                                BinaryOperator.NOT_EQUAL,
                                IntLiteral(tag.toBigInteger(), position),
                                position
                            ),
                            thenBlock = CodeBlock().also {
                                it.code.add(
                                    ExpressionStatement(
                                        CallExpression(
                                            func = ExceptionLib.panic,
                                            arguments = listOf(StringLiteral(if (CompilationContext.options.obfuscation.enabled) {
                                                "Scratcher runtime error: Cast"
                                            } else {
                                                "Scratcher runtime error: Type error when casting to: ${targetVariant.sourceAST.simplePath}::${targetVariant.name}"
                                            }, position)),
                                            position = position
                                        ),
                                        position
                                    )
                                )
                            },
                            position = position
                        )
                    ),
                    expression = TemporaryHeapGetExpression(
                        index = BinaryExpression(enumValueExpression, BinaryOperator.ADD, IntLiteral(ptrOffset.toBigInteger(), position), position),
                        type = targetVariant.type,
                        position = position
                    ),
                    position = position
                )
            }

            override fun visitSealedEnumConstructionExpression(
                sealedEnum: SealedEnum,
                targetVariant: Struct,
                args: List<Expression>,
                position: Position?
            ): Expression {
                val visitedArgs = args.map { visit(it) }
                val wrapper = MemoryLib.ensureVariantAllocFunc(StandardLibASTGenerator.memLib, sealedEnum, targetVariant)
                return CallExpression(wrapper, visitedArgs, position)
            }
        })
    }
}