package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.ParameterExpression
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.dsl.*
import dev.betterclient.scratcher.std.lib.MemoryLib

object GCLib {
    val gcList = ScratchList(obfuscate("Type metadata"))
    fun init(lib: ASTFile) {
        accessFunctions(lib, MemoryLib.allocAddressList, "AllocAddressList")
        accessFunctions(lib, MemoryLib.allocNameList, "AllocNameList")

        compileInline(
            lib,
            "isStack",
            parameters = mutableListOf(Parameter("type", Type.str)),
            returnType = Type.bool
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                ((originalIndex * 3.sc) + 1.sc).lower()
            )
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
    }
}