package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.SealedEnum
import dev.betterclient.scratcher.ast.SealedEnumType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type

val gcNames = mutableListOf<GCInfo>()

fun addGC(gcInfo: GCInfo) {
    gcNames.add(gcInfo)
}

sealed class GCInfo {
    abstract fun toGCList(): String
}

data class SealedEnumGCInfo(val type: Type, val sealedEnum: SealedEnum) : GCInfo() {
    override fun toGCList(): String {
        val size = if (CompilationConstants.REFCOUNT_GC) 3 else 2
        return List(size) { i -> if (i == size - 1) "?" else "p" }.joinToString("-")
    }
}

private fun Type.gcFieldDescriptor(): String {
    if (isPrimitive || this is FunctionType) return "p"
    if (this is ListType) {
        val inner = raw()
        val prefix = "l".repeat(this.toString().count { '[' == it })
        return if (inner.asNonNull() is SealedEnumType) "${prefix}?" else "$prefix${findGC(inner)}"
    }
    return if (asNonNull() is SealedEnumType) "?" else findGC(this).toString()
}

data class StructGCInfo(val type: Type, val struct: Struct) : GCInfo() {
    override fun toGCList(): String {
        return struct.parameters.joinToString("-") { it.type.gcFieldDescriptor() }
    }
}
data class StackGCInfo(val stack: List<Type>, val func: Function) : GCInfo() {
    override fun toGCList(): String {
        return stack.joinToString("-") { it.gcFieldDescriptor() }
    }
}

val GCInfo.name
    get() = gcNames.indexOf(this) + 1

fun findGC(struct: Struct): Int {
    return gcNames.find { it is StructGCInfo && it.struct == struct }?.name ?: -999
}

fun findGC(type: Type): Int {
    if (type.isPrimitive || type is FunctionType) return 0

    val nonNullType = type.asNonNull()
    if (nonNullType is SealedEnumType) {
        return gcNames.find { it is SealedEnumGCInfo && it.type.asNonNull() == nonNullType }?.name ?: -999
    }
    return gcNames.find { it is StructGCInfo && it.type.asNonNull() == nonNullType }?.name ?: -999
}
