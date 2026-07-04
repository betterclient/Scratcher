package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type

val gcNames = mutableListOf<GCInfo>()

fun addGC(gcInfo: GCInfo) {
    gcNames.add(gcInfo)
}

sealed class GCInfo {
    abstract fun toGCList(): String
}
data class StructGCInfo(val type: Type, val struct: Struct) : GCInfo() {
    override fun toGCList(): String {
        return struct.parameters.map { itt ->
            val type = itt.type
            if (type.isPrimitive) {
                "p"
            } else if (type.inner != null) {
                "${"l".repeat(type.name.count { '[' == it })}${findGC(type.raw())}"
            } else findGC(type)
        }.joinToString("-")
    }
}
data class StackGCInfo(val stack: List<Type>, val func: Function) : GCInfo() {
    override fun toGCList(): String {
        return stack.map { type ->
            if (type.isPrimitive) {
                "p"
            } else if (type.inner != null) {
                "${"l".repeat(type.name.count { '[' == it })}${findGC(type.raw())}"
            } else findGC(type)
        }.joinToString("-")
    }
}

val GCInfo.name
    get() = gcNames.indexOf(this) + 1

fun findGC(struct: Struct): Int {
    return gcNames.find { it is StructGCInfo && it.struct == struct }?.name ?: -999
}

fun findGC(type: Type): Int {
    if (type.isPrimitive) return 0 //list of primitives would be l0

    val nonNullType = type.asNonNull()
    return gcNames.find { it is StructGCInfo && it.type.asNonNull() == nonNullType }?.name ?: -999
}