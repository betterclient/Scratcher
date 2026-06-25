package dev.betterclient.scratcher.std.dsl

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.codegen.opcode.StopMode
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.translation.ExpressionLowerResult

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class StandardLibraryScratchDSL

@StandardLibraryScratchDSL
class CodeBuilder internal constructor(
    private val name: String,
    private val editor: ScratchEditor,
    private val nested: Boolean,
    private val warp: Boolean,
    private val userAccessible: Boolean,
) : DSLListExprs, DSLVariableExprs {
    private val statements = mutableListOf<ScratchStatement>()
    private val arguments = mutableListOf<ScratchFuncArgument>()
    private val astArguments = mutableListOf<Parameter>()
    private var currentReturnType = Type.void

    val looks get() = DSLLooks(this)
    val control get() = DSLControl(this)
    val sensing get() = DSLSensing(this)

    val String.sc
        get() = DSLFromCreator { this.scratch }

    val Int.sc
        get() = DSLFromCreator { this.toString().scratch }

    val Float.sc
        get() = DSLFromCreator { this.toString().scratch }

    fun arg(name: String, type: Type): DSLExpression {
        return DSLArgumentExpression(
            ScratchFuncArgument(obfuscate(name), ScratchType.ANY)
                .also {
                    arguments.add(it)
                    astArguments.add(Parameter(name, type))
                }
        )
    }

    fun returnArg(type: Type): DSLExpression {
        currentReturnType = type
        return DSLArgumentExpression(
            ScratchFuncArgument(obfuscate("compiler@returnIndex"), ScratchType.ANY).also {
                arguments.add(it)
            }
        )
    }

    fun boolArg(name: String): DSLBoolExpression {
        return DSLBoolArgumentExpression(
            ScratchFuncArgument(obfuscate(name), ScratchType.BOOL)
                .also {
                    arguments.add(it)
                    astArguments.add(Parameter(name, Type.bool))
                }
        )
    }

    fun variable(name: String): DSLVariable {
        return DSLVariable(ScratchVariable(obfuscate(name)).also {
            editor.addVariable(it)
        })
    }

    fun call(function: StandardLibASTFunction, vararg args: DSLExpression) {
        statements.add(CallFunction(
            function.precompiledCode, args.map { it.lower() }
        ))
    }

    internal fun addStatement(statement: ScratchStatement) {
        statements.add(statement)
    }

    internal fun compileInternal(block: CodeBuilder.() -> Unit): MutableList<ScratchStatement> {
        val newBuilder = CodeBuilder(name, editor, true, warp, userAccessible)
        newBuilder.block()
        return newBuilder.statements
    }

    internal fun toFunc(): StandardLibASTFunction {
        if (nested) throw IllegalStateException()

        return StandardLibASTFunction(
            name = name, //can't obfuscate this one
            parameters = astArguments,
            precompiledCode = ScratchASTFunction(
                name = obfuscate(name),
                args = arguments,
                code = statements,
                runWithoutScreenRefresh = warp
            ),
            returnType = currentReturnType,
            userAccessible = userAccessible
        )
    }
}

@JvmInline
@StandardLibraryScratchDSL
value class DSLLooks(private val builder: CodeBuilder) {
    fun say(message: DSLExpression, seconds: DSLExpression? = null) {
        builder.addStatement(LooksStatements.Say(message.lower(), seconds?.lower()))
    }
}

@JvmInline
@StandardLibraryScratchDSL
value class DSLSensing(private val builder: CodeBuilder) {
    val answer: DSLExpression
        get() = get(SensingExpressions.SensingData.Answer)

    fun ask(message: DSLExpression) {
        builder.addStatement(SensingStatements.Ask(message.lower()))
    }

    operator fun get(value: SensingExpressions.SensingData) = DSLFromCreator {
        SensingExpressions.SenseExpression(value)
    }
}

@JvmInline
@StandardLibraryScratchDSL
value class DSLControl(private val builder: CodeBuilder) {
    fun repeat(amount: DSLExpression, block: CodeBuilder.() -> Unit) {
        builder.addStatement(ControlStatements.RepeatTimes(amount.lower(), builder.compileInternal(block)))
    }

    fun ifThen(condition: DSLBoolExpression, block: CodeBuilder.() -> Unit) {
        builder.addStatement(ControlStatements.IfThen(condition.lower(), builder.compileInternal(block)))
    }

    fun ifElse(condition: DSLBoolExpression, thenBlock: CodeBuilder.() -> Unit, elseBlock: CodeBuilder.() -> Unit) {
        builder.addStatement(ControlStatements.IfElse(condition.lower(), builder.compileInternal(thenBlock), builder.compileInternal(elseBlock)))
    }

    fun repeatUntil(condition: DSLBoolExpression, block: CodeBuilder.() -> Unit) {
        builder.addStatement(ControlStatements.RepeatUntil(condition.lower(), builder.compileInternal(block)))
    }

    fun stop(mode: StopMode) {
        builder.addStatement(ControlStatements.Stop(mode))
    }
}

fun compile(name: String, editor: ScratchEditor, warp: Boolean, userAccessible: Boolean, block: CodeBuilder.() -> Unit): StandardLibASTFunction {
    val builder = CodeBuilder(
        name,
        editor,
        nested = false,
        warp = warp,
        userAccessible = userAccessible
    ).also { it.block() }
    return builder.toFunc()
}

fun ScratchEditor.compile(library: ASTFile, name: String, warp: Boolean = true, userAccessible: Boolean = true, block: CodeBuilder.() -> Unit): StandardLibASTFunction {
    val func = compile(name, this, warp, userAccessible, block)
    library.functions.add(func)
    return func
}

fun <T> compileInline(
    library: ASTFile,
    name: String,
    parameters: MutableList<Parameter> = mutableListOf(),
    returnType: Type = Type.void,
    useLocal: Boolean = false,
    userAccessible: Boolean = true,
    prepend: ((List<ScratchExpression>) -> List<ScratchStatement>)? = null,
    block: (List<ScratchExpression>) -> T
): InlineStandardLibFunction {
    val func = InlineStandardLibFunction(
        name = name,
        parameters = parameters,
        returnType = returnType,
        useLocal = useLocal,
        userAccessible = userAccessible,
        realCode = { args ->
            val prependList = mutableListOf<Statement>()

            if (prepend != null) {
                prependList.add(TemporaryScratchStmt(args) { scratchArgs ->
                    val statements = prepend(scratchArgs)
                    statements
                })
            }

            if (returnType == Type.void) {
                val stmt = TemporaryScratchStmt(args) { scratchArgs ->
                    listOf(block(scratchArgs) as ScratchStatement)
                }
                ExpressionLowerResult(expression = null, prepend = prependList + stmt)
            } else {
                val expr = TemporaryScratchExpr(args) { scratchArgs ->
                    block(scratchArgs) as ScratchExpression
                }
                ExpressionLowerResult(expression = expr, prepend = prependList)
            }
        }
    )

    library.functions.add(func)
    return func
}