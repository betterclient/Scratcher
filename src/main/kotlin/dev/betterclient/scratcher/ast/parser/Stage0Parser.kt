package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.Key
import dev.betterclient.scratcher.gc.StructGCInfo
import dev.betterclient.scratcher.gc.addGC
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import java.io.File

class CompilationContext {
    fun generateGCNames() {
        if (CompilationConstants.MANUAL_MEMORY) return

        asts.values.flatMap { it.structs }.forEach {
            addGC(StructGCInfo(it.type, it))
        }
    }

    var eventListenerIndex: Int = 0
    val asts = mutableMapOf<String, ASTFile>()
    val types = mutableListOf<Type>(
        PrimitiveType.Str,
        PrimitiveType.Integer,
        PrimitiveType.Float,
        PrimitiveType.Bool,
        PrimitiveType.Void,
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
            ast.structs.find { it.name == structAST.name }?.let {
                throw DuplicateDefinitionException("Duplicate struct definition ${ast.simplePath}::${it.name}")
            }

            ast.structs.add(structAST)
        }

        for (context in initialRead.topLevelElement().filter { it.enumDecl() != null }) {
            val enum = context.enumDecl()!!
            val enumAST = ASTEnum(
                enum.IDENTIFIER(0)!!.text,
                enum.IDENTIFIER().subList(1, enum.IDENTIFIER().size).map { it.text },
                ast
            )
            ctx.types.add(enumAST.type)
            ast.enums.find { it.name == enumAST.name }?.let {
                throw DuplicateDefinitionException("Duplicate enum definition ${ast.simplePath}::${it.name}")
            }

            ast.enums.add(enumAST)
        }

        for (context in initialRead.importDecl()) {
            val plainStringLiteralCtx = context.plainStringLiteral()

            if (plainStringLiteralCtx == null) {
                //stdlib import
                val moduleNode = context.IDENTIFIER(0)
                    ?: throw NotFoundException("Module identifier not found in import declaration")
                val moduleName = moduleNode.text

                val alias = context.IDENTIFIER(1)?.text

                val stdLib = StandardLibASTGenerator.lib[moduleName]
                    ?: throw NotFoundException("Standard library module $moduleName not found")
                if (moduleName == "gc" && CompilationConstants.MANUAL_MEMORY) throw GeneralCompilerException("GC is disabled! Cannot access \"gc\" from ${ast.simplePath}")
                if (StandardLibASTGenerator.isRestricted(stdLib)) throw GeneralCompilerException("Standard library module $moduleName is restricted!")

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
                    throw NotFoundException("Imported file not found at $importedPath")
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
                val isNullable = type is NullableType
                if (isNullable && type.isPrimitive) {
                    throw NotNullableException("Primitive fields cannot be nullable in ${ast.simplePath}::${struct.name}")
                }

                if (type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${struct.name} has an argument with type void.")
                struct.parameters.add(
                    Parameter(
                        field.IDENTIFIER().text,
                        type
                    )
                )
            }
            checkDuplicates(struct.parameters, "struct ${ast.simplePath}::${struct.name}")
            struct.parseInfo = null
        }

        for (context in initialRead.topLevelElement()) {
            if (context.funcDecl() != null) {
                val func = context.funcDecl()!!
                val parameterList = (func.paramList()?.param() ?: listOf()).map {
                    val type = figureOutType(ctx, ast, it.type())
                    if (type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${func.IDENTIFIER().text} has an argument with type void.")
                    Parameter(it.IDENTIFIER().text, type)
                }.toMutableList()

                checkDuplicates(parameterList, "function ${ast.simplePath}::${func.IDENTIFIER().text}")

                val returnType = figureOutType(ctx, ast, func.type())
                val funcName = func.IDENTIFIER().text

                val duplicate = ast.functions.find { existing ->
                    existing.name == funcName &&
                            existing.parameters.map { it.type } == parameterList.map { it.type }
                }

                if (duplicate != null) {
                    val sig = parameterList.joinToString(", ") { it.type.toString() }
                    throw DuplicateDefinitionException("Duplicate function definition in ${ast.simplePath}::$funcName($sig)")
                }

                var isWarp = false
                var isExport = false
                func.modifier().forEach { modifier ->
                    when {
                        modifier.EXPORT() != null -> {
                            if (isExport) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                throw GeneralCompilerException("Double export in ${ast.simplePath}::$funcName($sig)")
                            }
                            isExport = true
                        }
                        modifier.WARP() != null -> {
                            if (isWarp) {
                                val sig = parameterList.joinToString(", ") { it.type.toString() }
                                throw GeneralCompilerException("Double warp in ${ast.simplePath}::$funcName($sig)")
                            }
                            isWarp = true
                        }
                    }
                }

                val funcAST = Function(
                    name = funcName,
                    parameters = parameterList,
                    returnType = returnType,
                    warp = isWarp,
                    export = isExport,
                    sourceAST = ast
                )
                funcAST.ctx = func.block()

                ast.functions.add(funcAST)
            } else if (context.tlVarDecl() != null) {
                val variable = context.tlVarDecl()!!
                if (variable.expression() == null && variable.AUTO() != null) {
                    throw GeneralCompilerException("Top-level auto variable ${variable.IDENTIFIER().text} must have an initializer.")
                }

                val astVariable = TLVariable(
                    variable.IDENTIFIER().text,
                    variable.isConst == null,
                    variable.type()?.let {
                        figureOutType(ctx, ast, it)
                    }?: PrimitiveType.Auto,
                    sourceAST = ast
                )
                astVariable.ctx = variable.expression()
                if (astVariable.type == PrimitiveType.Void) throw VoidVariableException("${ast.simplePath}::${astVariable.name} is type void.")
                if (ast.variables.find { it.name == astVariable.name } != null) {
                    throw DuplicateDefinitionException("Duplicate variable definition ${astVariable.name}")
                }

                ast.variables.add(astVariable)
            } else if (context.eventDecl() != null) {
                val event = context.eventDecl()!!
                val listener = when (event.IDENTIFIER().text) {
                    "GreenFlag" if event.eventArg() == null -> EventListener.GreenFlag
                    "KeyPressed" if event.eventArg() != null -> EventListener.KeyPressed(Key.from(event.eventArg()!!))
                    else -> throw GeneralCompilerException("Unknown event type ${event.text}, expected GreenFlag or KeyPressed")
                }

                val func = Function(
                    name = "compiler@eventlistener@${obfuscate(event.IDENTIFIER().text)}i${ctx.eventListenerIndex++}",
                    parameters = mutableListOf(),
                    returnType = PrimitiveType.Void,
                    export = false,
                    warp = false,
                    sourceAST = ast,
                    isEventListener = true
                )
                func.ctx = event.block()

                ast.functions.add(func)
                ast.eventListeners.add(ASTEventListener(
                    listener, CodeBlock(), ast
                ).also {
                    it.ctx = func
                })
            }
        }

        return ast
    }

    private fun checkDuplicates(list: List<Parameter>, error: String) {
        val found = mutableListOf<String>()
        list.forEach {
            if (found.contains(it.name)) {
                throw DuplicateDefinitionException("Duplicate parameter definition ${it.name} in $error")
            }
            found.add(it.name)
        }
    }
}

fun figureOutType(context: CompilationContext, currentAST: ASTFile, type: ScratcherLangParser.TypeContext): Type {
    return when(type) {
        is ScratcherLangParser.ArrayTypeContext -> {
            ListType(
                figureOutType(context, currentAST, type.type())
            )
        }
        is ScratcherLangParser.NullableTypeContext -> {
            NullableType(
                figureOutType(context, currentAST, type.type())
            )
        }
        is ScratcherLangParser.PathTypeContext -> {
            val id = type.typePath().IDENTIFIER()
            when (id.size) {
                2 -> {
                    //from other file
                    val otherFile = currentAST.imports[id[0].text]?: throw NotFoundException("Type ${type.text} not found in any imports")
                    context.types.find {
                        if (it !is SimpleType) return@find false
                        id[1].text == it.name && it.sourceAST == otherFile
                    }?: throw NotFoundException("Type ${type.text} not found in any imports")
                }
                1 -> {
                    //current file
                    context.types.find {
                        if (it !is SimpleType) return@find false
                        id[0].text == it.name && it.sourceAST == currentAST
                    }?: throw NotFoundException("Type ${type.text} not found in current file")
                }
                else -> {
                    throw GeneralCompilerException("Too many :: in type ${type.text}")
                }
            }
        }
        is ScratcherLangParser.PrimTypeContext -> {
            context.types.find {
                if (it !is PrimitiveType) return@find false
                it.toString() == type.primitiveType().text
            }!!
        }

        else -> throw GeneralCompilerException("Type parser not implemented for ${type.text}")
    }
}