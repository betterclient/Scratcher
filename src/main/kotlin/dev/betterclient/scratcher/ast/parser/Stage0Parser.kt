package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.cli.CompilationOptions
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
import org.antlr.v4.kotlinruntime.ast.Position
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import org.json.JSONArray
import java.io.File

class CompilationContext(
    val options: CompilationOptions,
    val print: (String) -> Unit,
    val error: (String) -> Unit
) {
    companion object {
        fun print(msg: String, err: Boolean = false) {
            (if(err) current.error else current.print)(msg)
        }

        lateinit var current: CompilationContext
            private set //go

        val options: CompilationOptions
            get() = current.options

        val std: StandardLibASTGenerator
            get() = current.std
    }
    init {
        current = this
    }

    val std = StandardLibASTGenerator(this)
    var refCountGCSetupDone = false

    fun generateGCNames() {
        if (!options.gc.markAndSweepGC) return

        val allStructs = asts.values.flatMap { it.structs } +
                std.compilerLib.structs +
                std.lambdaLib.structs

        allStructs.distinct().forEach { struct ->
            if (gcNames.none { it is StructGCInfo && it.struct == struct }) {
                addGC(StructGCInfo(struct.type, struct))
            }
        }

        val allSealedEnums = asts.values.flatMap { it.sealedEnums } +
                std.compilerLib.sealedEnums +
                std.lambdaLib.sealedEnums

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

class ASTReader(val errorReporter: ErrorReporter, val ctx: CompilationContext, val source: String, val fullPath: String) {
    val initialRead = read(source, errorReporter)

    fun read(): ASTFile {
        val ast = ASTFile(
            fullPath,
            sourceCode = MySource.Some(source)
        )

        ctx.asts[fullPath] = ast

        //get these out of the way
        for (context in initialRead.topLevelElement().filter { it.tlListDecl() != null }) {
            val list = context.tlListDecl()!!
            val isPrivate = list.PRIVATE() != null
            val staticList = TLStaticList(
                name = list.IDENTIFIER().text,
                sourceAST = ast,
                private = isPrivate
            )

            list.plainStringLiteral()?.let {
                val relPath = it.text.removeSurrounding("\"")
                val currentFile = File(fullPath).absoluteFile
                val jsonFile = File(currentFile.parentFile, relPath).canonicalFile

                if (!jsonFile.exists()) {
                    errorReporter.logError(ast, list.position!!, "JSON file not found at ${jsonFile.absolutePath}")
                    return@let
                }

                try {
                    val jsonArray = JSONArray(jsonFile.readText())
                    jsonArray.forEach { any ->
                        staticList.scratchList.items.add(any.toString())
                    }
                    if (staticList.scratchList.items.size > 200_000) {
                        errorReporter.warn(
                            ast,
                            list.plainStringLiteral()?.position!!,
                            "${ast.simplePath}::${staticList.name} contains more than 200k elements, you might not be able to add items to this list in vanilla scratch."
                        )
                    }
                } catch (e: Exception) {
                    errorReporter.logError(ast, list.position!!, "Failed to parse JSON array from ${jsonFile.name}: ${e.message}")
                    return@let
                }
            }

            ast.staticLists.find { it.name == staticList.name }?.let {
                errorReporter.logError(ast, list.position!!, "Duplicate static list definition ${ast.simplePath}::${it.name}")
            }

            ast.staticLists.add(staticList)
        }

        //first read for types
        for (context in initialRead.topLevelElement().filter { it.structDecl() != null }) {
            val struct = context.structDecl()!!
            val typeParams = struct.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()
            val isPrivate = struct.PRIVATE() != null

            val structAST = Struct(
                name = struct.IDENTIFIER().text,
                sourceAST = ast,
                typeParameters = typeParams,
                private = isPrivate
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
            val private = enum.PRIVATE() != null
            val enumAST = ASTEnum(
                enum.IDENTIFIER(0)!!.text,
                enum.IDENTIFIER().subList(1, enum.IDENTIFIER().size).map { it.text },
                ast,
                private = private
            )
            ctx.types.add(enumAST.type)
            ast.enums.find { it.name == enumAST.name }?.let {
                errorReporter.logError(ast, enum.position!!, "Duplicate enum definition ${ast.simplePath}::${it.name}")
            }

            ast.enums.add(enumAST)
        }

        val sealedEnumArgMap = mutableMapOf<Struct, ScratcherLangParser.SealedEnumArgContext>()
        val sealedTemplateVariantMap = mutableMapOf<Struct, Pair<SealedEnum, ScratcherLangParser.SealedEnumArgContext>>()

        for (context in initialRead.topLevelElement().filter { it.sealedEnumDecl() != null }) {
            val sealedDecl = context.sealedEnumDecl()!!
            val enumName = sealedDecl.IDENTIFIER().text
            val typeParams = sealedDecl.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()
            val isPrivate = sealedDecl.PRIVATE() != null

            val sealedEnumAST = SealedEnum(
                name = enumName,
                types = mutableListOf(),
                sourceAST = ast,
                typeParameters = typeParams,
                private = isPrivate
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
                    sourceAST = ast,
                    private = false
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
        errorReporter.flush() //if we import a restricted library and use elements from it that we don't have access to, that will cause more errors, fail fast here

        //second read for parameters
        (ast.structs.filter { it.parseInfo != null } + ast.structTemplates).forEach { struct ->
            for (field in struct.parseInfo!!.structField()) {
                val type = figureOutType(errorReporter, ctx, ast, field.type(), struct.typeParameters)

                val isPrivate = field.PRIVATE() != null
                if (type == PrimitiveType.Void) {
                    errorReporter.logError(ast, field.type().position!!, "${ast.simplePath}::${struct.name} has an argument with type void.")
                }
                struct.parameters.add(
                    Parameter(
                        field.IDENTIFIER().text,
                        type,
                        isPrivate
                    )
                )
            }
            checkDuplicates(ast, struct.parseInfo!!.position!!, struct.parameters, "struct ${ast.simplePath}::${struct.name}")
            if (struct !in ast.structTemplates) {
                struct.parseInfo = null
            }
        }

        for ((struct, argCtx) in sealedEnumArgMap) {
            val paramList = argCtx.paramList()?.param() ?: emptyList()
            for (param in paramList) {
                val type = figureOutType(errorReporter, ctx, ast, param.type())
                if (type == PrimitiveType.Void) {
                    errorReporter.logError(ast, param.type().position!!, "${ast.simplePath}::${struct.name} has an argument with type void.")
                }
                struct.parameters.add(
                    Parameter(
                        param.IDENTIFIER().text,
                        type,
                        false
                    )
                )
            }
            checkDuplicates(ast, argCtx.position!!, struct.parameters, "sealed enum variant ${ast.simplePath}::${struct.name}")
        }

        for ((struct, pair) in sealedTemplateVariantMap) {
            val (parentSealed, argCtx) = pair
            val paramList = argCtx.paramList()?.param() ?: emptyList()
            for (param in paramList) {
                val type = figureOutType(errorReporter, ctx, ast, param.type(), parentSealed.typeParameters)
                if (type == PrimitiveType.Void) {
                    errorReporter.logError(ast, param.type().position!!, "${ast.simplePath}::${struct.name} has a field with type void.")
                }
                struct.parameters.add(
                    Parameter(
                        param.IDENTIFIER().text,
                        type,
                        false
                    )
                )
            }
            checkDuplicates(ast, argCtx.position!!, struct.parameters, "sealed enum variant ${ast.simplePath}::${struct.name}")
        }

        for (context in initialRead.topLevelElement()) {
            if (context.funcDecl() != null) {
                val func = context.funcDecl()!!
                val typeParams = func.typeParameters()?.IDENTIFIER()?.map { it.text } ?: emptyList()
                val hasReceiver = func.type().size > 1
                val returnTypeCtx = func.type(0)!!
                val receiverTypeCtx = if (hasReceiver) func.type(1) else null
                val isPrivate = func.PRIVATE() != null

                val returnType = figureOutType(errorReporter, ctx, ast, returnTypeCtx, typeParams)
                val receiverType = receiverTypeCtx?.let { figureOutType(errorReporter, ctx, ast, it, typeParams) }

                val parameterList = (func.paramList()?.param() ?: listOf()).map {
                    val type = figureOutType(errorReporter, ctx, ast, it.type(), typeParams)
                    if (type == PrimitiveType.Void) {
                        errorReporter.logError(ast, it.position!!,"${ast.simplePath}::${func.IDENTIFIER().text} has an argument with type void.")
                    }
                    Parameter(it.IDENTIFIER().text, type, false)
                }.toMutableList()

                if (receiverType != null) {
                    parameterList.add(0, Parameter("this", receiverType, false))
                }

                if (receiverType == PrimitiveType.Void) {
                    errorReporter.logError(ast, receiverTypeCtx.position!!,"${ast.simplePath}::${func.IDENTIFIER().text} has a receiver with type void.")
                }

                checkDuplicates(
                    ast,
                    func.paramList()?.position ?:
                        receiverTypeCtx?.position ?:
                        func.LPAREN().position(),
                    parameterList,
                    "function ${ast.simplePath}::${func.IDENTIFIER().text}"
                )

                val funcName = func.IDENTIFIER().text

                val duplicate = ast.functions.find { existing ->
                    existing.name == funcName &&
                            existing.parameters.map { it.type } == parameterList.map { it.type }
                }

                if (duplicate != null) {
                    val sig = parameterList.joinToString(", ") { it.type.toString() }
                    errorReporter.logError(ast, func.position!!,"Duplicate function definition in ${ast.simplePath}::$funcName($sig)")
                }

                var isWarp = false
                var isExport = false
                var isOperator = false
                var lastExportCtx: Position? = null
                func.modifier().forEach { modifier ->
                    when {
                        modifier.EXPORT() != null -> {
                            lastExportCtx = modifier.position!!
                            if (isExport) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                errorReporter.logError(ast, modifier.position!!, "Double export in ${ast.simplePath}::$funcName($sig)")
                            }
                            isExport = true
                        }
                        modifier.WARP() != null -> {
                            if (isWarp) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                errorReporter.logError(ast, modifier.position!!, "Double warp in ${ast.simplePath}::$funcName($sig)")
                            }
                            isWarp = true
                        }
                        modifier.OPERATOR() != null -> {
                            if(isOperator) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                errorReporter.logError(ast, modifier.position!!, "Double operator in ${ast.simplePath}::$funcName($sig)")
                            }
                            isOperator = true
                        }
                    }
                }

                if (isPrivate && isExport) {
                    errorReporter.logError(ast, lastExportCtx!!, "Function ${ast.simplePath}::$funcName cannot be both private and exported")
                }

                val funcAST = Function(
                    name = funcName,
                    parameters = parameterList,
                    returnType = returnType,
                    warp = isWarp,
                    export = isExport,
                    operator = isOperator,
                    sourceAST = ast,
                    typeParameters = typeParams,
                    isReceiver = receiverType != null,
                    private = isPrivate
                )

                if (funcAST.operator) {
                    OperatorFunctions.check(funcAST, errorReporter, func.position!!)
                }
                funcAST.ctx = func.block()

                if (typeParams.isEmpty()) {
                    ast.functions.add(funcAST)
                } else {
                    ast.templates.add(funcAST)
                }
            } else if (context.tlVarDecl() != null) {
                val variable = context.tlVarDecl()!!
                val private = variable.PRIVATE() != null
                if (variable.expression() == null && variable.AUTO() != null) {
                    errorReporter.logError(ast, variable.position!!, "Top-level auto variable ${variable.IDENTIFIER().text} must have an initializer.")
                }

                val astVariable = TLVariable(
                    variable.IDENTIFIER().text,
                    variable.isConst == null,
                    variable.type()?.let {
                        figureOutType(errorReporter, ctx, ast, it)
                    }?: PrimitiveType.Auto,
                    sourceAST = ast,
                    private = private,
                    position = variable.position
                )
                astVariable.ctx = variable.expression()
                if (astVariable.type == PrimitiveType.Void) {
                    errorReporter.logError(ast, variable.position!!, "${ast.simplePath}::${astVariable.name} is type void.")
                }
                if (ast.variables.find { it.name == astVariable.name } != null) {
                    errorReporter.logError(ast, variable.position!!, "Duplicate variable definition ${astVariable.name}")
                }
                if (ast.staticLists.find { it.name == astVariable.name } != null) {
                    errorReporter.logError(ast, variable.position!!, "Top-level variable ${ast.simplePath}::${astVariable.name} conflicts with static list of the same name")
                }

                ast.variables.add(astVariable)
            } else if (context.eventDecl() != null) {
                val event = context.eventDecl()!!
                val listener = when (event.IDENTIFIER().text) {
                    "GreenFlag" if event.eventArg() == null -> EventListener.GreenFlag
                    "KeyPressed" if event.eventArg() != null -> EventListener.KeyPressed(Key.from(event.eventArg()!!, errorReporter, ast))
                    else -> {
                        errorReporter.logError(ast, event.IDENTIFIER().position(), "Unknown event type, expected GreenFlag or KeyPressed")
                        EventListener.GreenFlag
                    }
                }

                val func = Function(
                    name = "compiler@eventlistener@${obfuscate(event.IDENTIFIER().text)}i${ctx.eventListenerIndex++}",
                    parameters = mutableListOf(),
                    returnType = PrimitiveType.Void,
                    export = false,
                    warp = false,
                    operator = false,
                    sourceAST = ast,
                    isEventListener = true,
                    private = false
                )
                func.ctx = event.block()

                ast.functions.add(func)
                ast.eventListeners.add(ASTEventListener(
                    listener, CodeBlock(), ast, event.position
                ).also {
                    it.ctx = func
                })
            }
        }

        errorReporter.flush()
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

            val stdLib = ctx.std.lib[moduleName]
                ?: run {
                    errorReporter.logError(ast, identifier.position(), "No standard library module $moduleName found!")
                    ctx.std.emptyLib
                }
            if (moduleName == "gc" && !ctx.options.gc.markAndSweepGC) {
                errorReporter.logError(ast, identifier.position(), "Mark and sweep GC is disabled! Cannot access \"gc\" from ${ast.simplePath}")
            }
            if (ctx.std.isRestricted(stdLib)) {
                errorReporter.logError(ast, identifier.position(), "Standard library module $moduleName is restricted!")
            }

            return stdLib
        } else {
            //file import
            val text = plainStringLiteralContext.text.removeSurrounding("\"")
            val currentFile = File(fullPath).absoluteFile
            val parentDir = currentFile.parentFile
            val importedFile = File(parentDir, text).canonicalFile
            val importedPath = importedFile.absolutePath

            if (!importedFile.exists()) {
                errorReporter.logError(ast, plainStringLiteralContext.position!!, "Imported file not found at $importedPath")
                return ctx.std.emptyLib
            }

            val importedAST = ctx.asts[importedPath] ?: run {
                val sourceCode = importedFile.readText()
                val reader = ASTReader(errorReporter, ctx, sourceCode, importedPath)
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
                        verifyFlatImportNameExists(context.imported().position!!, name, imported, ast)
                        if (ast.flatImportNames.containsKey(name)) {
                            errorReporter.logError(ast, context.imported().position!!,  "Duplicate flat import of \"$name\" in ${ast.simplePath} (already from ${ast.flatImportNames[name]!!.simplePath})")
                        }
                        ast.flatImportNames[name] = imported
                    }
                }
            }
        }
    }

    private fun verifyFlatImportNameExists(position: Position, name: String, source: ASTFile, me: ASTFile) {
        val found =
            source.functions.any { it.name == name && !it.private } ||
            source.templates.any { it.name == name && !it.private } ||
            source.structs.any { it.name == name && !it.private } ||
            source.structTemplates.any { it.name == name && !it.private } ||
            source.enums.any { it.name == name && !it.private } ||
            source.sealedEnums.any { it.name == name && !it.private } ||
            source.sealedEnumTemplates.any { it.name == name && !it.private } ||
            source.variables.any { it.name == name && !it.private } ||
            source.staticLists.any { it.name == name && !it.private }

        if (!found) {
            val available = (
                source.functions.filter { !it.private }.map { it.name } +
                source.templates.filter { !it.private }.map { it.name } +
                source.structs.filter { !it.private }.map { it.name } +
                source.structTemplates.filter { !it.private }.map { it.name } +
                source.enums.filter { !it.private }.map { it.name } +
                source.sealedEnums.filter { !it.private }.map { it.name } +
                source.sealedEnumTemplates.filter { !it.private }.map { it.name } +
                source.variables.filter { !it.private }.map { it.name } +
                source.staticLists.filter { it.name == name && !it.private }
            ).distinct().take(10).joinToString(", ")

            errorReporter.logError(me, position, "Cannot import \"$name\" from ${source.simplePath}: no such item exists. " +
                    "Available: $available")
        }
    }

    private fun checkDuplicates(ast: ASTFile, position: Position, list: List<Parameter>, error: String) {
        val found = mutableListOf<String>()
        list.forEach {
            if (found.contains(it.name)) {
                errorReporter.logError(ast, position, "Duplicate argument definition ${it.name} in $error")
            }
            found.add(it.name)
        }
    }
}

fun figureOutType(
    reporter: ErrorReporter,
    context: CompilationContext,
    currentAST: ASTFile,
    type: ScratcherLangParser.TypeContext,
    typeParameters: List<String> = emptyList(),
    localTypeBindings: Map<String, Type> = emptyMap()
): Type {
    return when(type) {
        is ScratcherLangParser.ArrayTypeContext -> {
            ArrayType(
                figureOutType(reporter, context, currentAST, type.type(), typeParameters, localTypeBindings)
            )
        }
        is ScratcherLangParser.NullableTypeContext -> {
            val inner = figureOutType(reporter, context, currentAST, type.type(), typeParameters, localTypeBindings)
            if (inner is NullableType) return inner
            if (inner == PrimitiveType.Str) {
                NullableType(
                    context.std.compilerLib.structs.find { it.name == "StringBox" }!!.type
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
                val resolvedArgs = typeArgsCtx.map { figureOutType(reporter, context, currentAST, it, typeParameters, localTypeBindings) }
                val targetAST = if (id.size == 2) {
                    currentAST.imports[id[0].text]
                        ?: run {
                            reporter.logError(currentAST, id[0].position(), "Import \"${id[0].text}\" not found for type ${type.text}")
                            context.std.emptyLib
                        }
                } else {
                    currentAST
                }

                val sealedTemplate = targetAST.sealedEnumTemplates.find { it.name == typeName }
                    ?: (if (id.size == 1) {
                        targetAST.flatImportNames[typeName]?.sealedEnumTemplates?.find { it.name == typeName }
                            ?: targetAST.wildcardImportSources.firstNotNullOfOrNull { it.sealedEnumTemplates.find { st -> st.name == typeName } }
                            ?: targetAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == typeName }
                    } else null)

                if (sealedTemplate != null) {
                    return Generics.resolveGenericSealedEnum(reporter, context, targetAST, currentAST, typeName, resolvedArgs, if (id.size == 2) null else currentAST)
                }

                return Generics.resolveGenericStruct(reporter, context, targetAST, currentAST, typeName, resolvedArgs, if (id.size == 2) null else currentAST)
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
                        ?: run {
                            reporter.logError(currentAST, type.typePath().position!!, "Type ${type.text} not found in any imports")
                            context.std.emptyLib
                        }

                    val found = context.types.find {
                        (it is SimpleType && it.name == typeFullName && it.sourceAST == otherFile) ||
                                (it is SealedEnumType && it.name == typeFullName && it.sourceAST == otherFile)
                    } ?: run {
                        reporter.logError(currentAST, type.typePath().position!!, "Type ${type.text} not found in import $importName")
                        PrimitiveType.Integer
                    }

                    if (otherFile != currentAST) {
                        val isPrivate = if (typeFullName.contains(".")) {
                            val parentName = typeFullName.substringBefore(".").substringBefore("@")
                            otherFile.sealedEnums.find { it.name == parentName || it.name.substringBefore("@") == parentName }?.private
                                ?: otherFile.sealedEnumTemplates.find { it.name == parentName }?.private
                                ?: otherFile.structs.find { it.name == typeFullName }?.private
                                ?: false
                        } else {
                            otherFile.structs.find { it.name == typeFullName }?.private
                                ?: otherFile.structTemplates.find { it.name == typeFullName }?.private
                                ?: otherFile.enums.find { it.name == typeFullName }?.private
                                ?: otherFile.sealedEnums.find { it.name == typeFullName }?.private
                                ?: otherFile.sealedEnumTemplates.find { it.name == typeFullName }?.private
                                ?: false
                        }
                        if (isPrivate) {
                            reporter.logError(currentAST, type.position!!, "Type ${type.text} is private and cannot be accessed from ${currentAST.simplePath}")
                        }
                    }
                    return found
                } else {
                    val parentName = rawText.substringBefore(".").substringBefore("@")
                    fun findIn(source: ASTFile) = context.types.find {
                        it is SimpleType && it.name == rawText && it.sourceAST == source
                    }
                    fun checkPrivate(source: ASTFile) {
                        if (source == currentAST) return
                        val isPrivate = source.sealedEnums.find { it.name == parentName || it.name.substringBefore("@") == parentName }?.private
                            ?: source.sealedEnumTemplates.find { it.name == parentName }?.private
                            ?: source.structs.find { it.name == rawText }?.private
                            ?: false
                        if (isPrivate) {
                            reporter.logError(currentAST, type.position!!, "Type ${type.text} is private and cannot be accessed from ${currentAST.simplePath}")
                        }
                    }
                    findIn(currentAST)?.also { checkPrivate(currentAST); return it }
                    currentAST.flatImportNames[parentName]?.let { flatSource ->
                        findIn(flatSource)?.also { checkPrivate(flatSource); return it }
                    }
                    for (wildcardAst in currentAST.wildcardImportSources) {
                        findIn(wildcardAst)?.also { checkPrivate(wildcardAst); return it }
                    }
                    reporter.logError(currentAST, type.position!!, "Type ${type.text} not found in current file")
                    return PrimitiveType.Integer
                }
            }

            when (id.size) {
                2 -> {
                    //from other file
                    val otherFile = currentAST.imports[id[0].text]
                        ?: run {
                            reporter.logError(currentAST, type.typePath().position!!, "Type ${type.text} not found in any imports")
                            context.std.emptyLib
                        }

                    val typeName = id[1].text
                    val isPrivate = otherFile.structs.find { it.name == typeName }?.private
                        ?: otherFile.structTemplates.find { it.name == typeName }?.private
                        ?: otherFile.enums.find { it.name == typeName }?.private
                        ?: otherFile.sealedEnums.find { it.name == typeName }?.private
                        ?: otherFile.sealedEnumTemplates.find { it.name == typeName }?.private
                        ?: false

                    if (isPrivate && otherFile != currentAST) {
                        reporter.logError(currentAST, type.position!!, "Type ${type.text} is private and cannot be accessed from ${currentAST.simplePath}")
                    }

                    context.types.find {
                        ((it is SimpleType && it.name == id[1].text && it.sourceAST == otherFile) ||
                                (it is SealedEnumType && it.name == id[1].text && it.sourceAST == otherFile))
                    } ?: run {
                        reporter.logError(currentAST, type.position!!, "Type ${type.text} not found in import ${id[0].text}")
                        PrimitiveType.Integer
                    }
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
                        if (fromFlat != null) {
                            if (flatSource != currentAST) {
                                val typeName = id[0].text
                                val isPrivate = flatSource.structs.find { it.name == typeName }?.private
                                    ?: flatSource.structTemplates.find { it.name == typeName }?.private
                                    ?: flatSource.enums.find { it.name == typeName }?.private
                                    ?: flatSource.sealedEnums.find { it.name == typeName }?.private
                                    ?: flatSource.sealedEnumTemplates.find { it.name == typeName }?.private
                                    ?: false
                                if (isPrivate) {
                                    reporter.logError(currentAST, type.position!!, "Type ${type.text} is private and cannot be accessed from ${currentAST.simplePath}")
                                }
                            }
                            return fromFlat
                        }
                    }

                    for (wildcardAst in currentAST.wildcardImportSources) {
                        val fromWildcard = context.types.find {
                            val matches = (it is SimpleType && it.name == id[0].text && it.sourceAST == wildcardAst) ||
                                    (it is SealedEnumType && it.name == id[0].text && it.sourceAST == wildcardAst)
                            if (!matches) return@find false

                            val isPrivate = wildcardAst.structs.find { s -> s.type == it }?.private
                                ?: wildcardAst.enums.find { e -> e.type == it }?.private
                                ?: wildcardAst.sealedEnums.find { se -> se.type == it }?.private
                                ?: false

                            !isPrivate
                        }
                        if (fromWildcard != null) return fromWildcard
                    }

                    reporter.logError(currentAST, type.position!!, "Type ${type.text} not found in current file")
                    PrimitiveType.Integer
                }
                else -> {
                    reporter.logError(currentAST, type.position!!, "Too many :: in type ${type.text}")
                    PrimitiveType.Integer
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
                    figureOutType(reporter, context, currentAST, it, typeParameters, localTypeBindings)
                },
                returnType = figureOutType(reporter, context, currentAST, type.type().last(), typeParameters, localTypeBindings)
            )
        }

        else -> throw GeneralCompilerException("Type parser not implemented for ${type.text}")
    }
}

fun TerminalNode.position(): Position {
    return this.symbol.let {
        Position(it.startPoint(), it.endPoint()!!)
    }
}