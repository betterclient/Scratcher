package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.NullableType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PlaceholderType
import dev.betterclient.scratcher.ast.Type

object Generics {
    fun substituteType(type: Type, bindings: Map<String, Type>): Type {
        return when (type) {
            is PlaceholderType -> bindings[type.name] ?: type
            is ListType -> ListType(substituteType(type.elementType, bindings))
            is NullableType -> NullableType(substituteType(type.inner, bindings))
            is FunctionType -> FunctionType(
                type.parameterTypes.map { substituteType(it, bindings) },
                substituteType(type.returnType, bindings)
            )
            else -> type
        }
    }

    fun deduceTypeArgs(paramType: Type, providedType: Type, typeParams: List<String>, bindings: MutableMap<String, Type>): Boolean {
        if (paramType is PlaceholderType && typeParams.contains(paramType.name)) {
            val existing = bindings[paramType.name]
            if (existing != null && existing != providedType) return false
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
            for (i in paramType.parameterTypes.indices) {
                if (!deduceTypeArgs(paramType.parameterTypes[i], providedType.parameterTypes[i], typeParams, bindings)) return false
            }
            return deduceTypeArgs(paramType.returnType, providedType.returnType, typeParams, bindings)
        }
        return paramType == providedType
    }

    fun tryResolve(
        sourceAST: ASTFile,
        funcName: String,
        expectedArgListTypes: List<Type>,
        args: List<Expression>,
        parser: Stage1Parser
    ): CallExpression? {
        val template = sourceAST.templates.find { it.name == funcName } ?: return null
        if (expectedArgListTypes.size != template.parameters.size) return null

        val bindings = mutableMapOf<String, Type>()
        var matches = true
        for (i in expectedArgListTypes.indices) {
            if (!deduceTypeArgs(template.parameters[i].type, expectedArgListTypes[i], template.typeParameters, bindings)) {
                matches = false
                break
            }
        }

        if (!matches || !template.typeParameters.all { bindings.containsKey(it) }) return null

        val typeSuffix = bindings.values.joinToString("_") {
            it.toSafeString()
        }
        val instantiatedName = "${template.name}\$$typeSuffix"

        var resolvedFunc = sourceAST.functions.find { it.name == instantiatedName }
        if (resolvedFunc == null) {
            val newParams = template.parameters.map {
                Parameter(it.name, substituteType(it.type, bindings))
            }.toMutableList()
            val newReturnType = substituteType(template.returnType, bindings)

            resolvedFunc = Function(
                name = instantiatedName,
                parameters = newParams,
                returnType = newReturnType,
                export = template.export,
                warp = template.warp,
                sourceAST = sourceAST,
                typeBindings = bindings
            )
            sourceAST.functions.add(resolvedFunc)

            compileTemplate(parser, resolvedFunc, template, bindings)
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
}