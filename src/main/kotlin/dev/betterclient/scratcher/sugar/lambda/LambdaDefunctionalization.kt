package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib
import org.antlr.v4.kotlinruntime.ast.Position

class LambdaDefunctionalization(
    val ctx: CompilationContext,
    val parLookup: Map<LocalVariable, Parameter> = emptyMap(),
    val closureConversion: LambdaClosureConversion? = null
) : ASTVisitor() {
    companion object {
        var index = 0
        val closureStructs = mutableMapOf<FunctionType, Struct>()
        val trampolines = mutableMapOf<Function, Function>()
    }

    private fun getOrCreateClosureStruct(funcType: FunctionType): Struct {
        return closureStructs.computeIfAbsent(funcType) {
            val captureType = closureConversion?.lambdaCapturesEnum?.type ?: StandardLibASTGenerator.lambdaLib.sealedEnums.first { it.name == "LambdaCaptures" }.type
            val targetFuncType = FunctionType(listOf(captureType) + funcType.parameterTypes, funcType.returnType)
            val struct = Struct(
                name = "Closure@${index++}",
                parameters = mutableListOf(
                    Parameter("func", targetFuncType),
                    Parameter("captures", captureType.asNullable())
                ),
                sourceAST = StandardLibASTGenerator.lambdaLib,
                private = false,
            )
            StandardLibASTGenerator.lambdaLib.structs.add(struct)
            ctx.types.add(struct.type)
            if (CompilationContext.options.gc.markAndSweepGC) {
                addGC(StructGCInfo(struct.type, struct))
            }
            MemoryLib.initMem(StandardLibASTGenerator.memLib, StandardLibASTGenerator.lambdaLib)
            struct
        }
    }

    override fun visitLambdaExpression(
        block: CodeBlock,
        arguments: List<LocalVariable>,
        captured: MutableSet<LocalVariable>,
        position: Position?
    ): Expression {
        val pars = arguments.map { Parameter(it.name, it.type) }
        val lookup = arguments.zip(pars).toMap()
        val lambdaExpr = LambdaExpression(arguments, block, captured, position)
        val fullFuncType = ExpressionTypes.getExpressionType(lambdaExpr) as FunctionType

        val userFuncType = FunctionType(fullFuncType.parameterTypes.drop(1), fullFuncType.returnType)

        val defunc = Function(
            name = "defunc@lambda@${index++}",
            parameters = pars.toMutableList(),
            returnType = fullFuncType.returnType,
            export = false,
            warp = true,
            operator = false,
            sourceAST = StandardLibASTGenerator.lambdaLib,
            private = false,
            code = LambdaDefunctionalization(ctx, lookup, closureConversion).visitCodeBlock(block)
        ).also {
            StandardLibASTGenerator.lambdaLib.functions.add(it)
        }

        val closureStruct = getOrCreateClosureStruct(userFuncType)
        val lambdaCapturesEnum = closureConversion!!.lambdaCapturesEnum
        val myCaptureStruct = closureConversion.lambdaCaptureStructs[lambdaExpr]

        val captureAlloc = if (myCaptureStruct != null && captured.isNotEmpty()) {
            val myCaptureArgs = captured.map { LocalVariableExpression(it, position) }

            SealedEnumConstructionExpression(
                sealedEnum = lambdaCapturesEnum,
                targetVariant = myCaptureStruct,
                arguments = myCaptureArgs,
                position = position
            )
        } else {
            SealedEnumConstructionExpression(
                sealedEnum = lambdaCapturesEnum,
                targetVariant = myCaptureStruct!!,
                arguments = listOf(),
                position = position
            )
        }

        return CallExpression(
            closureStruct.allocFunc,
            listOf(FunctionLiteral(defunc, position), captureAlloc),
            position
        )
    }

    override fun visitFunctionLiteral(func: Function, position: Position?): Expression {
        val userFuncType = FunctionType.from(func)
        val closureStruct = getOrCreateClosureStruct(userFuncType)

        val trampoline = trampolines.computeIfAbsent(func) {
            val captureType = closureConversion?.lambdaCapturesEnum?.type ?: StandardLibASTGenerator.lambdaLib.sealedEnums.first { it.name == "LambdaCaptures" }.type
            val capturePar = Parameter("capture", captureType)
            val userPars = func.parameters.map { Parameter(it.name, it.type) }

            val trampolineBody = CodeBlock()
            val callExpr = CallExpression(func, userPars.map { ParameterExpression(it, null) }, null)
            if (func.returnType == PrimitiveType.Void) {
                trampolineBody.code.add(ExpressionStatement(callExpr, null))
                trampolineBody.code.add(ReturnStatement(null, null))
            } else {
                trampolineBody.code.add(ReturnStatement(callExpr, null))
            }

            Function(
                name = "trampoline@${func.name}@${index++}",
                parameters = (listOf(capturePar) + userPars).toMutableList(),
                returnType = func.returnType,
                export = false,
                warp = true,
                operator = false,
                sourceAST = StandardLibASTGenerator.lambdaLib,
                code = trampolineBody,
                private = false,
            ).also {
                StandardLibASTGenerator.lambdaLib.functions.add(it)
            }
        }

        return CallExpression(
            closureStruct.allocFunc,
            listOf(FunctionLiteral(trampoline, position), NullExpression),
            position
        )
    }

    override fun visitDynamicCallExpression(
        function: Expression,
        args: List<Expression>,
        type: FunctionType,
        position: Position?
    ): Expression {
        val closureStruct = getOrCreateClosureStruct(type)

        val funcField = closureStruct.parameters[0]
        val capturesField = closureStruct.parameters[1]

        val targetFuncType = FunctionType(
            listOf(capturesField.type.asNonNull()) + type.parameterTypes,
            type.returnType
        )

        return DynamicCallExpression(
            function = MemberExpression(function, funcField, closureStruct, position),
            arguments = listOf(MemberExpression(function, capturesField, closureStruct, position)) + args,
            type = targetFuncType,
            position = position
        )
    }

    override fun visitLocalVariableExpression(variable: LocalVariable, position: Position?): Expression {
        if (parLookup.containsKey(variable)) {
            return ParameterExpression(parLookup[variable]!!, position)
        }

        return super.visitLocalVariableExpression(variable, position)
    }

    override fun visitLocalVariableAssignmentStatement(
        variable: LocalVariable,
        assignment: Expression,
        position: Position?
    ): Statement? {
        if (parLookup.containsKey(variable)) {
            throw GeneralCompilerException("Cannot assign value to parameter ${variable.name}")
        }

        return super.visitLocalVariableAssignmentStatement(variable, assignment, position)
    }
}