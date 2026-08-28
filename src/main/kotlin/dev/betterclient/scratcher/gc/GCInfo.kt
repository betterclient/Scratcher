package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.ArrayType
import dev.betterclient.scratcher.ast.SealedEnum
import dev.betterclient.scratcher.ast.SealedEnumType
import dev.betterclient.scratcher.ast.SimpleType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type

val gcNames = mutableListOf<GCInfo>()

fun addGC(gcInfo: GCInfo) {
    gcNames.add(gcInfo)
}

sealed class GCInfo {
    abstract fun toFieldList(): List<String>
}

data class SealedEnumGCInfo(val type: Type, val sealedEnum: SealedEnum) : GCInfo() {
    override fun toFieldList(): List<String> {
        val size = if (CompilationConstants.REFCOUNT_GC) 3 else 2
        return List(size) { i -> if (i == size - 1) "?" else "p" }
    }
}

private fun Type.isEnumType(): Boolean {
    val nonNull = asNonNull()
    if (nonNull !is SimpleType) return false
    if (nonNull.sourceAST.enums.any { enum -> enum.name == nonNull.name }) return true
    for (importedAST in nonNull.sourceAST.imports.values) {
        if (importedAST.enums.any { enum -> enum.name == nonNull.name }) return true
    }
    return false
}

private fun Type.gcFieldDescriptor(): String {
    val nonNull = asNonNull()
    if (nonNull.isPrimitive || nonNull is FunctionType || nonNull.isEnumType()) return "p"
    if (nonNull is ArrayType) {
        val inner = nonNull.raw()
        val prefix = "l".repeat(nonNull.toString().count { '[' == it })
        return if (inner.asNonNull() is SealedEnumType) "${prefix}?" else "$prefix${findGC(inner)}"
    }
    return if (nonNull is SealedEnumType) "?" else findGC(nonNull).toString()
}

data class StructGCInfo(val type: Type, val struct: Struct) : GCInfo() {
    override fun toFieldList(): List<String> {
        return struct.parameters.map { it.type.gcFieldDescriptor() }
    }
}
data class StackGCInfo(val stack: List<Type>, val func: Function) : GCInfo() {
    override fun toFieldList(): List<String> {
        return stack.map { it.gcFieldDescriptor() }
    }
}

val GCInfo.name
    get() = gcNames.indexOf(this) + 1

fun findGC(struct: Struct): Int {
    return gcNames.find { it is StructGCInfo && it.struct == struct }?.name ?: -999
}

fun findGC(type: Type): Int {
    val nonNullType = type.asNonNull()
    if (nonNullType.isPrimitive || nonNullType is FunctionType || nonNullType.isEnumType()) return 0

    if (nonNullType is SealedEnumType) {
        return gcNames.find { it is SealedEnumGCInfo && it.type.asNonNull() == nonNullType }?.name ?: -999
    }
    return gcNames.find { it is StructGCInfo && it.type.asNonNull() == nonNullType }?.name ?: -999
}