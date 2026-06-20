package dev.betterclient.ast

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.std.StandardLibASTGenerator
import java.io.File

val asts = mutableMapOf<String, ASTFile>()
val types = mutableListOf(
    Type.str,
    Type.int,
    Type.float,
    Type.bool,
    Type.void,
)

class ASTReader(source: String, val fullPath: String) {
    val initialRead = read(source)

    fun read(): ASTFile {
        val ast = ASTFile(
            fullPath
        )
        asts[fullPath] = ast

        //first read for types
        for (context in initialRead.topLevelElement().filter { it.structDecl() != null }) {
            val struct = context.structDecl()!!
            val structAST = Struct(struct.IDENTIFIER().text)
            structAST.parseInfo = struct
            types.add(Type(structAST.name, ast))
            ast.structs.add(structAST)
        }

        for (context in initialRead.importDecl()) {
            if (context.IDENTIFIER() != null) {
                val stdLib = StandardLibASTGenerator.lib[context.IDENTIFIER()!!.text]?:
                    throw NullPointerException("Standard library module ${context.IDENTIFIER()!!.text} not found")

                ast.imports.add(stdLib)
            } else {
                val text = context.PLAIN_STRING()!!.text.removeSurrounding("\"")
                val currentFile = File(fullPath).absoluteFile
                val parentDir = currentFile.parentFile
                val importedFile = File(parentDir, text).canonicalFile
                val importedPath = importedFile.absolutePath

                if (!importedFile.exists()) {
                    throw NoSuchFileException(importedFile, reason = "Imported file not found")
                }

                val importedAST = asts[importedPath] ?: run {
                    val sourceCode = importedFile.readText()
                    val reader = ASTReader(sourceCode, importedPath)
                    reader.read()
                }

                ast.imports.add(importedAST)
            }
        }

        //second read for parameters
        ast.structs.forEach { struct ->
            for (field in struct.parseInfo!!.structField()) {
                struct.parameters.add(
                    Parameter(field.IDENTIFIER().text, figureOutType(ast, field.type()))
                )
            }
        }

        for (context in initialRead.topLevelElement()) {
            if (context.funcDecl() != null) {
                val func = context.funcDecl()!!
                val funcAST = Function(
                    func.IDENTIFIER().text,
                    parameters = (func.paramList()?.param()?: listOf()).map {
                        Parameter(it.IDENTIFIER().text, figureOutType(ast, it.type()))
                    }.toMutableList(),
                    returnType = figureOutType(ast, func.type())
                )
                funcAST.ctx = func

                ast.functions.add(funcAST)
            } else if (context.tlVarDecl() != null) {
                val variable = context.tlVarDecl()!!
                val astVariable = TLVariable(
                    variable.IDENTIFIER().text,
                    variable.isConst == null,
                    figureOutType(ast, variable.type())
                )
                astVariable.ctx = variable.expression()

                ast.variables.add(astVariable)
            }
        }

        return ast
    }
}

fun figureOutType(currentAST: ASTFile, type: ScratcherLangParser.TypeContext): Type {
    if (type.primitiveType() != null) {
        return types.find { it.sourceAST == null && it.name == type.primitiveType()!!.text }!! //npe unreachable
    } else {
        val typePath = type.typePath()!!
        val id = typePath.IDENTIFIER()
        when (id.size) {
            2 -> {
                //from other file
                val otherFile = currentAST.imports.find {
                    File(it.path).nameWithoutExtension == id[0].text
                }
                otherFile ?: throw Exception("Type ${type.text} not found in any imports")
                return types.find { id[1].text == it.name && it.sourceAST == otherFile }
                    ?: throw Exception("Type ${type.text} not found in any imports")
            }
            1 -> {
                //current file
                return types.find { id[0].text == it.name && it.sourceAST == currentAST }
                    ?: throw Exception("Type ${type.text} not found in current file")
            }
            else -> {
                throw UnsupportedOperationException("Too many :: in type ${type.text}")
            }
        }
    }
}