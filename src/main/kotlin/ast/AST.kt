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
}

class Struct(
    val name: String,
    val parameters: MutableList<Parameter> = mutableListOf(),
    val sourceAST: ASTFile
) {
    val type = Type(name, sourceAST)
    var parseInfo: ScratcherLangParser.StructDeclContext? = null

    val initFunc = Function(
        $$"compiler$initStruct$$name",
        parameters,
        type
    )
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

    /*fun list(): Type {
        return Type("$name[]", this.sourceAST, this)
    }*/
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