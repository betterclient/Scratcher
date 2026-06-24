package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.read
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.Key
import dev.betterclient.scratcher.codegen.rand
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import java.io.File

class CompilationContext {
    val asts = mutableMapOf<String, ASTFile>()
    val types = mutableListOf(
        Type.str,
        Type.int,
        Type.float,
        Type.bool,
        Type.void,
    )
}

class ASTReader(val ctx: CompilationContext, source: String, val fullPath: String) {
    val initialRead = read(source)

    fun read(): ASTFile {
        val ast = ASTFile(fullPath)
        ctx.asts[fullPath] = ast

        //first read for types
        for (context in initialRead.topLevelElement().filter { it.structDecl() != null }) {
            val struct = context.structDecl()!!
            val structAST =
                Struct(struct.IDENTIFIER().text, sourceAST = ast)
            structAST.parseInfo = struct
            ctx.types.add(structAST.type)
            ast.structs.add(structAST)
        }

        for (context in initialRead.importDecl()) {
            val plainStringLiteralCtx = context.plainStringLiteral()

            if (plainStringLiteralCtx == null) {
                //stdlib import
                val moduleNode = context.IDENTIFIER(0)
                    ?: throw NullPointerException("Module identifier not found in import declaration")
                val moduleName = moduleNode.text

                val alias = context.IDENTIFIER(1)?.text

                val stdLib = StandardLibASTGenerator.lib[moduleName]
                    ?: throw NullPointerException("Standard library module $moduleName not found")

                val key = alias ?: moduleName
                ast.imports[key] = stdLib
            } else {
                //file import
                val text = plainStringLiteralCtx.text.removeSurrounding("\"")
                val currentFile = File(fullPath).absoluteFile
                val parentDir = currentFile.parentFile
                val importedFile = File(parentDir, text).canonicalFile
                val importedPath = importedFile.absolutePath

                if (!importedFile.exists()) {
                    throw Exception("Imported file not found at $importedPath")
                }

                val importedAST = ctx.asts[importedPath] ?: run {
                    val sourceCode = importedFile.readText()
                    val reader = ASTReader(ctx, sourceCode, importedPath)
                    reader.read()
                }

                val alias = context.IDENTIFIER(0)?.text

                val key = alias ?: importedFile.nameWithoutExtension
                ast.imports[key] = importedAST
            }
        }

        //second read for parameters
        ast.structs.forEach { struct ->
            for (field in struct.parseInfo!!.structField()) {
                val type = figureOutType(ctx, ast, field.type())
                if (type == Type.void) throw UnsupportedOperationException("${ast.path}::${struct.name} has an argument with type void.")
                struct.parameters.add(
                    Parameter(
                        field.IDENTIFIER().text,
                        type
                    )
                )
            }
            struct.parseInfo = null
        }

        for (context in initialRead.topLevelElement()) {
            if (context.funcDecl() != null) {
                val func = context.funcDecl()!!
                val funcAST = Function(
                    func.IDENTIFIER().text,
                    parameters = (func.paramList()?.param() ?: listOf()).map {
                        val type = figureOutType(ctx, ast, it.type())
                        if (type == Type.void) throw UnsupportedOperationException("${ast.path}::${func.IDENTIFIER().text} has an argument with type void.")
                        Parameter(
                            it.IDENTIFIER().text,
                            type
                        )
                    }.toMutableList(),
                    returnType = figureOutType(ctx, ast, func.type())
                )
                funcAST.ctx = func.block()

                ast.functions.add(funcAST)
            } else if (context.tlVarDecl() != null) {
                val variable = context.tlVarDecl()!!
                val astVariable = TLVariable(
                    variable.IDENTIFIER().text,
                    variable.isConst == null,
                    figureOutType(ctx, ast, variable.type())
                )
                astVariable.ctx = variable.expression()
                if (astVariable.type == Type.void) throw UnsupportedOperationException("${ast.path}::${astVariable.name} is type void.")

                ast.variables.add(astVariable)
            } else if (context.eventDecl() != null) {
                val event = context.eventDecl()!!
                val listener = when (event.IDENTIFIER().text) {
                    "GreenFlag" if event.eventArg() == null -> EventListener.GreenFlag
                    "KeyPressed" if event.eventArg() != null -> EventListener.KeyPressed(Key.from(event.eventArg()!!))
                    else -> throw UnsupportedOperationException("Unknown event type ${event.IDENTIFIER().text}")
                }

                val func = Function(
                    name = "compiler@eventlistener@${rand()}",
                    parameters = mutableListOf(),
                    returnType = Type.void,
                )
                func.ctx = event.block()

                ast.functions.add(func)
                ast.eventListeners.add(ASTEventListener(
                    listener, CodeBlock()
                ).also {
                    it.ctx = func
                })
            }
        }

        return ast
    }
}

fun figureOutType(context: CompilationContext, currentAST: ASTFile, type: ScratcherLangParser.TypeContext): Type {
    if (type.primitiveType() != null) {
        return context.types.find { it.sourceAST == null && it.name == type.primitiveType()!!.text }!! //npe unreachable
    } else {
        val typePath = type.typePath()!!
        val id = typePath.IDENTIFIER()
        when (id.size) {
            2 -> {
                //from other file
                val otherFile = currentAST.imports[id[0].text]?: throw Exception("Type ${type.text} not found in any imports")
                return context.types.find { id[1].text == it.name && it.sourceAST == otherFile }
                    ?: throw Exception("Type ${type.text} not found in any imports")
            }
            1 -> {
                //current file
                return context.types.find { id[0].text == it.name && it.sourceAST == currentAST }
                    ?: throw Exception("Type ${type.text} not found in current file")
            }
            else -> {
                throw UnsupportedOperationException("Too many :: in type ${type.text}")
            }
        }
    }
}