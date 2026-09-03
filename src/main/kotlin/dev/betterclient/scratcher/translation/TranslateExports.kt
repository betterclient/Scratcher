package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.ProcedureArgumentString
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class TranslateExports(val reachable: MutableList<Function>, val entrypoints: MutableList<ASTEventListener>) {
    fun run() {
        reachable.filter { it.export }.forEach {
            throwIfNotValid(it)
            translate(it)
        }
    }

    private fun translate(function: Function) {
        val compilerLib = StandardLibASTGenerator.compilerLib
        val args = function.parameters.map { ProcedureArgumentString(it.name) }
        entrypoints.add(ASTEventListener(
            event = EventListener.ProcedureCall(
                ScratchFunction(
                    name = "Exported: ${function.sourceAST.simplePath}::${function.name}",
                    runWithoutScreenRefresh = function.warp,
                    first = null,
                    arguments = args
                )
            ),
            sourceAST = compilerLib,
            code = CodeBlock()
        ).also { event ->
            compilerLib.eventListeners.add(event)
            val params = function.parameters.map { Parameter(it.name, it.type) }.toMutableList()
            event.ctx = Function(
                name = "export_caller_${getUniqueName()}",
                code = CodeBlock().also { block ->
                    val callExpr = CallExpression(function, params.map { ParameterExpression(it) })
                    if (function.returnType == PrimitiveType.Void) {
                        block.code.add(ExpressionStatement(
                            callExpr
                        ))
                    } else {
                        val outVar = TLVariable(
                            "Exported Return: ${function.sourceAST.simplePath}::${function.name}",
                            true,
                            function.returnType,
                            null,
                            compilerLib
                        )
                        compilerLib.variables.add(outVar)
                        block.code.add(TLVariableAssignmentStatement(
                            variable = outVar,
                            sourceAST = compilerLib,
                            assignment = callExpr
                        ))
                    }
                },
                export = false,
                warp = function.warp,
                operator = false,
                returnType = PrimitiveType.Void,
                sourceAST = StandardLibASTGenerator.compilerLib,
                parameters = params
            ).also { func ->
                StandardLibASTGenerator.compilerLib.functions.add(func)
                reachable.add(func)
            }
        })

    }

    private fun throwIfNotValid(function: Function) {
        if (function.typeBindings.isNotEmpty() || function.typeParameters.isNotEmpty()) {
            throw GeneralCompilerException("Generics not allowed in exported function: ${function.sourceAST.simplePath}::${function.name}")
        }

        if (function.returnType !is PrimitiveType) {
            throw GeneralCompilerException("Export function ${function.sourceAST.simplePath}::${function.name} does not have a primitive return, found ${function.returnType}")
        }

        function.parameters.forEach {
            if (it.type !is PrimitiveType) {
                throw GeneralCompilerException("Export function ${function.sourceAST.simplePath}::${function.name}'s parameter ${it.name} is not a primitive type, found ${it.type}")
            }
        }
    }
}