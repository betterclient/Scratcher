package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.MemoryLib
import org.antlr.v4.kotlinruntime.ast.Position

class AllocAndFreeStackAdder(
    val stackParameter: Parameter,
    val func: Function,
    val hasLocalsMap: Map<Function, Boolean>
) : ASTVisitor() {

    fun run() {
        visit(func, this)
    }

    private fun createExitStatements(): List<Statement> {
        if (hasLocalsMap[func] != true) return emptyList()

        val freePtr: Expression = if (CompilationContext.options.gc.markAndSweepGC) {
            BinaryExpression(
                left = ParameterExpression(stackParameter, null),
                right = IntLiteral(java.math.BigInteger.ONE, null),
                operator = BinaryOperator.SUBTRACT,
                position = null
            )
        } else {
            ParameterExpression(stackParameter, null)
        }

        val freeSize: Expression = TemporaryStackSizeExpression(
            func,
            includeGcHeader = CompilationContext.options.gc.markAndSweepGC,
            position = null
        )

        val freeStmt = TemporaryCallStatement(
            MemoryLib.free,
            args = mutableListOf(freePtr, freeSize),
            position = null
        )
        val deleteStmt = TemporaryScratchStmt(listOf(ParameterExpression(stackParameter, null)), { scratchExpressions ->
            if (CompilationContext.options.gc.markAndSweepGC) {
                listOf(ListStatements.DeleteItem(
                    GCLib.rootsList,
                    ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])
                ))
            } else emptyList()
        }, null)
        return listOf(deleteStmt, freeStmt)
    }

    override fun visitTemporaryCallStatement(
        func: Function,
        args: MutableList<Expression>,
        position: Position?
    ): Statement? {
        if (func is StandardLibASTFunction) {
            return super.visitTemporaryCallStatement(func, args, position)
        }

        if (hasLocalsMap[func] != true) {
            return TemporaryCallStatement(func, (listOf(NullExpression) + args).toMutableList(), position)
        } else {
            val result = mutableListOf<Statement>()
            val allocVar = LocalVariable(obfuscate("stackAllocationFor${func.name}Call"), PrimitiveType.Integer)
            result.add(VariableStatement(null, allocVar, null))
            result.add(TemporaryCallStatement(
                MemoryLib.alloc,
                mutableListOf<Expression>(
                    TemporaryStackSizeExpression(func, position = null),
                    TemporaryLocalVariableIndexExpression(allocVar, null)
                ).also {
                    if (CompilationContext.options.gc.markAndSweepGC) {
                        it.add(1, TemporaryStackNameExpression(func, null))
                    }
                },
                position = null
            ))
            if (CompilationContext.options.gc.markAndSweepGC) {
                result.add(TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar, null)), { scratchArgs ->
                    listOf(
                        ListStatements.AddToList(GCLib.rootsList, scratchArgs[0])
                    )
                }, null))
            }
            result.add(TemporaryCallStatement(func, (listOf(LocalVariableExpression(allocVar, null)) + args).toMutableList(), null))

            addStatements(result)
            return null
        }
    }

    override fun visitReturnStatement(expression: Expression?, position: Position?): Statement {
        val cleanups = createExitStatements()
        return CompositeStatement(cleanups + ReturnStatement(expression, position), position)
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val isMainBlock = block === func.code
        super.visitCodeBlock(block)

        if (isMainBlock) {
            val lastStmt = block.code.lastOrNull()
            val endsWithReturn = lastStmt is ReturnStatement ||
                    (lastStmt is CompositeStatement && lastStmt.statements.lastOrNull() is ReturnStatement)

            if (!endsWithReturn) {
                block.code.addAll(createExitStatements())
            }
        }
        return block
    }
}