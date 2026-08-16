package dev.betterclient.scratcher.sugar.lambda

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.LambdaExpression
import dev.betterclient.scratcher.ast.LocalVariable
import dev.betterclient.scratcher.ast.LocalVariableExpression
import dev.betterclient.scratcher.ast.MemberExpression
import dev.betterclient.scratcher.ast.NullExpression
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Statement
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.optimize.ASTVisitor
import dev.betterclient.scratcher.optimize.visit
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib

class LambdaClosureConversion(
    val ctx: CompilationContext
) : ASTVisitor() {
    val lambdaCapturesStruct = Struct(
        name = "LambdaCaptures",
        sourceAST = StandardLibASTGenerator.lambdaLib
    ).also {
        StandardLibASTGenerator.lambdaLib.structs.add(it)
        ctx.types.add(it.type)
        if (CompilationConstants.MARK_AND_SWEEP_GC) {
            addGC(StructGCInfo(it.type, it))
        }
    }

    val boxTypes = mutableMapOf<Type, Struct>()
    val variableBoxes = mutableMapOf<LocalVariable, Struct>()
    var index = 0

    var activeCapture: Struct? = null
    var activeCaptureVal: LocalVariable? = null
    val captureMappings = mutableMapOf<LocalVariable, Parameter>()

    val allCapturedLocals = mutableSetOf<LocalVariable>()
    val lambdaCaptureStructs = mutableMapOf<LambdaExpression, Struct>()

    fun run(func: Function) {
        val captureCollector = object : ASTVisitor() {
            override fun visitLambdaExpression(
                block: CodeBlock,
                arguments: List<LocalVariable>,
                captured: MutableSet<LocalVariable>
            ): Expression {
                allCapturedLocals.addAll(captured)
                return super.visitLambdaExpression(block, arguments, captured)
            }
        }
        visit(func, captureCollector)

        allCapturedLocals.forEach { createBox(it.type) }
        MemoryLib.initMem(StandardLibASTGenerator.memLib, StandardLibASTGenerator.lambdaLib)

        visit(func, this)
        MemoryLib.initMem(StandardLibASTGenerator.memLib, StandardLibASTGenerator.lambdaLib)
    }

    override fun visitLambdaExpression(
        block: CodeBlock,
        arguments: List<LocalVariable>,
        captured: MutableSet<LocalVariable>
    ): Expression {
        val myCapture = Struct(
            name = "LambdaCapture@${index}",
            sourceAST = StandardLibASTGenerator.lambdaLib
        ).also {
            StandardLibASTGenerator.lambdaLib.structs.add(it)
            lambdaCapturesStruct.parameters.add(Parameter("capture${index++}", it.type.asNullable()))
            ctx.types.add(it.type)
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                addGC(StructGCInfo(it.type, it))
            }
        }

        val oldMappings = captureMappings.toMap()

        myCapture.parameters.addAll(captured.mapIndexed { index, variable ->
            val type = createBox(variable.type)

            Parameter("capture$index@${variable.name}", type.type).also {
                captureMappings[variable] = it
            }
        })

        val captureVar = LocalVariable("lambda@capture", lambdaCapturesStruct.type)

        val newLambda = LambdaExpression(
            listOf(captureVar) + arguments,
            run {
                val active = activeCapture
                activeCapture = myCapture
                val av = activeCaptureVal
                activeCaptureVal = captureVar

                val out = visitCodeBlock(block)

                activeCaptureVal = av
                activeCapture = active
                captureMappings.clear()
                captureMappings.putAll(oldMappings)
                out
            },
            captured
        )
        lambdaCaptureStructs[newLambda] = myCapture
        return newLambda
    }

    private fun createBox(type: Type) = boxTypes.computeIfAbsent(type) { type ->
        Struct(
            name = "LambdaBox${type.toSafeString()}",
            parameters = mutableListOf(Parameter("val", type)),
            sourceAST = StandardLibASTGenerator.lambdaLib
        ).also {
            StandardLibASTGenerator.lambdaLib.structs.add(it)
            ctx.types.add(it.type)
            if (CompilationConstants.MARK_AND_SWEEP_GC) {
                addGC(StructGCInfo(it.type, it))
            }
        }
    }

    override fun visitVariableStatement(defaultValue: Expression?, variable: LocalVariable): Statement? {
        if (allCapturedLocals.contains(variable)) {
            val boxStruct = variableBoxes.computeIfAbsent(variable) { createBox(it.type) }
            val visitedDefault = defaultValue?.let { visit(it) } ?: NullExpression
            val allocBoxCall = CallExpression(boxStruct.allocFunc, listOf(visitedDefault))
            variable.type = boxStruct.type
            return VariableStatement(allocBoxCall, variable)
        }
        return super.visitVariableStatement(defaultValue, variable)
    }

    override fun visitLocalVariableExpression(variable: LocalVariable): Expression {
        if (activeCapture != null) {
            val mappedParam = captureMappings[variable]
            if (mappedParam != null) {
                val captureParamExpr = LocalVariableExpression(activeCaptureVal!!)
                val captureExpr = MemberExpression(captureParamExpr, lambdaCapturesStruct.parameters.find { it.type.asNonNull() == activeCapture!!.type }!!, lambdaCapturesStruct)
                val boxExpr = MemberExpression(captureExpr, mappedParam, activeCapture!!)
                val boxStruct = variableBoxes[variable] ?: createBox(variable.type)
                return MemberExpression(boxExpr, boxStruct.parameters.first(), boxStruct)
            }
        }
        if (allCapturedLocals.contains(variable)) {
            val boxStruct = variableBoxes[variable] ?: createBox(variable.type)
            return MemberExpression(
                expression = LocalVariableExpression(variable),
                member = boxStruct.parameters.first(),
                struct = boxStruct
            )
        }
        return super.visitLocalVariableExpression(variable)
    }

    override fun visitLocalVariableAssignmentStatement(variable: LocalVariable, assignment: Expression): Statement? {
        if (activeCapture != null) {
            val mappedParam = captureMappings[variable]
            if (mappedParam != null) {
                val captureParamExpr = LocalVariableExpression(activeCaptureVal!!)
                val captureExpr = MemberExpression(captureParamExpr, lambdaCapturesStruct.parameters.find { it.type.asNonNull() == activeCapture!!.type }!!, lambdaCapturesStruct)
                val boxExpr = MemberExpression(captureExpr, mappedParam, activeCapture!!)
                val boxStruct = variableBoxes[variable] ?: boxTypes[variable.type] ?: createBox(variable.type)
                return VariableAssignmentStatement(
                    target = boxExpr,
                    struct = boxStruct,
                    variable = boxStruct.parameters.first(),
                    assignment = visit(assignment),
                )
            }
        }
        if (allCapturedLocals.contains(variable)) {
            val boxStruct = variableBoxes[variable] ?: boxTypes[variable.type] ?: createBox(variable.type)
            return VariableAssignmentStatement(
                target = LocalVariableExpression(variable),
                struct = boxStruct,
                variable = boxStruct.parameters.first(),
                assignment = visit(assignment)
            )
        }

        return super.visitLocalVariableAssignmentStatement(variable, assignment)
    }
}