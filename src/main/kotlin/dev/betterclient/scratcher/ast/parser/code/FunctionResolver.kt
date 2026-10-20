package dev.betterclient.scratcher.ast.parser.code

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.ast.parser.ExpressionTypes.type
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.std.lib.ArrayLib
import org.antlr.v4.kotlinruntime.ast.Position
import java.math.BigInteger

class FunctionResolver(
    val parser: Stage1Parser,
    val ast: ASTFile
) {
    val reporter = parser.reporter
    fun figureOutFunction(
        funcCall: ScratcherLangParser.FunctionIdentifierContext,
        argList: ScratcherLangParser.ArgListContext?,
        expectedType: Type?,
        position: Position? = funcCall.position
    ): Expression {
        val importName = if (funcCall.IDENTIFIER() != null) null else funcCall.simpleTypePath()!!.IDENTIFIER(0)!!.text
        val funcName = if (funcCall.IDENTIFIER() != null) funcCall.IDENTIFIER()!!.text else funcCall.simpleTypePath()!!.IDENTIFIER(1)!!.text
        return figureOutFunctionInternal(importName, funcName, funcCall.getParent()?.text?: funcCall.position!!.toString(), argList, expectedType, position)
    }

    fun figureOutFunctionInternal(
        importName: String?,
        funcName: String,
        errorText: String,
        argList: ScratcherLangParser.ArgListContext?,
        expectedType: Type?,
        position: Position? = null
    ): Expression {
        val sourceAST = if (importName == null) {
            ast
        } else {
            ast.imports[importName]?:
                return reporter.error(position!!, ast, "Import not found $importName for $errorText.")
        }

        val expectedArgListTypes = argList?.expression()?.map { expr ->
            ExpressionTypes.getExpressionType(parser.expressionParser.parseExpression(expr))
        }?: listOf()
        val args = argList?.expression()?.map { parser.expressionParser.parseExpression(it) }?: listOf()
        val inflatedArgs = { paramTypes: List<Type> ->
            args.mapIndexed { i, arg -> StringBoxing.autoConvert(arg, paramTypes.getOrNull(i)) }
        }

        //special stuff for array.sc
        if (sourceAST == CompilationContext.std.arrayInternalLib) {
            if (funcName == "newArray") {
                val elemType = parser.currentTypeBindings["T"]
                    ?: (expectedType as? ArrayType)?.elementType
                    ?: (expectedType?.asNonNull() as? ArrayType)?.elementType
                    ?: return this.reporter.error(position!!, ast, "Cannot infer element type for array_internal::newArray")

                val lengthArg = if (args.isNotEmpty()) args.last() else IntLiteral(BigInteger.ZERO, position)
                return CallExpression(
                    func = ArrayLib.newArray,
                    arguments = listOf(TypeLiteral(elemType, position), lengthArg),
                    position = position
                )
            }

            val resolvedFunc = sourceAST.functions.find { it.name == funcName }
                ?: return this.reporter.error(position!!, ast, "Function $funcName not found")

            return CallExpression(
                func = resolvedFunc,
                arguments = inflatedArgs(resolvedFunc.parameters.map { par -> par.type }),
                position = position
            )
        }

        Generics.tryResolve(this.parser.reporter, parser.ctx, sourceAST, funcName, expectedArgListTypes, args, parser, expectedType)?.let {
            return it
        }

        val structTemplate = if (importName != null) {
            sourceAST.structTemplates.find { it.name == funcName }
        } else {
            sourceAST.structTemplates.find { it.name == funcName }
                ?: sourceAST.flatImportNames[funcName]?.structTemplates?.find { it.name == funcName }
                ?: sourceAST.wildcardImportSources.firstNotNullOfOrNull { it.structTemplates.find { st -> st.name == funcName } }
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

                var concreteStruct = structTemplate.sourceAST.structs.find { it.name == instantiatedName }

                if (concreteStruct == null) {
                    val concreteType = Generics.resolveGenericStruct(this.parser.reporter, parser.ctx, structTemplate.sourceAST, ast, funcName, resolvedTypes) as SimpleType
                    concreteStruct = structTemplate.sourceAST.structs.find { it.type == concreteType }
                }

                if (concreteStruct?.private == true && concreteStruct.sourceAST != ast) {
                    this.reporter.error(position!!, ast, "${concreteStruct.name} is private and cannot be accessed from ${ast.simplePath}")
                }

                if (concreteStruct != null) {
                    return CallExpression(
                        concreteStruct.allocFunc,
                        inflatedArgs(concreteStruct.parameters.map { par -> par.type }),
                        position = position
                    )
                }
            }
        }

        var resolvedFunc = sourceAST.functions.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            matchesArgumentsExactly(expectedArgListTypes, foundArgListTypes)
        }

        if (resolvedFunc == null) {
            resolvedFunc = sourceAST.functions.find {
                if (it.name != funcName) return@find false

                val foundArgListTypes = it.parameters.map { par -> par.type }
                matchesArguments(expectedArgListTypes, foundArgListTypes)
            }
        }

        if (resolvedFunc == null && importName == null) {
            //import from?
            val flatSource = ast.flatImportNames[funcName]
            if (flatSource != null) {
                resolvedFunc = flatSource.functions.find {
                    it.name == funcName && matchesArgumentsExactly(expectedArgListTypes, it.parameters.map { par -> par.type })
                } ?: flatSource.functions.find {
                    it.name == funcName && matchesArguments(expectedArgListTypes, it.parameters.map { par -> par.type })
                }
            }

            if (resolvedFunc == null) {
                for (wildcardAst in ast.wildcardImportSources) {
                    resolvedFunc = wildcardAst.functions.find {
                        it.name == funcName && matchesArgumentsExactly(expectedArgListTypes, it.parameters.map { par -> par.type })
                    } ?: wildcardAst.functions.find {
                        it.name == funcName && matchesArguments(expectedArgListTypes, it.parameters.map { par -> par.type })
                    }
                    if (resolvedFunc != null) break
                }
            }
        }

        resolvedFunc?.let {
            if (!it.userAccessible) {
                this.reporter.error(position!!, ast, "Function $errorText is not accessible.")
            }
            if (it.private && it.sourceAST != ast) {
                this.reporter.error(position!!, ast, "Function ${it.name} is private and cannot be accessed from ${ast.simplePath}")
            }
            return CallExpression(
                func = it,
                arguments = inflatedArgs(it.parameters.map { par -> par.type }),
                position = position
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
                        function = LocalVariableExpression(it, position),
                        arguments = inflatedArgs((it.type as FunctionType).parameterTypes),
                        position = position
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
                        function = ParameterExpression(it, position),
                        arguments = inflatedArgs(it.type.parameterTypes),
                        position = position
                    )
                }
            }
        }

        var structFinding = sourceAST.structs.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            matchesArguments(expectedArgListTypes, foundArgListTypes)
        } ?: (if (importName == null) sourceAST.imports.values.flatMap { it.structs }.find {
            it.name == funcName && matchesArguments(expectedArgListTypes, it.parameters.map { par -> par.type })
        } else null)

        if (structFinding == null && importName == null) {
            val sourcesToCheck = mutableListOf<ASTFile>()
            ast.flatImportNames[funcName]?.let { sourcesToCheck.add(it) }
            sourcesToCheck.addAll(ast.wildcardImportSources)

            for (src in sourcesToCheck) {
                structFinding = src.structs.find {
                    it.name == funcName && matchesArguments(expectedArgListTypes, it.parameters.map { par -> par.type })
                } ?: src.structTemplates.find {
                    it.name == funcName && matchesArguments(expectedArgListTypes, it.parameters.map { par -> par.type })
                }
                if (structFinding != null) break
            }
        }

        structFinding?.let {
            if (it.private && it.sourceAST != ast) {
                this.reporter.error(position!!, ast, "${it.name} is private and cannot be accessed from ${ast.simplePath}")
            }
            if (it.sourceAST != ast && it.parameters.any { param -> param.private }) {
                this.reporter.error(position!!, ast, "Cannot instantiate struct ${it.name} from ${ast.simplePath}: it contains private fields")
            }
            return CallExpression(
                it.allocFunc,
                inflatedArgs(it.parameters.map { par -> par.type }),
                position = position
            )
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
                receiverExpr = ParameterExpression(
                    parser.currentFunction?.parameters?.find { it.name == "this" }!!,
                    position
                ),
                methodName = funcName,
                arguments = args,
                position = position
            )
            if (a != null) return a
        }

        return this.reporter.error(position!!, ast, "Function $targetFunc not found, candidates: \n${candidates.joinToString("\n")}")
    }

    fun figureOutFunctionSimple(
        funcCall: ScratcherLangParser.FunctionIdentifierContext
    ): Function? {
        val sourceAST = if (funcCall.IDENTIFIER() != null) {
            ast
        } else {
            val import = funcCall.simpleTypePath()!!.IDENTIFIER(0)!!.text
            ast.imports[import]?: run {
                this.reporter.error(funcCall.simpleTypePath()!!.position!!, ast, "Import not found $import.")
                return null
            }
        }

        val funcName = if (funcCall.IDENTIFIER() != null) {
            funcCall.IDENTIFIER()!!.text
        } else {
            funcCall.simpleTypePath()!!.IDENTIFIER(1)!!.text
        }

        var resolvedFunc = sourceAST.functions.filter {
            it.name == funcName
        }

        if (resolvedFunc.isEmpty() && funcCall.IDENTIFIER() != null) {
            val name = funcCall.IDENTIFIER()!!.text
            val flatSource = ast.flatImportNames[name]
                ?: ast.wildcardImportSources.firstOrNull()?.takeIf { it.functions.any { f -> f.name == name && !f.private } }
            if (flatSource != null) {
                resolvedFunc = flatSource.functions.filter { it.name == name && !it.private }
            }
        }

        return when(resolvedFunc.size) {
            0 -> {
                this.reporter.error(funcCall.position!!, ast, "Unable to find ${funcCall.text}.")
                null
            }
            1 -> {
                if (!resolvedFunc[0].userAccessible) {
                    this.reporter.error(funcCall.position!!, ast, "Function ${funcCall.text} is not accessible.")
                }
                if (resolvedFunc[0].private && resolvedFunc[0].sourceAST != ast) {
                    this.reporter.error(funcCall.position!!, ast, "Function ${funcCall.text} is private and cannot be accessed from ${ast.simplePath}")
                }
                resolvedFunc[0]
            }
            else -> {
                this.reporter.error(funcCall.position!!, ast, "Ambiguous function reference ${funcCall.text}.")
                null
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
        arguments: List<Expression>,
        filter: (Function) -> Boolean = { true },
        position: Position? = receiverExpr.position
    ): CallExpression? {
        val allArgs = listOf(receiverExpr) + arguments
        val receiverType = ExpressionTypes.getExpressionType(receiverExpr)
        val argTypes = listOf(receiverType) + arguments.map { ExpressionTypes.getExpressionType(it) }

        val inflatedArgs = { paramTypes: List<Type> ->
            allArgs.mapIndexed { i, arg -> StringBoxing.autoConvert(arg, paramTypes.getOrNull(i)) }
        }

        val searchASTs = listOf(ast) + ast.imports.values + ast.wildcardImportSources + ast.flatImportNames.values

        for (sourceAST in searchASTs) {
            Generics.tryResolve(this.parser.reporter, parser.ctx, sourceAST, methodName, argTypes, allArgs, parser, filter = filter)?.let {
                return it
            }

            val resolvedFunc = sourceAST.functions.find { func ->
                if (!func.isReceiver) return@find false
                if (func.name != methodName) return@find false
                if (func.private && func.sourceAST != parser.ast) return@find false
                if (func.parameters.size != argTypes.size) return@find false
                if (!filter(func)) return@find false
                matchesArguments(argTypes, func.parameters.map { it.type })
            }

            if (resolvedFunc != null) {
                return CallExpression(
                    func = resolvedFunc,
                    arguments = inflatedArgs(resolvedFunc.parameters.map { it.type }),
                    position = position
                )
            }
        }

        return null
    }

    fun resolveOperatorFunction(
        left: Expression,
        operatorName: String,
        right: Expression,
        position: Position? = left.position
    ): CallExpression? {
        val allArgs = listOf(left, right)
        val argTypes = allArgs.map { ExpressionTypes.getExpressionType(it) }
        val inflatedArgs = { paramTypes: List<Type> ->
            allArgs.mapIndexed { i, arg -> StringBoxing.autoConvert(arg, paramTypes.getOrNull(i)) }
        }

        val searchASTs = (listOf(ast) + ast.imports.values + ast.wildcardImportSources + ast.flatImportNames.values).distinct()

        for (sourceAST in searchASTs) {
            Generics.tryResolve(
                this.parser.reporter, parser.ctx, sourceAST, operatorName, argTypes, allArgs, parser,
                filter = { it.operator && !it.isReceiver && !(it.private && it.sourceAST != ast) }
            )?.let { return it }

            var resolvedFunc = sourceAST.functions.find { func ->
                !func.isReceiver && func.operator && func.name == operatorName &&
                        !(func.private && func.sourceAST != ast) &&
                        matchesArgumentsExactly(argTypes, func.parameters.map { it.type })
            }

            if (resolvedFunc == null) {
                resolvedFunc = sourceAST.functions.find { func ->
                    !func.isReceiver && func.operator && func.name == operatorName &&
                            !(func.private && func.sourceAST != ast) &&
                            matchesArguments(argTypes, func.parameters.map { it.type })
                }
            }

            if (resolvedFunc != null) {
                return CallExpression(
                    func = resolvedFunc,
                    arguments = inflatedArgs(resolvedFunc.parameters.map { it.type }),
                    position = position
                )
            }
        }

        return null
    }

    fun resolveStaticListFunction(
        list: StaticListExpression,
        methodName: String,
        args: List<Expression>,
        position: Position? = list.position
    ): Expression {
        return when (methodName) {
            "add" if args.size == 1 && args[0].type == PrimitiveType.Str -> {
                StatementExpression(
                    statements = listOf(
                        StaticListAddStatement(
                            list = list.list,
                            item = args[0],
                            position
                        )
                    ),
                    expression = NullExpression,
                    position = position
                )
            }

            "get" if args.size == 1 && args[0].type == PrimitiveType.Integer -> {
                StaticListItemExpression(list.list, args[0], position)
            }

            "set" if args.size == 2 && args[0].type == PrimitiveType.Integer && args[1].type == PrimitiveType.Str -> {
                StatementExpression(
                    statements = listOf(
                        StaticListSetStatement(
                            list = list.list,
                            value = args[1],
                            index = args[0],
                            position = position
                        )
                    ),
                    expression = NullExpression,
                    position = position
                )
            }

            "clear" if args.isEmpty() -> {
                StatementExpression(
                    statements = listOf(StaticListClearStatement(list.list, position)),
                    expression = NullExpression,
                    position = position
                )
            }

            "length" if args.isEmpty() -> {
                StaticListLengthExpression(list.list, position)
            }

            "insert" if args.size == 2 && args[0].type == PrimitiveType.Integer && args[1].type == PrimitiveType.Str -> {
                StatementExpression(
                    statements = listOf(
                        StaticListInsertStatement(
                            list = list.list,
                            value = args[1],
                            index = args[0],
                            position = position
                        )
                    ),
                    expression = NullExpression,
                    position = position
                )
            }

            "contains" if args.size == 1 && args[0].type == PrimitiveType.Str -> {
                StaticListContainsExpression(list.list, args[0], position)
            }

            "indexOf" if args.size == 1 && args[0].type == PrimitiveType.Str -> {
                StaticListItemIndexExpression(list.list, args[0], position)
            }

            "remove" if args.size == 1 && args[0].type == PrimitiveType.Integer -> {
                StatementExpression(
                    statements = listOf(
                        StaticListRemoveStatement(
                            list = list.list,
                            index = args[0],
                            position = position
                        )
                    ),
                    expression = NullExpression,
                    position = position
                )
            }

            else -> this.reporter.error(position!!, ast, "No method $methodName that can operate on static lists.")
        }
    }
}