package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.std.StandardLibASTGenerator

object TypeCheckParameters {
    fun addParameterChecks(
        code: CodeBlock,
        parameters: MutableList<Parameter>,
        ast: ASTFile,
        currentFunction: Function?
    ) {
        if (CompilationConstants.DISABLE_TYPE_CHECKER || ast.simplePath == "gc_impl") return

        for (parameter in parameters) {
            if (parameter.type == PrimitiveType.Float) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloatObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloat" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not a float!")
                    ).toMutableList())
                ))
            } else if (parameter.type == PrimitiveType.Integer) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkIntObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkInt" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not an integer!")
                    ).toMutableList())
                ))
            }
        }
    }
}