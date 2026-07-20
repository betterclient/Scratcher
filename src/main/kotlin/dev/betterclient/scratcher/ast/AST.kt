package dev.betterclient.scratcher.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.opcode.EventListener
import java.io.File

class ASTFile(
    val path: String,
    val simplePath: String = File(path).nameWithoutExtension,
    val imports: MutableMap<String, ASTFile> = mutableMapOf(),
    val structs: MutableList<Struct> = mutableListOf(),
    val variables: MutableList<TLVariable> = mutableListOf(),
    val functions: MutableList<Function> = mutableListOf(),
    val eventListeners: MutableList<ASTEventListener> = mutableListOf(),
    val enums: MutableList<ASTEnum> = mutableListOf(),
    val templates: MutableList<Function> = mutableListOf(),
    val structTemplates: MutableList<Struct> = mutableListOf(),
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
    val sourceAST: ASTFile,
    val typeParameters: List<String> = emptyList(),
    val typeBindings: Map<String, Type> = emptyMap()
) {
    val type = SimpleType(name, sourceAST)
    val sizeOnHeap: Int
        get() = parameters.size
    var parseInfo: ScratcherLangParser.StructDeclContext? = null
    lateinit var allocFunc: Function

    fun getIndex(parameter: Parameter): Int {
        return parameters.indexOf(parameter)
    }
}

class TLVariable(
    val name: String,
    val mutable: Boolean,
    var type: Type,
    var defaultValue: Expression? = null,
    val sourceAST: ASTFile
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
    val userAccessible: Boolean = true,
    val sourceAST: ASTFile,
    val isEventListener: Boolean = false,
    val typeParameters: List<String> = emptyList(),
    val typeBindings: Map<String, Type> = emptyMap()
) {
    var ctx: ScratcherLangParser.BlockContext? = null
}

class StandardLibASTFunction(
    name: String,
    parameters: MutableList<Parameter> = mutableListOf(),
    val precompiledCode: ScratchASTFunction,
    returnType: Type = PrimitiveType.Void,
    userAccessible: Boolean = true,
    sourceAST: ASTFile
) : Function(
    name, parameters, returnType, CodeBlock(), false, true, userAccessible, sourceAST
)

class InlineStandardLibFunction(
    name: String,
    parameters: MutableList<Parameter> = mutableListOf(),
    returnType: Type = PrimitiveType.Void,
    val realCode: (args: List<Expression>) -> ExpressionLowerResult,
    val useLocal: Boolean = false,
    warp: Boolean, //use for opts
    userAccessible: Boolean = true,
    sourceAST: ASTFile
) : Function(
    name, parameters, returnType, CodeBlock(), false, warp, userAccessible, sourceAST
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

data class ASTEnum(
    val name: String,
    val values: List<String>,
    val sourceAST: ASTFile
) {
    val type = SimpleType(name, sourceAST)
}

data class ExpressionLowerResult(
    val expression: Expression?,
    val prepend: List<Statement> = emptyList(),
)