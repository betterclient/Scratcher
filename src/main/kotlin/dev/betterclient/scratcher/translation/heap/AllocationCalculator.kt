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

class LocalAllocationCalculator(val func: Function) : ASTVisitor() {
    val varToGroupOffset = mutableMapOf<LocalVariable, Pair<String, Int>>()
    private val currentOffsets = mutableMapOf<String, Int>()
    val maxOffsets = mutableMapOf<String, Int>()
    val gcTypeToRepresentativeType = mutableMapOf<String, Type>()

    fun calculate(): FunctionLocalsInfo {
        visitCodeBlock(func.code)

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

    override fun shouldVisitCodeBlock(block: CodeBlock): VisitMode = VisitMode.READ_ONLY

    private fun getGCType(type: Type): String {
        return if (type.isPrimitive) {
            "p"
        } else if (type is ListType) {
            "${"l".repeat(type.toString().count { '[' == it })}${findGC(type.raw())}"
        } else {
            findGC(type).toString()
        }
    }

    override fun visitCodeBlock(block: CodeBlock): CodeBlock {
        val savedOffsets = currentOffsets.toMap()

        for (variable in block.localVariables) {
            val gcType = getGCType(variable.type)
            val offset = currentOffsets[gcType] ?: 0
            varToGroupOffset[variable] = Pair(gcType, offset)
            gcTypeToRepresentativeType[gcType] = variable.type

            val nextOffset = offset + 1
            currentOffsets[gcType] = nextOffset

            val maxSoFar = maxOffsets[gcType] ?: 0
            if (nextOffset > maxSoFar) {
                maxOffsets[gcType] = nextOffset
            }
        }

        super.visitCodeBlock(block)

        currentOffsets.clear()
        currentOffsets.putAll(savedOffsets)
        return block
    }
}