package dev.betterclient.scratcher.translation.heap

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.gc.*
import dev.betterclient.scratcher.optimize.*

class FunctionLocalsInfo(
    val size: Int,
    val gcInfo: GCInfo,
    val indexMap: Map<LocalVariable, Int>
)

class LocalAllocationCalculator(val func: Function) {
    val varToGroupOffset = mutableMapOf<LocalVariable, Pair<String, Int>>()
    val maxOffsets = mutableMapOf<String, Int>()
    val gcTypeToRepresentativeType = mutableMapOf<String, Type>()

    fun calculate(): FunctionLocalsInfo {
        val defPos = mutableMapOf<LocalVariable, Int>()
        val lastUse = mutableMapOf<LocalVariable, Int>()
        val loops = mutableListOf<Pair<Int, Int>>()
        val loopStack = mutableListOf<Int>()
        var pos = 0

        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY

            override fun visitStatement(statement: Statement) {
                pos++
                if (statement is WhileStatement || statement is RepeatStatement) {
                    loopStack.add(pos)
                }
            }

            override fun visitWhileStatement(condition: Expression, block: CodeBlock): Statement? {
                loops.add(loopStack.removeAt(loopStack.lastIndex) to pos)
                return super.visitWhileStatement(condition, block)
            }

            override fun visitRepeatStatement(amount: Expression, block: CodeBlock): Statement? {
                loops.add(loopStack.removeAt(loopStack.lastIndex) to pos)
                return super.visitRepeatStatement(amount, block)
            }

            override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
                defPos.putIfAbsent(variable, pos)
                lastUse[variable] = maxOf(lastUse[variable] ?: pos, pos)
                return super.visitVariableStatement(defaultValue, variable)
            }

            override fun visitLocalVariableAssignmentStatement(
                variable: LocalVariable,
                assignment: Expression
            ): Statement? {
                lastUse[variable] = maxOf(lastUse[variable] ?: pos, pos)
                return super.visitLocalVariableAssignmentStatement(variable, assignment)
            }

            override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
                lastUse[variable] = maxOf(lastUse[variable] ?: pos, pos)
                return super.visitLocalVariableExpression(variable)
            }

            override fun visitTemporaryLocalVariableIndexExpression(variable: LocalVariable): Expression {
                lastUse[variable] = maxOf(lastUse[variable] ?: pos, pos)
                return super.visitTemporaryLocalVariableIndexExpression(variable)
            }
        })

        var changed = true
        while (changed) {
            changed = false
            for ((loopStart, loopEnd) in loops) {
                for ((variable, definedAt) in defPos) {
                    val usedUntil = lastUse[variable] ?: definedAt
                    if (definedAt < loopStart && usedUntil >= loopStart && usedUntil < loopEnd) {
                        lastUse[variable] = loopEnd
                        changed = true
                    }
                }
            }
        }

        for (variable in blockLocals()) {
            defPos.putIfAbsent(variable, 0)
            lastUse.putIfAbsent(variable, defPos[variable]!!)
        }

        val freePools = mutableMapOf<String, MutableList<Int>>()
        val active = mutableListOf<Active>()

        val ordered = defPos.entries.sortedWith(
            compareBy({ it.value }, { it.key.name }, { it.key.type.toString() })
        )
        for ((variable, definedAt) in ordered) {
            val gcType = getGCType(variable.type)
            gcTypeToRepresentativeType.putIfAbsent(gcType, variable.type)

            val expired = active.filter { it.expiry < definedAt }
            active.removeAll(expired)
            for (slot in expired) {
                freePools.getOrPut(slot.gcType) { mutableListOf() }.add(slot.offset)
            }

            val pool = freePools.getOrPut(gcType) { mutableListOf() }
            val offset = if (pool.isNotEmpty()) {
                pool.removeAt(pool.lastIndex)
            } else {
                val next = maxOffsets[gcType] ?: 0
                maxOffsets[gcType] = next + 1
                next
            }
            varToGroupOffset[variable] = Pair(gcType, offset)
            active.add(Active(variable, gcType, offset, lastUse[variable] ?: definedAt))
        }

        val gcTypes = maxOffsets.keys.sorted()
        val typeStarts = mutableMapOf<String, Int>()
        var currentIndex = 0
        for (gcType in gcTypes) {
            typeStarts[gcType] = currentIndex
            currentIndex += maxOffsets[gcType] ?: 0
        }

        val indexMap = varToGroupOffset.mapValues { (_, pair) ->
            val (gcType, offset) = pair
            typeStarts[gcType]!! + offset
        }

        val flatTypes = mutableListOf<Type>()
        for (gcType in gcTypes) {
            val repType = gcTypeToRepresentativeType[gcType]!!
            val count = maxOffsets[gcType] ?: 0
            repeat(count) {
                flatTypes.add(repType)
            }
        }

        val gcInfo = StackGCInfo(flatTypes, func).also { addGC(it) }
        return FunctionLocalsInfo(currentIndex, gcInfo, indexMap)
    }

    private fun blockLocals(): Set<LocalVariable> {
        val out = mutableSetOf<LocalVariable>()
        visit(func, object : ASTVisitor() {
            override fun shouldVisitCodeBlock(block: CodeBlock) = VisitMode.READ_ONLY
            override fun visitCodeBlock(block: CodeBlock): CodeBlock {
                out.addAll(block.localVariables)
                return super.visitCodeBlock(block)
            }
        })
        return out
    }

    private fun getGCType(type: Type): String {
        return if (type.isPrimitive) {
            "p"
        } else if (type is ArrayType) {
            "${"l".repeat(type.toString().count { '[' == it })}${findGC(type.raw())}"
        } else {
            findGC(type).toString()
        }
    }

    private data class Active(val variable: LocalVariable, val gcType: String, val offset: Int, val expiry: Int)
}