package dev.betterclient.scratcher.std

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.LooksStatements
import dev.betterclient.scratcher.codegen.ast.SBoolParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchBoolExpression
import dev.betterclient.scratcher.codegen.ast.ScratchExpression
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.codegen.ast.VariableStatements
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.obfuscate
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class StandardLibraryScratchDSL

@StandardLibraryScratchDSL
class CodeBuilder internal constructor(
    private val name: String,
    private val editor: ScratchEditor,
    private val nested: Boolean,
    private val warp: Boolean
) : DSLListExprs, DSLVariableExprs {
    private val statements = mutableListOf<ScratchStatement>()
    private val arguments = mutableListOf<ScratchFuncArgument>()
    private val astArguments = mutableListOf<Parameter>()

    val looks get() = DSLLooks(this)
    val control get() = DSLControl(this)

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

    internal fun addStatement(statement: ScratchStatement) {
        statements.add(statement)
    }

    internal fun compileInternal(block: CodeBuilder.() -> Unit): MutableList<ScratchStatement> {
        val newBuilder = CodeBuilder(name, editor, true, warp)
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
            )
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
}

fun compile(name: String, editor: ScratchEditor, warp: Boolean, block: CodeBuilder.() -> Unit): StandardLibASTFunction {
    val builder = CodeBuilder(name, editor, false, warp).also { it.block() }
    return builder.toFunc()
}

fun ScratchEditor.compile(library: ASTFile, name: String, warp: Boolean = false, block: CodeBuilder.() -> Unit): StandardLibASTFunction {
    val func = compile(name, this, warp, block)
    library.functions.add(func)
    return func
}