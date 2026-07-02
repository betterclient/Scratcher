package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.dsl.*
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.std.lib.MemoryLib

//this just contains helper functions, actual gc implemented in resources/gc.sc
object GCLib {
    val markedList = ScratchList(obfuscate("GC: Marked"))
    val gcList = ScratchList(obfuscate("Type metadata"))
    fun init(lib: ASTFile) {
        accessFunctions(lib, MemoryLib.allocAddressList, "AllocAddressList")
        accessFunctions(lib, MemoryLib.allocNameList, "AllocNameList")
        accessFunctions(lib, MemoryLib.freeList, "FreeList")

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
                (((originalIndex - 1.sc) * 3.sc) + 2.sc).lower()
            )
        }
        compileInline(
            lib,
            "getInternalNames",
            parameters = mutableListOf(Parameter("type", Type.str)),
            returnType = Type.str
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 3.sc).lower()
            )
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
        return if (!CompilationConstants.MANUAL_MEMORY) {
            StandardLibASTGenerator.gc.eventListeners
        } else listOf()
    }

    fun populateList(editor: ScratchEditor) {
        gcList.items.addAll(
            gcNames.flatMapIndexed { index, info ->
                listOf(
                    (index + 1).toString(),
                    (info is StackGCInfo).toString(),
                    info.toGCList()
                )
            }
        )
        editor.addList(gcList)
        editor.addList(markedList)
    }
}