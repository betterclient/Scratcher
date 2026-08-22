package dev.betterclient.scratcher.sugar

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.TCallGraph
import dev.betterclient.scratcher.optimize.visit
import java.math.BigInteger

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

                return TemporaryHeapGetExpression(
                    BinaryExpression(visitedExpr, BinaryOperator.ADD, IntLiteral(ptrOffset.toBigInteger()))
                )
            }
        })
    }
}
