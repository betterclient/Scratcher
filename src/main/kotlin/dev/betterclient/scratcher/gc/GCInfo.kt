package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type

val gcNames = mutableListOf<GCInfo>()

fun addGC(gcInfo: GCInfo) {
    gcNames.add(gcInfo)
}

sealed class GCInfo
data class StructGCInfo(val type: Type, val struct: Struct) : GCInfo()
data class StackGCInfo(val stack: List<Type>) : GCInfo()

val GCInfo.name
    get() = gcNames.indexOf(this) + 1

fun findGC(struct: Struct): Int {
    return gcNames.find { it is StructGCInfo && it.struct == struct }?.name ?: 0
}

fun findGC(type: Type): Int {
    return gcNames.find { it is StructGCInfo && it.type == type }?.name ?: 0
}