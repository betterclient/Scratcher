package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.ScratchASTEventListener
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchStatement
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.rand
import dev.betterclient.scratcher.std.MemoryLibrary

class EntrypointTranslator(val getFunctionLocalSize: (Function) -> Int, val toScratch: (Function) -> ScratchASTFunction) {
    fun translateAll(editor: ScratchEditor, listeners: List<ASTEventListener>) {
        generateResetEvent(editor, listeners.size)

        var index = 1
        for (listener in listeners) {
            translateListener(editor, listener, index)

            index++
        }
    }

    private fun translateListener(
        editor: ScratchEditor,
        listener: ASTEventListener,
        reservedIndex: Int
    ) {
        val func = listener.ctx ?: return
        val localSize = getFunctionLocalSize(func)
        val exec = if (localSize == 0) {
            listOf(CallFunction(
                toScratch(func), listOf("-1".scratch)
            ))
        } else {
            listOf(
                CallFunction(
                    MemoryLibrary.alloc.precompiledCode,
                    listOf(localSize.toString().scratch, reservedIndex.toString().scratch) //allocate slots for the entrypoint
                ),
                CallFunction(
                    toScratch(func), listOf( //call the entrypoint
                        ListExpressions.ItemAtIndex(MemoryLibrary.heap, reservedIndex.toString().scratch)
                    )
                )
            )
        }

        editor.addEventListener(ScratchASTEventListener(
            event = listener.event,
            code = listOf(
                ControlStatements.Wait("0".scratch), //give time to the reset entrypoint so it can allocate slots for us (idk if this is actually needed)
            ) + exec
        ))
    }

    private fun generateResetEvent(editor: ScratchEditor, entrypointCount: Int) {
        val func = ScratchASTFunction(
            rand(),
            runWithoutScreenRefresh = true,
            args = listOf(),
            code = mutableListOf<ScratchStatement>(
                ListStatements.ClearList(MemoryLibrary.heap),
                ListStatements.ClearList(MemoryLibrary.freeList)
            ).also { list ->
                repeat(entrypointCount) {
                    list.add(ListStatements.AddToList(MemoryLibrary.heap, "reserved".scratch))
                }
            }
        )

        editor.addFunction(func)
        editor.addEventListener(ScratchASTEventListener(
            event = EventListener.GreenFlag,
            code = listOf(
                CallFunction(
                    func, listOf()
                )
            )
        ))
    }
}