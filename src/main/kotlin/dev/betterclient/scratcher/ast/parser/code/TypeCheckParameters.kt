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
            when (parameter.type) {
                PrimitiveType.Float -> {
                    code.code.add(
                        this.forDataType(
                            parameter = parameter,
                            ast = ast,
                            currentFunction = currentFunction,
                            type = "Float",
                            typeInError = "a float!"
                        )
                    )
                }
                PrimitiveType.Integer -> {
                    code.code.add(
                        this.forDataType(
                            parameter = parameter,
                            ast = ast,
                            currentFunction = currentFunction,
                            type = "Int",
                            typeInError = "an integer!"
                        )
                    )
                }
                PrimitiveType.Char -> {
                    code.code.add(
                        this.forDataType(
                            parameter = parameter,
                            ast = ast,
                            currentFunction = currentFunction,
                            type = "Char",
                            typeInError = "a char"
                        )
                    )
                }
                else -> {}
            }
        }
    }

    fun forDataType(parameter: Parameter, ast: ASTFile, currentFunction: Function?, type: String, typeInError: String): ExpressionStatement {
        val func = if (CompilationConstants.OBFUSCATION) {
            StandardLibASTGenerator.typeChecker.functions.find { it.name == "check${type}Obf" }
        } else {
            StandardLibASTGenerator.typeChecker.functions.find { it.name == "check$type" }
        }!!

        return ExpressionStatement(
            CallExpression(
                func, listOfNotNull(
                    ParameterExpression(parameter),
                    if (CompilationConstants.OBFUSCATION) null else
                        StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not $typeInError!")
                ).toMutableList()
            )
        )
    }
}