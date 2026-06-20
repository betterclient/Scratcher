package dev.betterclient.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser

class ASTFile(
    val path: String,
    val imports: MutableMap<String, ASTFile> = mutableMapOf(),
    val structs: MutableList<Struct> = mutableListOf(),
    val variables: MutableList<TLVariable> = mutableListOf(),
    val functions: MutableList<Function> = mutableListOf()
) {
    var completedStage1Parsing = false
    var completedTypeAnalysis = false
}

class Struct(
    val name: String,
    val parameters: MutableList<Parameter> = mutableListOf(),
    val sourceAST: ASTFile
) {
    val type = Type(name, sourceAST)
    var parseInfo: ScratcherLangParser.StructDeclContext? = null
}

//sourceAST = null only for built-in types
data class Type(val name: String, val sourceAST: ASTFile?, val inner: Type? = null) {
    companion object {
        val str = Type("str", null)
        val int = Type("int", null)
        val float = Type("float", null)
        val bool = Type("bool", null)
        val void = Type("void", null)
    }
    val isPrimitive: Boolean
        get() = sourceAST == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Type

        if (this === int && other === float) return true //implicit conversion, so sorry for this

        if (name != other.name) return false
        if (sourceAST != other.sourceAST) return false
        if (inner != other.inner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (sourceAST?.hashCode() ?: 0)
        result = 31 * result + (inner?.hashCode() ?: 0)
        return result
    }

    /*fun list(): Type {
        return Type("$name[]", this.sourceAST, this)
    }*/

    override fun toString(): String {
        return name
    }
}

class TLVariable(
    val name: String,
    val mutable: Boolean,
    val type: Type,
    var defaultValue: Expression? = null
) {
    var ctx: ScratcherLangParser.ExpressionContext? = null
}

class Function(
    val name: String,
    val parameters: MutableList<Parameter> = mutableListOf(),
    val returnType: Type,
    val code: CodeBlock = CodeBlock()
) {
    var ctx: ScratcherLangParser.FuncDeclContext? = null
}

class CodeBlock(
    val code: MutableList<Statement> = mutableListOf(),
    val localVariables: MutableList<LocalVariable> = mutableListOf(),
)

class LocalVariable(
    val name: String,
    val type: Type
)

class Parameter(
    val name: String,
    val type: Type
)