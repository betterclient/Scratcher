package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.CompositeStatement
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapGetExpression
import dev.betterclient.scratcher.ast.TemporaryLocalVariableIndexExpression
import dev.betterclient.scratcher.ast.TemporaryScratchStmt
import dev.betterclient.scratcher.ast.TemporaryStackNameExpression
import dev.betterclient.scratcher.ast.TemporaryStackSizeExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.lib.MemoryLib

class AllocAndFreeStackAdder(
    val stackParameter: Parameter,
    val func: Function,
    val hasLocalsMap: Map<Function, Boolean>
) : ASTVisitor() {

    fun run() {
        visit(func, this)
    }

    private fun createExitStatements(): List<Statement> {
        val freeStmt = TemporaryCallStatement(
            MemoryLib.free,
            args = mutableListOf(ParameterExpression(stackParameter), TemporaryStackSizeExpression(func))
        )
        val deleteStmt = TemporaryScratchStmt(listOf(ParameterExpression(stackParameter))) { scratchExpressions ->
            listOf(
                ListStatements.DeleteItem(
                    GCLib.rootsList,
                    ListExpressions.IndexOfItemInList(GCLib.rootsList, scratchExpressions[0])
                )
            )
        }
        return listOf(freeStmt, deleteStmt)
    }

    override fun visitTemporaryCallStatement(func: Function, args: MutableList<Expression>): Statement? {
        if (func is StandardLibASTFunction) {
            return super.visitTemporaryCallStatement(func, args)
        }

        if (hasLocalsMap[func] != true) {
            return TemporaryCallStatement(func, (listOf(NullExpression) + args).toMutableList())
        } else {
            val result = mutableListOf<Statement>()
            val allocVar = LocalVariable(obfuscate("stackAllocationFor${func.name}Call"), Type.int)
            result.add(VariableStatement(null, allocVar))
            result.add(TemporaryCallStatement(
                MemoryLib.alloc,
                mutableListOf(
                    TemporaryStackSizeExpression(func),
                    TemporaryStackNameExpression(func),
                    TemporaryLocalVariableIndexExpression(allocVar)
                )
            ))
            result.add(TemporaryScratchStmt(listOf(LocalVariableExpression(allocVar))) { scratchArgs ->
                listOf(
                    ListStatements.AddToList(GCLib.rootsList, scratchArgs[0])
                )
            })
            result.add(TemporaryCallStatement(func, (listOf(LocalVariableExpression(allocVar)) + args).toMutableList()))

            addStatements(result)
            return null
        }
    }

    override fun visitReturnStatement(expression: Expression?): Statement {
        val cleanups = createExitStatements()
        return CompositeStatement(cleanups + ReturnStatement(expression))
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