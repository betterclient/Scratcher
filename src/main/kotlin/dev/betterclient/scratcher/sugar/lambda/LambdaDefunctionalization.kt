package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib

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
                sourceAST = StandardLibASTGenerator.lambdaLib
            )
            StandardLibASTGenerator.lambdaLib.structs.add(struct)
            ctx.types.add(struct.type)
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                addGC(StructGCInfo(struct.type, struct))
            }
            MemoryLib.initMem(StandardLibASTGenerator.memLib, StandardLibASTGenerator.lambdaLib)
            struct
        }
    }

    override fun visitLambdaExpression(
        block: CodeBlock,
        arguments: List<LocalVariable>,
        captured: MutableSet<LocalVariable>
    ): Expression {
        val pars = arguments.map { Parameter(it.name, it.type) }
        val lookup = arguments.zip(pars).toMap()
        val lambdaExpr = LambdaExpression(arguments, block, captured)
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
            code = LambdaDefunctionalization(ctx, lookup, closureConversion).visitCodeBlock(block)
        ).also {
            StandardLibASTGenerator.lambdaLib.functions.add(it)
        }

        val closureStruct = getOrCreateClosureStruct(userFuncType)
        val lambdaCapturesEnum = closureConversion!!.lambdaCapturesEnum
        val myCaptureStruct = closureConversion.lambdaCaptureStructs[lambdaExpr]

        val captureAlloc = if (myCaptureStruct != null && captured.isNotEmpty()) {
            val myCaptureArgs = captured.map { LocalVariableExpression(it) }

            SealedEnumConstructionExpression(
                sealedEnum = lambdaCapturesEnum,
                targetVariant = myCaptureStruct,
                arguments = myCaptureArgs
            )
        } else {
            SealedEnumConstructionExpression(
                sealedEnum = lambdaCapturesEnum,
                targetVariant = myCaptureStruct!!,
                arguments = listOf()
            )
        }

        return CallExpression(
            closureStruct.allocFunc,
            listOf(FunctionLiteral(defunc), captureAlloc)
        )
    }

    override fun visitFunctionLiteral(func: Function): Expression {
        val userFuncType = FunctionType.from(func)
        val closureStruct = getOrCreateClosureStruct(userFuncType)

        val trampoline = trampolines.computeIfAbsent(func) {
            val captureType = closureConversion?.lambdaCapturesEnum?.type ?: StandardLibASTGenerator.lambdaLib.sealedEnums.first { it.name == "LambdaCaptures" }.type
            val capturePar = Parameter("capture", captureType)
            val userPars = func.parameters.map { Parameter(it.name, it.type) }

            val trampolineBody = CodeBlock()
            val callExpr = CallExpression(func, userPars.map { ParameterExpression(it) })
            if (func.returnType == PrimitiveType.Void) {
                trampolineBody.code.add(ExpressionStatement(callExpr))
                trampolineBody.code.add(ReturnStatement(null))
            } else {
                trampolineBody.code.add(ReturnStatement(callExpr))
            }

            Function(
                name = "trampoline@${func.name}@${index++}",
                parameters = (listOf(capturePar) + userPars).toMutableList(),
                returnType = func.returnType,
                export = false,
                warp = true,
                operator = false,
                sourceAST = StandardLibASTGenerator.lambdaLib,
                code = trampolineBody
            ).also {
                StandardLibASTGenerator.lambdaLib.functions.add(it)
            }
        }

        return CallExpression(
            closureStruct.allocFunc,
            listOf(FunctionLiteral(trampoline), NullExpression)
        )
    }

    override fun visitDynamicCallExpression(
        function: Expression,
        args: List<Expression>,
        type: FunctionType
    ): Expression {
        val closureStruct = getOrCreateClosureStruct(type)

        val funcField = closureStruct.parameters[0]
        val capturesField = closureStruct.parameters[1]

        val targetFuncType = FunctionType(
            listOf(capturesField.type.asNonNull()) + type.parameterTypes,
            type.returnType
        )

        return DynamicCallExpression(
            function = MemberExpression(function, funcField, closureStruct),
            arguments = listOf(MemberExpression(function, capturesField, closureStruct)) + args,
            type = targetFuncType
        )
    }

    override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
        if (parLookup.containsKey(variable)) {
            return ParameterExpression(parLookup[variable]!!)
        }

        return super.visitLocalVariableExpression(variable)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
        if (parLookup.containsKey(variable)) {
            throw GeneralCompilerException("Cannot assign value to parameter ${variable.name}")
        }

        return super.visitLocalVariableAssignmentStatement(variable, assignment)
    }
}