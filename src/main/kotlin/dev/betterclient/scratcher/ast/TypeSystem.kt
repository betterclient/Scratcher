package dev.betterclient.scratcher.ast

import dev.betterclient.scratcher.std.StandardLibASTGenerator

sealed interface Type {
    val isPrimitive: Boolean
    fun asNullable(): Type = this as? NullableType ?: NullableType(this)
    fun asNonNull(): Type = if (this is NullableType) this.inner.asNonNull() else this
    fun isAssignable(other: Type): Boolean
    fun toSafeString(): String
}

data class NullableType(
    val inner: Type
) : Type {
    init {
        if (inner is NullableType) throw GeneralCompilerException("Double nullability is not allowed")
    }
    override val isPrimitive = false

    override fun isAssignable(other: Type): Boolean {
        if (other == PrimitiveType.Null) return true
        val targetInner = inner.asNonNull()
        val otherInner = other.asNonNull()
        if ((targetInner == PrimitiveType.Str || targetInner.toString() == "str") &&
            (otherInner == PrimitiveType.Str || otherInner.toString() == "str")) {
            return true
        }
        return other is NullableType && this.inner.isAssignable(other.inner)
    }

    override fun toString(): String {
        return "$inner?"
    }

    override fun asNonNull(): Type {
        if (inner.toString() == "str") return PrimitiveType.Str
        return super.asNonNull()
    }

    override fun toSafeString() = "${inner.toSafeString()}?"
}

sealed interface PrimitiveType : Type {
    override val isPrimitive: Boolean get() = true

    override fun isAssignable(other: Type): Boolean {
        if (this == Null && other is NullableType) return true

        val target = other.asNonNull()
        if (this == target) return true

        if (this == Integer && target == Float) return true

        return false
    }

    object Str : PrimitiveType {
        override fun isAssignable(other: Type): Boolean {
            if (other == Null) return false
            val target = other.asNonNull()
            if (target == Char) return true
            return target == Str || target.toString() == "str"
        }
        override fun toString() = "str"
    }

    object Char : PrimitiveType {
        override fun toString() = "char"
        override fun isAssignable(other: Type): Boolean {
            if (other == Null) return false
            val target = other.asNonNull()

            if (Str.isAssignable(other)) return true
            return target == Char
        }
    }

    object Integer : PrimitiveType { override fun toString() = "int" }
    object Float : PrimitiveType { override fun toString() = "float" }
    object Bool : PrimitiveType { override fun toString() = "bool" }
    object Void : PrimitiveType { override fun toString() = "void" }
    object Null : PrimitiveType { override fun toString() = "null" }
    object Auto : PrimitiveType { override fun toString() = "auto" }

    override fun toSafeString() = toString()
}

data class SimpleType(
    val name: String,
    val sourceAST: ASTFile
) : Type {
    override val isPrimitive: Boolean get() = false

    override fun isAssignable(other: Type): Boolean {
        return this == other.asNonNull()
    }

    override fun toString(): String {
        if (sourceAST == StandardLibASTGenerator.compilerLib && name == "StringBox") return "str"
        return "${sourceAST.simplePath}::$name"
    }

    override fun toSafeString() = toString()
}

data class ArrayType(
    val elementType: Type
) : Type {
    override val isPrimitive: Boolean = false

    override fun isAssignable(other: Type): Boolean {
        if (this == other) return true

        val baseOther = other.asNonNull() as? ArrayType ?: return false
        return this.elementType == baseOther.elementType
    }

    override fun toString(): String {
        return "$elementType[]"
    }

    fun raw(): Type {
        var self: Type = this
        while (self is ArrayType) {
            self = self.elementType
        }
        return self
    }

    override fun toSafeString() = "${elementType.toSafeString()}[]"
}

data class FunctionType(
    val parameterTypes: List<Type>,
    val returnType: Type
) : Type {
    override val isPrimitive: Boolean = false
    override fun isAssignable(other: Type): Boolean {
        if (this == other) return true

        val target = other.asNonNull() as? FunctionType ?: return false

        if (this.parameterTypes.size != target.parameterTypes.size) return false
        for (i in this.parameterTypes.indices) {
            if (this.parameterTypes[i] != target.parameterTypes[i]) {
                return false
            }
        }

        return this.returnType.isAssignable(target.returnType)
    }

    override fun toString(): String {
        val params = parameterTypes.joinToString(", ") { it.toString() }
        return "($params) -> $returnType"
    }

    companion object {
        fun from(function: Function): FunctionType {
            return FunctionType(
                function.parameters.map { it.type },
                function.returnType
            )
        }
    }

    override fun toSafeString() = "${parameterTypes.joinToString { it.toSafeString() }} ${returnType.toSafeString()}}"
}

data class PlaceholderType(val name: String) : Type {
    override val isPrimitive: Boolean = false

    override fun isAssignable(other: Type): Boolean {
        if (other is PlaceholderType) return this.name == other.name
        if (other is NullableType) return this == other.inner
        return false
    }

    override fun toString(): String = name
    override fun toSafeString() = toString()
}

data class SealedEnumType(
    val name: String,
    val sourceAST: ASTFile,
    val typeBindings: Map<String, Type> = emptyMap()
) : Type {
    override val isPrimitive = false

    override fun isAssignable(other: Type): Boolean {
        val nonNullOther = other.asNonNull()
        if (this == nonNullOther) return true

        val sealedEnum = sourceAST.sealedEnums.find { it.name == this.name && it.typeBindings == this.typeBindings }
            ?: sourceAST.imports.values.flatMap { it.sealedEnums }.find { it.name == this.name && it.typeBindings == this.typeBindings }
            ?: sourceAST.sealedEnums.find { it.name == this.name }
            ?: sourceAST.imports.values.flatMap { it.sealedEnums }.find { it.name == this.name }
            ?: sourceAST.sealedEnumTemplates.find { it.name == this.name }
            ?: sourceAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == this.name }
            ?: return false

        return sealedEnum.types.any { it.type == nonNullOther }
    }

    override fun toString(): String {
        return if (typeBindings.isEmpty()) "${sourceAST.simplePath}::$name"
        else "${sourceAST.simplePath}::$name<${typeBindings.values.joinToString(", ")}>"
    }

    override fun toSafeString(): String {
        if (typeBindings.isEmpty()) return "${sourceAST.simplePath}_$name"
        if (name.contains("@")) return "${sourceAST.simplePath}_$name"
        return "${sourceAST.simplePath}_${name}@${typeBindings.values.joinToString("_"){it.toSafeString()}}"
    }
}

fun unifyTypes(left: Type, right: Type): Type? {
    if (left == right) return left
    if (left == PrimitiveType.Null) return right.asNullable()
    if (right == PrimitiveType.Null) return left.asNullable()

    if (left.isAssignable(right)) return right
    if (right.isAssignable(left)) return left

    val leftNullable = left.asNullable()
    if (right.isAssignable(leftNullable)) return leftNullable

    val rightNullable = right.asNullable()
    if (left.isAssignable(rightNullable)) return rightNullable

    return null
}