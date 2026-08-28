package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.ArrayType
import dev.betterclient.scratcher.ast.NotFoundException
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PlaceholderType
import dev.betterclient.scratcher.ast.SealedEnum
import dev.betterclient.scratcher.ast.SealedEnumType
import dev.betterclient.scratcher.ast.SimpleType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.figureOutType
import dev.betterclient.scratcher.gc.SealedEnumGCInfo
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib

object Generics {
    fun substituteType(context: CompilationContext, type: Type, bindings: Map<String, Type>): Type {
        return when (type) {
            is PlaceholderType -> bindings[type.name] ?: type
            is ArrayType -> ArrayType(substituteType(context, type.elementType, bindings))
            is NullableType -> substituteType(context, type.inner, bindings).asNullable()
            is FunctionType -> FunctionType(
                type.parameterTypes.map { substituteType(context, it, bindings) },
                substituteType(context, type.returnType, bindings)
            )
            is SealedEnumType -> {
                if (type.typeBindings.isEmpty()) {
                    val template = type.sourceAST.sealedEnumTemplates.find { it.name == type.name }
                        ?: type.sourceAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == type.name }
                    if (template != null && template.typeParameters.any { bindings.containsKey(it) }) {
                        val typeArgs = template.typeParameters.map { bindings[it] ?: PlaceholderType(it) }
                        if (typeArgs.none { it is PlaceholderType }) {
                            return resolveGenericSealedEnum(context, type.sourceAST, type.name, typeArgs)
                        }
                    }
                } else {
                    val newBindings = type.typeBindings.mapValues { substituteType(context, it.value, bindings) }
                    if (newBindings != type.typeBindings) {
                        val baseName = type.name.substringBefore("@")
                        val template = type.sourceAST.sealedEnumTemplates.find { it.name == baseName }
                            ?: type.sourceAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == baseName }
                        if (template != null) {
                            val typeArgs = template.typeParameters.map { newBindings[it]!! }
                            return resolveGenericSealedEnum(context, type.sourceAST, baseName, typeArgs)
                        }
                    }
                }
                type
            }
            is SimpleType -> {
                val struct = type.sourceAST.structs.find { it.type == type }
                if (struct != null && struct.typeBindings.isNotEmpty()) {
                    val newBindings = struct.typeBindings.mapValues { substituteType(context, it.value, bindings) }
                    if (newBindings != struct.typeBindings) {
                        val baseName = struct.name.substringBefore("@")
                        val template = type.sourceAST.structTemplates.find { it.name == baseName }
                        if (template != null) {
                            val typeArgs = template.typeParameters.map { newBindings[it]!! }
                            return resolveGenericStruct(context, type.sourceAST, baseName, typeArgs)
                        }
                    }
                }
                type
            }
            else -> type
        }
    }

    fun deduceTypeArgs(paramType: Type, providedType: Type, typeParams: List<String>, bindings: MutableMap<String, Type>): Boolean {
        if (paramType is PlaceholderType && typeParams.contains(paramType.name)) {
            val existing = bindings[paramType.name]
            if (existing != null) {
                return providedType.isAssignable(existing)
            }
            bindings[paramType.name] = providedType
            return true
        }
        if (paramType is ArrayType && providedType is ArrayType) {
            return deduceTypeArgs(paramType.elementType, providedType.elementType, typeParams, bindings)
        }
        if (paramType is NullableType && providedType is NullableType) {
            return deduceTypeArgs(paramType.inner, providedType.inner, typeParams, bindings)
        }
        if (paramType is FunctionType && providedType is FunctionType) {
            if (paramType.parameterTypes.size != providedType.parameterTypes.size) return false
            val paramsMatch = paramType.parameterTypes.indices.all { i ->
                deduceTypeArgs(paramType.parameterTypes[i], providedType.parameterTypes[i], typeParams, bindings)
            }
            return paramsMatch && deduceTypeArgs(paramType.returnType, providedType.returnType, typeParams, bindings)
        }
        if (paramType is SimpleType && providedType is SimpleType) {
            val paramStruct = paramType.sourceAST.structs.find { it.type == paramType }
            val providedStruct = providedType.sourceAST.structs.find { it.type == providedType }
            if (paramStruct != null && providedStruct != null) {
                val paramBaseName = paramStruct.name.substringBefore("@")
                val providedBaseName = providedStruct.name.substringBefore("@")
                if (paramBaseName == providedBaseName && paramStruct.typeBindings.isNotEmpty() && providedStruct.typeBindings.isNotEmpty()) {
                    return paramStruct.typeBindings.all { (key, value) ->
                        val providedValue = providedStruct.typeBindings[key] ?: return@all false
                        deduceTypeArgs(value, providedValue, typeParams, bindings)
                    }
                }
            }
        }
        if (paramType is SealedEnumType && providedType is SealedEnumType) {
            val paramBase = paramType.name.substringBefore("@")
            val providedBase = providedType.name.substringBefore("@")
            if (paramBase == providedBase) {
                val paramTemplate = paramType.sourceAST.sealedEnumTemplates.find { it.name == paramBase }
                    ?: paramType.sourceAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == paramBase }
                    ?: paramType.sourceAST.sealedEnums.find { it.name == paramBase }
                if (paramTemplate != null && paramType.typeBindings.isNotEmpty() && providedType.typeBindings.isNotEmpty()) {
                    return paramType.typeBindings.all { (key, value) ->
                        val providedValue = providedType.typeBindings[key] ?: return@all false
                        deduceTypeArgs(value, providedValue, typeParams, bindings)
                    }
                }
                if (paramType.typeBindings.isEmpty() && providedType.typeBindings.isNotEmpty() && paramTemplate != null) {
                    return paramTemplate.typeParameters.all { tp ->
                        val providedVal = providedType.typeBindings[tp] ?: return@all false
                        deduceTypeArgs(PlaceholderType(tp), providedVal, typeParams, bindings)
                    }
                }
            }
        }
        if (paramType is SealedEnumType && providedType is SimpleType) {
            val sealedName = paramType.name.substringBefore("@")
            val sealed = paramType.sourceAST.sealedEnums.find { it.name == sealedName && it.typeBindings == paramType.typeBindings }
                ?: paramType.sourceAST.imports.values.flatMap { it.sealedEnums }.find { it.name == sealedName && it.typeBindings == paramType.typeBindings }
                ?: return paramType == providedType
            return sealed.types.any { it.type == providedType }
        }
        return paramType == providedType
    }

    fun tryResolve(
        context: CompilationContext,
        sourceAST: ASTFile,
        funcName: String,
        expectedArgListTypes: List<Type>,
        args: List<Expression>,
        parser: Stage1Parser,
        expectedType: Type? = null
    ): CallExpression? {
        val template = sourceAST.templates.find { it.name == funcName }
            ?: sourceAST.imports.values.flatMap { it.templates }.find { it.name == funcName }
            ?: return null
        if (expectedArgListTypes.size != template.parameters.size) return null

        val bindings = mutableMapOf<String, Type>()
        val matches = expectedArgListTypes.indices.all { i ->
            deduceTypeArgs(template.parameters[i].type, expectedArgListTypes[i], template.typeParameters, bindings)
        }

        if (!matches) return null

        if (expectedType != null && !template.typeParameters.all { bindings.containsKey(it) }) {
            deduceTypeArgs(template.returnType, expectedType, template.typeParameters, bindings)
        }

        if (!template.typeParameters.all { bindings.containsKey(it) } && parser.currentTypeBindings.isNotEmpty()) {
            for (tp in template.typeParameters) {
                parser.currentTypeBindings[tp]?.let { bindings[tp] = it }
            }
        }

        if (!template.typeParameters.all { bindings.containsKey(it) }) return null

        val typeSuffix = bindings.values.joinToString("_") { it.toSafeString() }
        val instantiatedName = "${template.name}\$$typeSuffix"

        val resolvedFunc = sourceAST.functions.find { it.name == instantiatedName } ?: run {
            val newParams = template.parameters.map {
                Parameter(it.name, substituteType(context, it.type, bindings))
            }.toMutableList()
            val newReturnType = substituteType(context, template.returnType, bindings)

            Function(
                name = instantiatedName,
                parameters = newParams,
                returnType = newReturnType,
                export = template.export,
                warp = template.warp,
                sourceAST = sourceAST,
                typeBindings = bindings,
                isReceiver = template.isReceiver
            ).also {
                sourceAST.functions.add(it)
                compileTemplate(parser, it, template, bindings)
            }
        }

        return CallExpression(resolvedFunc, args)
    }

    fun compileTemplate(parser: Stage1Parser, resolvedFunc: Function, template: Function, bindings: Map<String, Type>) {
        val newParser = Stage1Parser(parser.ctx, template.sourceAST)

        newParser.currentFunction = resolvedFunc
        newParser.currentTypeBindings = bindings
        TypeCheckParameters.addParameterChecks(resolvedFunc.code, resolvedFunc.parameters, template.sourceAST, template)
        newParser.statementParser.parseBlock(resolvedFunc.code, template.ctx!!)
        resolvedFunc.ctx = null
    }

    fun resolveGenericStruct(
        context: CompilationContext,
        currentAST: ASTFile,
        baseName: String,
        typeArgs: List<Type>
    ): Type {
        val template = currentAST.structTemplates.find { it.name == baseName }
            ?: currentAST.imports.values.flatMap { it.structTemplates }.find { it.name == baseName }
            ?: throw NotFoundException("Generic struct template $baseName not found")

        if (template.typeParameters.size != typeArgs.size) {
            throw GeneralCompilerException("Type argument count mismatch for ${template.name}")
        }

        val suffix = typeArgs.joinToString("_") { it.toSafeString() }
        val instantiatedName = "${template.name}@$suffix"

        val existing = currentAST.structs.find { it.name == instantiatedName }
            ?: currentAST.imports.values.flatMap { it.structs }.find { it.name == instantiatedName }
        if (existing != null) {
            return existing.type
        }

        val bindings = template.typeParameters.zip(typeArgs).toMap()
        val instantiatedStruct = Struct(
            name = instantiatedName,
            sourceAST = template.sourceAST,
            typeBindings = bindings
        )

        template.sourceAST.structs.add(instantiatedStruct)
        context.types.add(instantiatedStruct.type)

        val isPhantom = typeArgs.any { it.asNonNull() is PlaceholderType }
        if (CompilationConstants.MARK_AND_SWEEP_GC && !isPhantom) {
            addGC(StructGCInfo(instantiatedStruct.type, instantiatedStruct))
        }

        for (field in template.parseInfo!!.structField()) {
            val abstractType =
                figureOutType(context, template.sourceAST, field.type(), template.typeParameters, bindings)
            val concreteType = substituteType(context, abstractType, bindings)

            instantiatedStruct.parameters.add(Parameter(field.IDENTIFIER().text, concreteType))
        }

        MemoryLib.initMem(StandardLibASTGenerator.memLib, template.sourceAST)

        return instantiatedStruct.type
    }

    fun resolveGenericSealedEnum(
        context: CompilationContext,
        currentAST: ASTFile,
        baseName: String,
        typeArgs: List<Type>
    ): Type {
        val template = currentAST.sealedEnumTemplates.find { it.name == baseName }
            ?: currentAST.imports.values.flatMap { it.sealedEnumTemplates }.find { it.name == baseName }
            ?: throw NotFoundException("Generic sealed enum template $baseName not found")

        if (template.typeParameters.size != typeArgs.size) {
            throw GeneralCompilerException("Type argument count mismatch for sealed enum ${template.name}")
        }

        val suffix = typeArgs.joinToString("_") { it.toSafeString() }
        val instantiatedName = "${template.name}@$suffix"

        val targetAST = template.sourceAST
        val existing = targetAST.sealedEnums.find { it.name == instantiatedName }
            ?: currentAST.sealedEnums.find { it.name == instantiatedName }
            ?: targetAST.sealedEnums.find { it.type == SealedEnumType(instantiatedName, targetAST) }
        if (existing != null) {
            return existing.type
        }
        val bindings = template.typeParameters.zip(typeArgs).toMap()
        val existingByBindings = targetAST.sealedEnums.find { it.name == template.name && it.typeBindings == bindings }
        if (existingByBindings != null) return existingByBindings.type

        val instantiatedSealed = SealedEnum(
            name = instantiatedName,
            types = mutableListOf(),
            sourceAST = targetAST,
            typeParameters = emptyList(),
            typeBindings = bindings
        )
        targetAST.sealedEnums.add(instantiatedSealed)
        val sealedType = instantiatedSealed.type
        context.types.add(sealedType)

        if (CompilationConstants.MARK_AND_SWEEP_GC) {
            addGC(SealedEnumGCInfo(sealedType, instantiatedSealed))
        }

        for (placeholderVariant in template.types) {
            val short = placeholderVariant.name.substringAfter(".")
            val variantFullName = "$instantiatedName.$short"
            val concreteVariant = Struct(
                name = variantFullName,
                sourceAST = targetAST,
                typeBindings = bindings
            )
            for (param in placeholderVariant.parameters) {
                val concreteType = substituteType(context, param.type, bindings)
                concreteVariant.parameters.add(Parameter(param.name, concreteType))
            }
            targetAST.structs.add(concreteVariant)
            context.types.add(concreteVariant.type)
            instantiatedSealed.types.add(concreteVariant)

            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                addGC(StructGCInfo(concreteVariant.type, concreteVariant))
            }
        }

        MemoryLib.initMem(StandardLibASTGenerator.memLib, targetAST)

        return sealedType
    }
}