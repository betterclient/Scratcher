package dev.betterclient.scratcher.ast.parser.code

import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.ExpressionTypes
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class Stage1Parser(val ctx: CompilationContext, val ast: ASTFile) {
    val functionResolver = FunctionResolver(this, ast)
    val expressionParser = ExpressionParser(this, ast)
    val statementParser = StatementParser(this, ast)
    val localVariables = mutableListOf<LocalVariable>() //keep track of local variables
    var currentFunction: Function? = null
    var currentTypeBindings: Map<String, Type> = emptyMap()

    fun parse() {
        ast.completedStage1Parsing = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedStage1Parsing) {
                ast.completedStage1Parsing = true
                Stage1Parser(ctx, ast).parse()
            }
        }

        if (StandardLibASTGenerator.lib.containsValue(ast)) return //do not try to parse standard lib

        parseInternal()
    }

    private fun parseInternal() {
        for (variable in ast.variables) {
            variable.ctx?.let {
                variable.defaultValue = expressionParser.parseExpression(it, variable.type)
                if (variable.type == PrimitiveType.Auto) {
                    variable.type = ExpressionTypes.getExpressionType(this.ctx, variable.defaultValue!!)
                }
            }
            variable.ctx = null
        }

        ast.eventListeners.forEach { listener ->
            listener.ctx?.let {
                listener.code.code.add(ExpressionStatement(
                    CallExpression(it, mutableListOf())
                ))
            }
        }

        currentFunction = null
        localVariables.clear()
        currentTypeBindings = emptyMap()

        ast.functions.toList().forEach {
            currentFunction = it
            currentTypeBindings = it.typeBindings
            localVariables.clear()
            if (ast.path != "typecheck") {
                TypeCheckParameters.addParameterChecks(it.code, it.parameters, ast, currentFunction)
            }
            statementParser.parseBlock(it.code, it.ctx!!)
            it.ctx = null
        }

        ast.templates.toList().forEach {
            currentFunction = it
            currentTypeBindings = it.typeParameters.associateWith { name -> PlaceholderType(name) }
            localVariables.clear()
            statementParser.parseBlock(it.code, it.ctx!!)
        }

        currentTypeBindings = emptyMap()
        currentFunction = null
    }
}