package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.NotFoundException
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PlaceholderType
import dev.betterclient.scratcher.ast.SimpleType
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib

object Generics {
    fun substituteType(context: CompilationContext, type: Type, bindings: Map<String, Type>): Type {
        return when (type) {
            is PlaceholderType -> bindings[type.name] ?: type
            is ListType -> ListType(substituteType(context, type.elementType, bindings))
            is NullableType -> NullableType(substituteType(context, type.inner, bindings))
            is FunctionType -> FunctionType(
                type.parameterTypes.map { substituteType(context, it, bindings) },
                substituteType(context, type.returnType, bindings)
            )
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
        if (paramType is ListType && providedType is ListType) {
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
        return paramType == providedType
    }

    fun tryResolve(
        context: CompilationContext,
        sourceAST: ASTFile,
        funcName: String,
        expectedArgListTypes: List<Type>,
        args: List<Expression>,
        parser: Stage1Parser
    ): CallExpression? {
        val template = sourceAST.templates.find { it.name == funcName } ?: return null
        if (expectedArgListTypes.size != template.parameters.size) return null

        val bindings = mutableMapOf<String, Type>()
        val matches = expectedArgListTypes.indices.all { i ->
            deduceTypeArgs(template.parameters[i].type, expectedArgListTypes[i], template.typeParameters, bindings)
        }

        if (!matches || !template.typeParameters.all { bindings.containsKey(it) }) return null

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
                typeBindings = bindings
            ).also {
                sourceAST.functions.add(it)
                compileTemplate(parser, it, template, bindings)
            }
        }

        return CallExpression(resolvedFunc, args)
    }

    fun compileTemplate(parser: Stage1Parser, resolvedFunc: Function, template: Function, bindings: Map<String, Type>) {
        val oldFunction = parser.currentFunction
        val oldBindings = parser.currentTypeBindings
        val oldLocals = parser.localVariables.toList()

        parser.currentFunction = resolvedFunc
        parser.currentTypeBindings = bindings
        parser.localVariables.clear()
        parser.addParameterChecks(resolvedFunc.code, resolvedFunc.parameters)
        parser.parseBlock(resolvedFunc.code, template.ctx!!)
        resolvedFunc.ctx = null

        parser.currentFunction = oldFunction
        parser.currentTypeBindings = oldBindings
        parser.localVariables.clear()
        parser.localVariables.addAll(oldLocals)
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

        if (!CompilationConstants.MANUAL_MEMORY) {
            addGC(StructGCInfo(instantiatedStruct.type, instantiatedStruct))
        }

        for (field in template.parseInfo!!.structField()) {
            val abstractType = figureOutType(context, template.sourceAST, field.type(), template.typeParameters, bindings)
            val concreteType = substituteType(context, abstractType, bindings)

            instantiatedStruct.parameters.add(Parameter(field.IDENTIFIER().text, concreteType))
        }

        MemoryLib.initMem(StandardLibASTGenerator.memLib, template.sourceAST)

        return instantiatedStruct.type
    }
}