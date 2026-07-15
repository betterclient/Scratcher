package dev.betterclient.scratcher.ast

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
        if (inner is PrimitiveType) throw GeneralCompilerException("Primitive types aren't allowed to be nullable")
        if (inner is NullableType) throw GeneralCompilerException("Double nullability is not allowed")
    }
    override val isPrimitive = false

    override fun isAssignable(other: Type): Boolean {
        return other is NullableType && this.inner.isAssignable(other.inner)
    }

    override fun toString(): String {
        return "$inner?"
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

    object Str : PrimitiveType { override fun toString() = "str" }
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
        return "${sourceAST.simplePath}::$name"
    }

    override fun toSafeString() = toString()
}

data class ListType(
    val elementType: Type
) : Type {
    override val isPrimitive: Boolean = false

    override fun isAssignable(other: Type): Boolean {
        if (this == other) return true

        val baseOther = other.asNonNull() as? ListType ?: return false
        return this.elementType == baseOther.elementType
    }

    override fun toString(): String {
        return "$elementType[]"
    }

    fun raw(): Type {
        var self: Type = this
        while (self is ListType) {
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
    override fun isAssignable(other: Type): Boolean = true
    override fun toString(): String = name
    override fun toSafeString() = toString()
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