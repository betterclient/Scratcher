package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.code.Generics
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.Key
import dev.betterclient.scratcher.gc.SealedEnumGCInfo
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.gc.gcNames
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import java.io.File
import kotlin.collections.set

class CompilationContext {
    fun generateGCNames() {
        if (!CompilationConstants.MARK_AND_SWEEP_GC) return

        val allStructs = asts.values.flatMap { it.structs } +
                StandardLibASTGenerator.compilerLib.structs +
                StandardLibASTGenerator.lambdaLib.structs

        allStructs.distinct().forEach { struct ->
            if (gcNames.none { it is StructGCInfo && it.struct == struct }) {
                addGC(StructGCInfo(struct.type, struct))
            }
        }

        val allSealedEnums = asts.values.flatMap { it.sealedEnums } +
                StandardLibASTGenerator.compilerLib.sealedEnums +
                StandardLibASTGenerator.lambdaLib.sealedEnums

        allSealedEnums.distinct().forEach { sealed ->
            if (gcNames.none { it is SealedEnumGCInfo && it.sealedEnum == sealed }) {
                addGC(SealedEnumGCInfo(sealed.type, sealed))
            }
        }
    }

    var isPreOptimize = true
    var eventListenerIndex: Int = 0
    val asts = mutableMapOf<String, ASTFile>()
    val types = mutableListOf<Type>(
        PrimitiveType.Str,
        PrimitiveType.Integer,
        PrimitiveType.Float,
        PrimitiveType.Bool,
        PrimitiveType.Char,
        PrimitiveType.Void
    )
}

class ASTReader(val ctx: CompilationContext, source: String, val fullPath: String) {
    val initialRead = read(source)

    fun read(): ASTFile {
        val ast = ASTFile(fullPath)
        ctx.asts[fullPath] = ast

        //first read for types
        for (context in initialRead.topLevelElement().filter { it.structDecl() != null }) {
            val struct = context.structDecl()!!
            val typeParams = struct.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()

            val structAST = Struct(
                name = struct.IDENTIFIER().text,
                sourceAST = ast,
                typeParameters = typeParams
            )
            structAST.parseInfo = struct

            if (typeParams.isNotEmpty()) {
                ast.structTemplates.add(structAST)
            } else {
                ctx.types.add(structAST.type)
                ast.structs.add(structAST)
            }
        }

        for (context in initialRead.topLevelElement().filter { it.enumDecl() != null }) {
            val enum = context.enumDecl()!!
            val enumAST = ASTEnum(
                enum.IDENTIFIER(0)!!.text,
                enum.IDENTIFIER().subList(1, enum.IDENTIFIER().size).map { it.text },
                ast
            )
            ctx.types.add(enumAST.type)
            ast.enums.find { it.name == enumAST.name }?.let {
                throw DuplicateDefinitionException("Duplicate enum definition ${ast.simplePath}::${it.name}")
            }

            ast.enums.add(enumAST)
        }

        val sealedEnumArgMap = mutableMapOf<Struct, ScratcherLangParser.SealedEnumArgContext>()
        val sealedTemplateVariantMap = mutableMapOf<Struct, Pair<SealedEnum, ScratcherLangParser.SealedEnumArgContext>>()

        for (context in initialRead.topLevelElement().filter { it.sealedEnumDecl() != null }) {
            val sealedDecl = context.sealedEnumDecl()!!
            val enumName = sealedDecl.IDENTIFIER().text
            val typeParams = sealedDecl.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()

            val sealedEnumAST = SealedEnum(
                name = enumName,
                types = mutableListOf(),
                sourceAST = ast,
                typeParameters = typeParams
            )
            sealedEnumAST.parseInfo = sealedDecl

            if (typeParams.isNotEmpty()) {
                ast.sealedEnumTemplates.add(sealedEnumAST)
            } else {
                ctx.types.add(sealedEnumAST.type)
                ast.sealedEnums.add(sealedEnumAST)
            }

            for (arg in sealedDecl.sealedEnumArg()) {
                val variantName = arg.IDENTIFIER().text
                val structAST = Struct(
                    name = "$enumName.$variantName",
                    sourceAST = ast
                )
                sealedEnumAST.types.add(structAST)
                if (typeParams.isNotEmpty()) {
                    sealedTemplateVariantMap[structAST] = Pair(sealedEnumAST, arg)
                } else {
                    sealedEnumArgMap[structAST] = arg
                    ctx.types.add(structAST.type)
                    ast.structs.add(structAST)
                }
            }
        }

        for (context in initialRead.importDecl()) {
            parseImport(context, ast)
        }

        //second read for parameters
        (ast.structs.filter { it.parseInfo != null } + ast.structTemplates).forEach { struct ->
            for (field in struct.parseInfo!!.structField()) {
                val type = figureOutType(ctx, ast, field.type(), struct.typeParameters)

                if (type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${struct.name} has an argument with type void.")
                struct.parameters.add(
                    Parameter(
                        field.IDENTIFIER().text,
                        type
                    )
                )
            }
            checkDuplicates(struct.parameters, "struct ${ast.simplePath}::${struct.name}")
            if (struct !in ast.structTemplates) {
                struct.parseInfo = null
            }
        }

        for ((struct, argCtx) in sealedEnumArgMap) {
            val paramList = argCtx.paramList()?.param() ?: emptyList()
            for (param in paramList) {
                val type = figureOutType(ctx, ast, param.type())
                if (type == PrimitiveType.Void) {
                    throw VoidVariableException("${ast.simplePath}::${struct.name} has a field with type void.")
                }
                struct.parameters.add(
                    Parameter(
                        param.IDENTIFIER().text,
                        type
                    )
                )
            }
            checkDuplicates(struct.parameters, "sealed enum variant ${ast.simplePath}::${struct.name}")
        }

        for ((struct, pair) in sealedTemplateVariantMap) {
            val (parentSealed, argCtx) = pair
            val paramList = argCtx.paramList()?.param() ?: emptyList()
            for (param in paramList) {
                val type = figureOutType(ctx, ast, param.type(), parentSealed.typeParameters)
                if (type == PrimitiveType.Void) {
                    throw VoidVariableException("${ast.simplePath}::${struct.name} has a field with type void.")
                }
                struct.parameters.add(
                    Parameter(
                        param.IDENTIFIER().text,
                        type
                    )
                )
            }
            checkDuplicates(struct.parameters, "sealed enum variant ${ast.simplePath}::${struct.name}")
        }

        for (context in initialRead.topLevelElement()) {
            if (context.funcDecl() != null) {
                val func = context.funcDecl()!!
                val typeParams = func.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()
                val hasReceiver = func.type().size > 1
                val returnTypeCtx = func.type(0)!!
                val receiverTypeCtx = if (hasReceiver) func.type(1) else null

                val returnType = figureOutType(ctx, ast, returnTypeCtx, typeParams)
                val receiverType = receiverTypeCtx?.let { figureOutType(ctx, ast, it, typeParams) }

                val parameterList = (func.paramList()?.param() ?: listOf()).map {
                    val type = figureOutType(ctx, ast, it.type(), typeParams)
                    if (type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${func.IDENTIFIER().text} has an argument with type void.")
                    Parameter(it.IDENTIFIER().text, type)
                }.toMutableList()

                if (receiverType != null) {
                    parameterList.add(0, Parameter("this", receiverType))
                }

                checkDuplicates(parameterList, "function ${ast.simplePath}::${func.IDENTIFIER().text}")

                val funcName = func.IDENTIFIER().text

                val duplicate = ast.functions.find { existing ->
                    existing.name == funcName &&
                            existing.parameters.map { it.type } == parameterList.map { it.type }
                }

                if (duplicate != null) {
                    val sig = parameterList.joinToString(", ") { it.type.toString() }
                    throw DuplicateDefinitionException("Duplicate function definition in ${ast.simplePath}::$funcName($sig)")
                }

                var isWarp = false
                var isExport = false
                func.modifier().forEach { modifier ->
                    when {
                        modifier.EXPORT() != null -> {
                            if (isExport) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                throw GeneralCompilerException("Double export in ${ast.simplePath}::$funcName($sig)")
                            }
                            isExport = true
                        }
                        modifier.WARP() != null -> {
                            if (isWarp) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                throw GeneralCompilerException("Double warp in ${ast.simplePath}::$funcName($sig)")
                            }
                            isWarp = true
                        }
                    }
                }

                val funcAST = Function(
                    name = funcName,
                    parameters = parameterList,
                    returnType = returnType,
                    warp = isWarp,
                    export = isExport,
                    sourceAST = ast,
                    typeParameters = typeParams,
                    isReceiver = receiverType != null
                )
                funcAST.ctx = func.block()

                if (typeParams.isEmpty()) {
                    ast.functions.add(funcAST)
                } else {
                    ast.templates.add(funcAST)
                }
            } else if (context.tlVarDecl() != null) {
                val variable = context.tlVarDecl()!!
                if (variable.expression() == null && variable.AUTO() != null) {
                    throw GeneralCompilerException("Top-level auto variable ${variable.IDENTIFIER().text} must have an initializer.")
                }

                val astVariable = TLVariable(
                    variable.IDENTIFIER().text,
                    variable.isConst == null,
                    variable.type()?.let {
                        figureOutType(ctx, ast, it)
                    }?: PrimitiveType.Auto,
                    sourceAST = ast
                )
                astVariable.ctx = variable.expression()
                if (astVariable.type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${astVariable.name} is type void.")
                if (ast.variables.find { it.name == astVariable.name } != null) {
                    throw DuplicateDefinitionException("Duplicate variable definition ${astVariable.name}")
                }

                ast.variables.add(astVariable)
            } else if (context.eventDecl() != null) {
                val event = context.eventDecl()!!
                val listener = when (event.IDENTIFIER().text) {
                    "GreenFlag" if event.eventArg() == null -> EventListener.GreenFlag
                    "KeyPressed" if event.eventArg() != null -> EventListener.KeyPressed(Key.from(event.eventArg()!!))
                    else -> throw GeneralCompilerException("Unknown event type ${event.text}, expected GreenFlag or KeyPressed")
                }

                val func = Function(
                    name = "compiler@eventlistener@${obfuscate(event.IDENTIFIER().text)}i${ctx.eventListenerIndex++}",
                    parameters = mutableListOf(),
                    returnType = PrimitiveType.Void,
                    export = false,
                    warp = false,
                    sourceAST = ast,
                    isEventListener = true
                )
                func.ctx = event.block()

                ast.functions.add(func)
                ast.eventListeners.add(ASTEventListener(
                    listener, CodeBlock(), ast
                ).also {
                    it.ctx = func
                })
            }
        }

        return ast
    }

    private fun findAST(
        plainStringLiteralContext: ScratcherLangParser.PlainStringLiteralContext?,
        identifier: TerminalNode?,
        ast: ASTFile
    ): ASTFile {
        if (plainStringLiteralContext == null) {
            //stdlib import
            val moduleNode = identifier!!
            val moduleName = moduleNode.text

            val stdLib = StandardLibASTGenerator.lib[moduleName]
                ?: throw NotFoundException("Standard library module $moduleName not found")
            if (moduleName == "gc" && !CompilationConstants.MARK_AND_SWEEP_GC) throw GeneralCompilerException("Mark and sweep GC is disabled! Cannot access \"gc\" from ${ast.simplePath}")
            if (StandardLibASTGenerator.isRestricted(stdLib)) throw GeneralCompilerException("Standard library module $moduleName is restricted!")

            return stdLib
        } else {
            //file import
            val text = plainStringLiteralContext.text.removeSurrounding("\"")
            val currentFile = File(fullPath).absoluteFile
            val parentDir = currentFile.parentFile
            val importedFile = File(parentDir, text).canonicalFile
            val importedPath = importedFile.absolutePath

            if (!importedFile.exists()) {
                throw NotFoundException("Imported file not found at $importedPath")
            }

            val importedAST = ctx.asts[importedPath] ?: run {
                val sourceCode = importedFile.readText()
                val reader = ASTReader(ctx, sourceCode, importedPath)
                reader.read()
            }

            return importedAST
        }
    }

    private fun parseImport(
        context: ScratcherLangParser.ImportDeclContext,
        ast: ASTFile
    ) {
        when(context) {
            is ScratcherLangParser.ImportNormalContext -> {
                val imported = findAST(
                    context.plainStringLiteral(),
                    context.IDENTIFIER(),
                    ast
                )
                ast.imports[imported.simplePath] = imported
            }
            is ScratcherLangParser.ImportAliasContext -> {
                val imported = findAST(
                    context.plainStringLiteral(),
                    context.IDENTIFIER(0),
                    ast
                )
                ast.imports[context.IDENTIFIER().last().text] = imported
            }
            is ScratcherLangParser.ImportSomeContext -> {
                val isWildcard = context.imported() is ScratcherLangParser.AllContext

                val importNames = when(val import = context.imported()) {
                    is ScratcherLangParser.AllContext -> {
                        listOf()
                    }
                    is ScratcherLangParser.ByIdentifierContext -> {
                        listOf(import.IDENTIFIER().text)
                    }
                    is ScratcherLangParser.ListContext -> {
                        import.IDENTIFIER().map { it.text }
                    }
                    else -> throw NotImplementedException("No implementation for ${import.text}")
                }

                val imported = findAST(context.plainStringLiteral(), context.IDENTIFIER(), ast)

                if (isWildcard) {
                    //import x::*
                    if (!ast.wildcardImportSources.contains(imported)) {
                        ast.wildcardImportSources.add(imported)
                    }
                } else {
                    //import x::x or x::{x, y, z}
                    importNames.forEach { name ->
                        verifyFlatImportNameExists(name, imported)
                        if (ast.flatImportNames.containsKey(name)) {
                            throw DuplicateDefinitionException(
                                "Duplicate flat import of \"$name\" in ${ast.simplePath} (already from ${ast.flatImportNames[name]!!.simplePath})"
                            )
                        }
                        ast.flatImportNames[name] = imported
                    }
                }
            }
        }
    }

    private fun verifyFlatImportNameExists(name: String, source: ASTFile) {
        val found =
            source.functions.any { it.name == name } ||
            source.templates.any { it.name == name } ||
            source.structs.any { it.name == name } ||
            source.structTemplates.any { it.name == name } ||
            source.enums.any { it.name == name } ||
            source.sealedEnums.any { it.name == name } ||
            source.sealedEnumTemplates.any { it.name == name } ||
            source.variables.any { it.name == name }

        if (!found) {
            throw NotFoundException(
                "Cannot import \"$name\" from ${source.simplePath}: no such item exists. " +
                        "Available: ${source.functions.map { it.name }.distinct().take(10).joinToString(", ")}"
            )
        }
    }

    private fun checkDuplicates(list: List<Parameter>, error: String) {
        val found = mutableListOf<String>()
        list.forEach {
            if (found.contains(it.name)) {
                throw DuplicateDefinitionException("Duplicate parameter definition ${it.name} in $error")
            }
            found.add(it.name)
        }
    }
}

fun figureOutType(
    context: CompilationContext,
    currentAST: ASTFile,
    type: ScratcherLangParser.TypeContext,
    typeParameters: List<String> = emptyList(),
    localTypeBindings: Map<String, Type> = emptyMap()
): Type {
    return when(type) {
        is ScratcherLangParser.ArrayTypeContext -> {
            ArrayType(
                figureOutType(context, currentAST, type.type(), typeParameters, localTypeBindings)
            )
        }
        is ScratcherLangParser.NullableTypeContext -> {
            val inner = figureOutType(context, currentAST, type.type(), typeParameters, localTypeBindings)
            if (inner is NullableType) return inner
            if (inner == PrimitiveType.Str) {
                NullableType(
                    StandardLibASTGenerator.compilerLib.structs.find { it.name == "StringBox" }!!.type
                )
            } else {
                NullableType(
                    inner
                )
            }
        }
        is ScratcherLangParser.PathTypeContext -> {
            val id = type.typePath().IDENTIFIER()
            val typeName = id.last().text
            val typeArgsCtx = type.type()
            val rawText = type.typePath().text

            if (typeArgsCtx.isNotEmpty()) {
                val resolvedArgs = typeArgsCtx.map { figureOutType(context, currentAST, it, typeParameters, localTypeBindings) }
                val targetAST = if (id.size == 2) {
                    currentAST.imports[id[0].text]
                        ?: throw NotFoundException("Import \"${id[0].text}\" not found for type ${type.text}")
                } else {
                    currentAST
                }

                val sealedTemplate = targetAST.sealedEnumTemplates.find { it.name == typeName }
                    ?: (if (id.size == 1) {
                        targetAST.flatImportNames[typeName]?.sealedEnumTemplates?.find { it.name == typeName }
                            ?: targetAST.wildcardImportSources.firstNotNullOfOrNull { it.sealedEnumTemplates.find { st -> st.name == typeName } }
                    } else null)

                if (sealedTemplate != null) {
                    return Generics.resolveGenericSealedEnum(context, sealedTemplate.sourceAST, typeName, resolvedArgs)
                }

                return Generics.resolveGenericStruct(context, targetAST, typeName, resolvedArgs, if (id.size == 2) null else currentAST)
            }

            if (id.size == 1) {
                val typeNameSingle = id[0].text
                if (localTypeBindings.containsKey(typeNameSingle)) {
                    return localTypeBindings[typeNameSingle]!!
                }
                if (typeParameters.contains(typeNameSingle)) {
                    return PlaceholderType(typeNameSingle)
                }
            }

            if (rawText.contains(".")) {
                if (rawText.contains("::")) {
                    val parts = rawText.split("::", limit = 2)
                    val importName = parts[0]
                    val typeFullName = parts[1]
                    val otherFile = currentAST.imports[importName]
                        ?: throw NotFoundException("Type ${type.text} not found in any imports")
                    return context.types.find {
                        (it is SimpleType && it.name == typeFullName && it.sourceAST == otherFile) ||
                                (it is SealedEnumType && it.name == typeFullName && it.sourceAST == otherFile)
                    } ?: throw NotFoundException("Type ${type.text} not found in import $importName")
                } else {
                    return context.types.find {
                        it is SimpleType && it.name == rawText && it.sourceAST == currentAST
                    } ?: throw NotFoundException("Type ${type.text} not found in current file")
                }
            }

            when (id.size) {
                2 -> {
                    //from other file
                    val otherFile = currentAST.imports[id[0].text]
                        ?: throw NotFoundException("Type ${type.text} not found in any imports")
                    context.types.find {
                        ((it is SimpleType && it.name == id[1].text && it.sourceAST == otherFile) ||
                                (it is SealedEnumType && it.name == id[1].text && it.sourceAST == otherFile))
                    } ?: throw NotFoundException("Type ${type.text} not found in import ${id[0].text}")
                }
                1 -> {
                    //current file
                    val found = context.types.find {
                        ((it is SimpleType && it.name == id[0].text && it.sourceAST == currentAST) ||
                                (it is SealedEnumType && it.name == id[0].text && it.sourceAST == currentAST))
                    }
                    if(found != null) return found

                    val flatSource = currentAST.flatImportNames[id[0].text]
                    if (flatSource != null) {
                        val fromFlat = context.types.find {
                            ((it is SimpleType && it.name == id[0].text && it.sourceAST == flatSource) ||
                                    (it is SealedEnumType && it.name == id[0].text && it.sourceAST == flatSource))
                        }
                        if (fromFlat != null) return fromFlat
                    }

                    for (wildcardAst in currentAST.wildcardImportSources) {
                        val fromWildcard = context.types.find {
                            ((it is SimpleType && it.name == id[0].text && it.sourceAST == wildcardAst) ||
                                    (it is SealedEnumType && it.name == id[0].text && it.sourceAST == wildcardAst))
                        }
                        if (fromWildcard != null) return fromWildcard
                    }

                    throw NotFoundException("Type ${type.text} not found in current file")
                }
                else -> {
                    throw GeneralCompilerException("Too many :: in type ${type.text}")
                }
            }
        }
        is ScratcherLangParser.PrimTypeContext -> {
            context.types.find {
                if (it !is PrimitiveType) return@find false
                it.toString() == type.primitiveType().text
            }!!
        }
        is ScratcherLangParser.FuncRefTypeContext -> {
            FunctionType(
                parameterTypes = type.type().dropLast(1).map {
                    figureOutType(context, currentAST, it, typeParameters, localTypeBindings)
                },
                returnType = figureOutType(context, currentAST, type.type().last(), typeParameters, localTypeBindings)
            )
        }

        else -> throw GeneralCompilerException("Type parser not implemented for ${type.text}")
    }
}