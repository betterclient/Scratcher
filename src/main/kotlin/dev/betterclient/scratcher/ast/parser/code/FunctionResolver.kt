package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.DuplicateDefinitionException
import dev.betterclient.scratcher.ast.DynamicCallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.FunctionType
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.NotFoundException
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.SimpleType
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class FunctionResolver(
    val parser: Stage1Parser,
    val ast: ASTFile
) {
    fun figureOutFunction(
        funcCall: ScratcherLangParser.FunctionIdentifierContext,
        argList: ScratcherLangParser.ArgListContext?,
        expectedType: Type?
    ): Expression {
        val importName = if (funcCall.IDENTIFIER() != null) null else funcCall.typePath()!!.IDENTIFIER(0)!!.text
        val funcName = if (funcCall.IDENTIFIER() != null) funcCall.IDENTIFIER()!!.text else funcCall.typePath()!!.IDENTIFIER(1)!!.text
        return figureOutFunctionInternal(importName, funcName, funcCall.getParent()?.text?: funcCall.position!!.toString(), argList, expectedType)
    }

    fun figureOutFunctionInternal(
        importName: String?,
        funcName: String,
        errorText: String,
        argList: ScratcherLangParser.ArgListContext?,
        expectedType: Type?
    ): Expression {
        val sourceAST = if (importName == null) {
            ast
        } else {
            ast.imports[importName]?: throw NotFoundException("Import not found $importName for $errorText.")
        }

        val expectedArgListTypes = argList?.expression()?.map { expr ->
            ExpressionTypes.getExpressionType(parser.ctx, parser.expressionParser.parseExpression(expr))
        }?: listOf()
        val args = argList?.expression()?.map { parser.expressionParser.parseExpression(it) }?: listOf()
        val inflatedArgs = { paramTypes: List<Type> ->
            args.mapIndexed { i, arg -> StringBoxing.autoConvert(arg, paramTypes.getOrNull(i), parser.ctx) }
        }

        Generics.tryResolve(parser.ctx, sourceAST, funcName, expectedArgListTypes, args, parser)?.let {
            return it
        }

        val structTemplate = sourceAST.structTemplates.find {
            it.name == funcName
        } ?: sourceAST.imports.values.flatMap { it.structTemplates }.find {
            it.name == funcName
        }

        if (structTemplate != null) {
            val bindings = mutableMapOf<String, Type>()
            var matches = true

            val expectedStruct = (expectedType as? SimpleType)?.let { t ->
                t.sourceAST.structs.find { it.type == t }
            }
            if (expectedStruct != null && expectedStruct.name.substringBefore("@") == structTemplate.name) {
                bindings.putAll(expectedStruct.typeBindings)
            }

            for (i in expectedArgListTypes.indices) {
                if (i < structTemplate.parameters.size) {
                    if (!Generics.deduceTypeArgs(structTemplate.parameters[i].type, expectedArgListTypes[i], structTemplate.typeParameters, bindings)) {
                        matches = false
                        break
                    }
                }
            }

            if (matches && structTemplate.typeParameters.all { bindings.containsKey(it) }) {
                val resolvedTypes = structTemplate.typeParameters.map { bindings[it]!! }

                val typeSuffix = resolvedTypes.joinToString("_") { it.toSafeString() }
                val instantiatedName = "$funcName@$typeSuffix"

                var concreteStruct = sourceAST.structs.find { it.name == instantiatedName }

                if (concreteStruct == null) {
                    val concreteType = Generics.resolveGenericStruct(parser.ctx, sourceAST, funcName, resolvedTypes) as SimpleType
                    concreteStruct = sourceAST.structs.find { it.type == concreteType }
                }

                if (concreteStruct != null) {
                    return CallExpression(concreteStruct.allocFunc, inflatedArgs(concreteStruct.parameters.map { par -> par.type }))
                }
            }
        }

        var resolvedFunc = sourceAST.functions.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            matchesArgumentsExactly(expectedArgListTypes, foundArgListTypes)
        }

        if (sourceAST == StandardLibASTGenerator.listLib && funcName != "newList") {
            //AAAAAAAAAAAAAAAAAAAAAAAAAAA
            resolvedFunc = sourceAST.functions.find { it.name == funcName }?: throw NotFoundException("Function $funcName not found. in ${ast.simplePath}::${parser.currentFunction?.name} at $errorText")
        }

        if (resolvedFunc == null) {
            resolvedFunc = sourceAST.functions.find {
                if (it.name != funcName) return@find false

                val foundArgListTypes = it.parameters.map { par -> par.type }
                matchesArguments(expectedArgListTypes, foundArgListTypes)
            }
        }

        resolvedFunc?.let {
            if (!it.userAccessible) {
                throw NotFoundException("Function $errorText is not accessible.")
            }
            return CallExpression(
                func = it,
                arguments = inflatedArgs(it.parameters.map { par -> par.type })
            )
        }

        //dynamic call?
        if (sourceAST == ast) {
            //first check local variables
            parser.localVariables.find { it.name == funcName && it.type is FunctionType }?.let {
                if (matchesArguments(
                        provided = (it.type as FunctionType).parameterTypes,
                        expected = expectedArgListTypes
                    )) {
                    return DynamicCallExpression(
                        type = it.type as FunctionType,
                        function = LocalVariableExpression(it),
                        arguments = inflatedArgs((it.type as FunctionType).parameterTypes)
                    )
                }
            }

            parser.currentFunction?.parameters?.find { it.name == funcName && it.type is FunctionType }?.let {
                if (matchesArguments(
                        provided = (it.type as FunctionType).parameterTypes,
                        expected = expectedArgListTypes
                    )) {
                    return DynamicCallExpression(
                        type = it.type,
                        function = ParameterExpression(it),
                        arguments = inflatedArgs(it.type.parameterTypes)
                    )
                }
            }
        }

        sourceAST.structs.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            return@find matchesArguments(
                expectedArgListTypes,
                foundArgListTypes
            )
        }?.let {
            return CallExpression(it.allocFunc, inflatedArgs(it.parameters.map { par -> par.type }))
        }

        val targetFunc = "$funcName(${expectedArgListTypes.joinToString(", ") { it.toString() }})"
        val candidates = mutableListOf<String>()
        sourceAST.functions.filter { it.name == funcName }.forEach { func ->
            candidates.add("Function \"${func.returnType} ${func.name}(${func.parameters.joinToString(", ") { "${it.type} ${it.name}" }})\"")
        }
        sourceAST.structs.filter { it.name == targetFunc }.forEach { struct ->
            candidates.add("Struct \"${struct.name}\"")
        }

        if(parser.currentFunction?.isReceiver == true && importName == null) {
            //might be trying to call this.xxx() without the this.
            val a = resolveReceiverFunction(
                receiverExpr = ParameterExpression(parser.currentFunction?.parameters?.find { it.name == "this" }!!),
                methodName = funcName,
                arguments = args
            )
            if (a != null) return a
        }

        throw NotFoundException("Function $targetFunc not found, candidates: \n${candidates.joinToString("\n")}\nStackTrace:")
    }

    fun figureOutFunctionSimple(
        funcCall: ScratcherLangParser.FunctionIdentifierContext
    ): Function {
        val sourceAST = if (funcCall.IDENTIFIER() != null) {
            ast
        } else {
            val import = funcCall.typePath()!!.IDENTIFIER(0)!!.text
            ast.imports[import]?: throw NotFoundException("Import not found $import for ${funcCall.text}.")
        }

        val funcName = if (funcCall.IDENTIFIER() != null) {
            funcCall.IDENTIFIER()!!.text
        } else {
            funcCall.typePath()!!.IDENTIFIER(1)!!.text
        }

        val resolvedFunc = sourceAST.functions.filter {
            it.name == funcName
        }

        return when(resolvedFunc.size) {
            0 -> {
                throw NotFoundException("Unable to find ${funcCall.text}.")
            }
            1 -> {
                if (!resolvedFunc[0].userAccessible) {
                    throw NotFoundException("Function ${funcCall.text} is not accessible.")
                }
                resolvedFunc[0]
            }
            else -> {
                throw DuplicateDefinitionException("Ambiguous function reference ${funcCall.text}.")
            }
        }
    }

    private fun matchesArgumentsExactly(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false
        return provided.zip(expected).all { (from, to) ->
            from == to
        }
    }

    private fun matchesArguments(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false

        return provided.zip(expected).all { (from, to) ->
            from.isAssignable(to)
        }
    }

    fun resolveReceiverFunction(
        receiverExpr: Expression,
        methodName: String,
        arguments: List<Expression>
    ): CallExpression? {
        val allArgs = listOf(receiverExpr) + arguments
        val receiverType = ExpressionTypes.getExpressionType(parser.ctx, receiverExpr)
        val argTypes = listOf(receiverType) + arguments.map { ExpressionTypes.getExpressionType(parser.ctx, it) }

        val inflatedArgs = { paramTypes: List<Type> ->
            allArgs.mapIndexed { i, arg -> StringBoxing.autoConvert(arg, paramTypes.getOrNull(i), parser.ctx) }
        }

        val searchASTs = listOf(ast) + ast.imports.values

        for (sourceAST in searchASTs) {
            Generics.tryResolve(parser.ctx, sourceAST, methodName, argTypes, allArgs, parser)?.let {
                return it
            }

            val resolvedFunc = sourceAST.functions.find { func ->
                if (!func.isReceiver) return@find false
                if (func.name != methodName) return@find false
                if (func.parameters.size != argTypes.size) return@find false
                matchesArguments(argTypes, func.parameters.map { it.type })
            }

            if (resolvedFunc != null) {
                return CallExpression(
                    func = resolvedFunc,
                    arguments = inflatedArgs(resolvedFunc.parameters.map { it.type })
                )
            }
        }

        return null
    }
}