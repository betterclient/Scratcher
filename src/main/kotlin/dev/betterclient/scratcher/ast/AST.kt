package dev.betterclient.scratcher.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.translation.ExpressionLowerResult
import java.io.File

class ASTFile(
    val path: String,
    val simplePath: String = File(path).nameWithoutExtension,
    val imports: MutableMap<String, ASTFile> = mutableMapOf(),
    val structs: MutableList<Struct> = mutableListOf(),
    val variables: MutableList<TLVariable> = mutableListOf(),
    val functions: MutableList<Function> = mutableListOf(),
    val eventListeners: MutableList<ASTEventListener> = mutableListOf()
) {
    var completedStage1Parsing = false
    var completedTypeAnalysis = false
}

class ASTEventListener(
    val event: EventListener,
    val code: CodeBlock,
    val sourceAST: ASTFile
) {
    var ctx: Function? = null
}

class Struct(
    val name: String,
    val parameters: MutableList<Parameter> = mutableListOf(),
    val sourceAST: ASTFile
) {
    val type = Type(name, sourceAST)
    val sizeOnHeap: Int
        get() = parameters.size
    var parseInfo: ScratcherLangParser.StructDeclContext? = null
    lateinit var allocFunc: Function

    fun getIndex(parameter: Parameter): Int {
        return parameters.indexOf(parameter)
    }
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

    /*fun list(): Type {
        return Type("$name[]", this.sourceAST, this)
    }*/

    override fun toString(): String {
        return name
    }

    fun isAssignable(other: Type): Boolean {
        if (other == this) return true

        return when {
            this == int && other == float -> true
            else -> false
        }
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

open class Function(
    val name: String,
    val parameters: MutableList<Parameter> = mutableListOf(),
    var returnType: Type,
    val code: CodeBlock = CodeBlock(),
    val export: Boolean,
    val warp: Boolean,
    val userAccessible: Boolean = true
) {
    var ctx: ScratcherLangParser.BlockContext? = null
}

class StandardLibASTFunction(
    name: String,
    parameters: MutableList<Parameter> = mutableListOf(),
    val precompiledCode: ScratchASTFunction,
    returnType: Type = Type.void,
    userAccessible: Boolean = true
) : Function(
    name, parameters, returnType, CodeBlock(), false, true, userAccessible
)

class InlineStandardLibFunction(
    name: String,
    parameters: MutableList<Parameter> = mutableListOf(),
    returnType: Type = Type.void,
    val realCode: (args: List<Expression>) -> ExpressionLowerResult,
    val useLocal: Boolean = false,
    userAccessible: Boolean = true
) : Function(
    name, parameters, returnType, CodeBlock(), false, true, userAccessible
)

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