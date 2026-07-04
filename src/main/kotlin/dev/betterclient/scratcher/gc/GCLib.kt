package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.TemporaryCallStatement
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.dsl.*
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.std.lib.MemoryLib
import dev.betterclient.scratcher.translation.ExpressionLowerResult

//this just contains helper functions, actual gc implemented in resources/gc.sc
object GCLib {
    val markedList = ScratchList(obfuscate("GC: Marked"))
    val gcList = ScratchList(obfuscate("Type metadata"))
    val fieldsList = ScratchList(obfuscate("Type fields"))
    val rootsList = ScratchList(obfuscate("GC: Roots"))
    val reflectList = ScratchList(obfuscate("GC: TLReflect"))

    fun init(lib: ASTFile) {
        accessFunctions(lib, MemoryLib.allocAddressList, "AllocAddressList")
        accessFunctions(lib, MemoryLib.allocNameList, "AllocNameList")
        accessFunctions(lib, MemoryLib.freeList, "FreeList")
        accessFunctions(lib, rootsList, "Roots")
        accessFunctions(lib, reflectList, "Reflect")

        markedListFunctions(lib)

        compileInline(
            lib,
            "isStack",
            parameters = mutableListOf(Parameter("type", Type.str)),
            returnType = Type.bool
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 1.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldsStart",
            parameters = mutableListOf(Parameter("type", Type.str)),
            returnType = Type.int
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 2.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldsCount",
            parameters = mutableListOf(Parameter("type", Type.str)),
            returnType = Type.int
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 3.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldType",
            parameters = mutableListOf(Parameter("index", Type.int)),
            returnType = Type.str
        ) {
            ListExpressions.ItemAtIndex(fieldsList, it[0])
        }

        compileInline(
            lib,
            "getHeap",
            parameters = mutableListOf(Parameter("index", Type.int)),
            returnType = Type.int
        ) {
            ListExpressions.ItemAtIndex(MemoryLib.heap, it[0])
        }

        compileInline(
            lib,
            "getHeapSize",
            returnType = Type.int
        ) {
            ListExpressions.LengthOfList(MemoryLib.heap)
        }

        compileInline(
            lib,
            "freeHeap",
            parameters = mutableListOf(Parameter("index", Type.int))
        ) {
            CallFunction(
                func = MemoryLib.free.precompiledCode,
                args = listOf(it[0], "1".scratch)
            )
        }

        freeFunc(lib, Type.str.list(), "StrArray")
        freeFunc(lib, Type.int.list(), "IntArray")

        lib.functions.add(InlineStandardLibFunction(
            "isReflectGC",
            returnType = Type.bool,
            sourceAST = lib,
            useLocal = false,
            warp = true,
            realCode = {
                ExpressionLowerResult(BooleanLiteral(CompilationConstants.REFLECT_GC))
            }
        ))

        compileInline(
            lib,
            "reflect",
            returnType = Type.int,
            parameters = mutableListOf(Parameter("varName", Type.str))
        ) {
            ListExpressions.ReflectVariable(it[0])
        }
    }

    private fun freeFunc(
        lib: ASTFile,
        type: Type,
        name: String
    ) {
        compileInline(
            lib,
            "free$name",
            parameters = mutableListOf(Parameter("array", type)),
        ) {
            CallFunction(
                func = ListLib.free.precompiledCode,
                args = listOf(it[0])
            )
        }
    }

    private fun markedListFunctions(lib: ASTFile) {
        accessFunctions(lib, markedList, "Marked")
        compileInline(
            lib,
            "addMarked",
            parameters = mutableListOf(Parameter("index", Type.int))
        ) {
            ListStatements.AddToList(
                markedList,
                it[0]
            )
        }
        compileInline(
            lib,
            "isMarked",
            parameters = mutableListOf(Parameter("item", Type.int)),
            returnType = Type.bool
        ) {
            ListExpressions.ContainsItemInList(
                markedList,
                it[0]
            )
        }
        compileInline(
            lib,
            "clearMarked"
        ) {
            ListStatements.ClearList(markedList)
        }
    }

    private fun accessFunctions(lib: ASTFile, list: ScratchList, name: String) {
        compileInline(
            lib,
            "get$name",
            parameters = mutableListOf(Parameter("index", Type.int)),
            returnType = Type.str
        ) {
            ListExpressions.ItemAtIndex(
                list,
                it[0]
            )
        }

        compileInline(
            lib,
            "lengthOf$name",
            returnType = Type.int
        ) {
            ListExpressions.LengthOfList(list)
        }
    }

    fun gcFuncs(): List<ASTEventListener> {
        if (!CompilationConstants.AUTOMATIC_GC) return listOf()

        return if (!CompilationConstants.MANUAL_MEMORY) {
            StandardLibASTGenerator.gc.eventListeners
        } else listOf()
    }

    fun populateList(editor: ScratchEditor) {
        val fields = mutableListOf<String>()
        var currentFieldIndex = 1

        gcList.items.addAll(
            gcNames.flatMap { info ->
                val typeFields = info.toGCList().split("-").filter { it.isNotEmpty() }
                val start = currentFieldIndex
                val count = typeFields.size
                fields.addAll(typeFields)
                currentFieldIndex += count

                listOf(
                    (info is StackGCInfo).toString(),
                    start.toString(),
                    count.toString()
                )
            }
        )

        fieldsList.items.addAll(fields)

        editor.addList(gcList)
        editor.addList(fieldsList)
        editor.addList(markedList)
        editor.addList(rootsList)
        editor.addList(reflectList)
    }

    fun initCaller(gc: ASTFile, gcLib: ASTFile) {
        gcLib.functions.add(gc.functions.find { it.name == "collect" }!!)
    }

    fun generate(vars: List<TLVariable>, translate: (TLVariable) -> ScratchVariable) {
        val eligible = vars.filter { !it.type.isPrimitive }
        val markTypeFunc = StandardLibASTGenerator.gc.functions.find { it.name == "markType" }!!
        val original = StandardLibASTGenerator.gc.functions.find { it.name == "markTopLevels" }!!
        if (CompilationConstants.REFLECT_GC) {
            reflectList.items.addAll(eligible.flatMap { variable ->
                listOf(
                    if (variable.type.inner != null) {
                        "${"l".repeat(variable.type.name.count { it == '[' })}${findGC(variable.type.raw())}"
                    } else {
                        findGC(variable.type).toString()
                    },
                    translate(variable).name
                )
            })
            return
        }

        original.code.code.clear()
        original.code.code.addAll(
            eligible.map { variable ->
                val typeStr = if (variable.type.inner != null) {
                    "${"l".repeat(variable.type.name.count { it == '[' })}${findGC(variable.type.raw())}"
                } else {
                    findGC(variable.type).toString()
                }

                ExpressionStatement(
                    CallExpression(
                        func = markTypeFunc,
                        arguments = listOf(
                            VariableExpression(variable, variable.sourceAST),
                            StringLiteral(typeStr)
                        )
                    )
                )
            }
        )
    }
}