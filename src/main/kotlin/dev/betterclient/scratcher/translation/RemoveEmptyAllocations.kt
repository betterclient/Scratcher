package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.IntLiteral
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.TemporaryHeapSetStatement
import dev.betterclient.scratcher.ast.TemporaryScratchStmt
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.std.lib.MemoryLib

class RemoveEmptyAllocations(val function: Function, val functionLocalCount: Int) {
    fun run() {
        run(function.code)
    }

    fun run(code: CodeBlock) {
        for (statement in code.code) {
            when (statement) {
                is WhileStatement -> run(statement.block)
                is IfElseStatement -> {
                    run(statement.thenBlock)
                    run(statement.elseBlock)
                }
                is IfStatement -> run(statement.thenBlock)
                is RepeatStatement -> run(statement.block)

                is VariableStatement, is LocalVariableAssignmentStatement, is ExpressionStatement -> throw UnsupportedOperationException("unreachable")
                is ReturnStatement, is TLVariableAssignmentStatement, is TemporaryCallStatement,
                is TemporaryHeapSetStatement, is VariableAssignmentStatement, is TemporaryScratchStmt -> {}
            }
        }

        code.code.removeAll { statement ->
            if (statement is TemporaryCallStatement) {
                val firstArg = statement.args.getOrNull(0)

                val isAlloc0 = statement.func == MemoryLib.alloc && //alloc
                        firstArg is IntLiteral && //with literal
                        firstArg.value == 0.toBigInteger() //0!!!!

                val isFree0 = statement.func == MemoryLib.free && //free
                        firstArg is ParameterExpression && //with a parameter
                        firstArg.parameter == function.parameters.first() && //stack parameter!!
                        functionLocalCount == 0 //and we don't have any locals

                isAlloc0 || isFree0
            } else {
                false
            }
        }
    }
}