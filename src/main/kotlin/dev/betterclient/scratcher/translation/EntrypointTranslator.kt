package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
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
import dev.betterclient.scratcher.gc.GCInfo
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.gc.name
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.lib.MemoryLib

class EntrypointTranslator(
    val getFunctionLocalSize: (Function) -> Pair<Int, GCInfo>,
    val toScratch: (Function) -> ScratchASTFunction,
    val topLevelInit: ScratchASTFunction,
    val topLevelInitLocals: Pair<Int, GCInfo>
) {
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
        val (localSize, gc) = getFunctionLocalSize(func)
        val exec = if (localSize == 0) {
            listOf(
                CallFunction(
                    toScratch(func), listOf("-1".scratch)
                )
            )
        } else if (CompilationConstants.MARK_AND_SWEEP_GC) {
            listOf(
                CallFunction(
                    MemoryLib.alloc.precompiledCode,
                    listOf(localSize.toString().scratch, gc.name.toString().scratch, reservedIndex.toString().scratch) //allocate slots for the entrypoint
                ),
                ListStatements.AddToList( //tell the garbage collector about us
                    GCLib.rootsList,
                    ListExpressions.ItemAtIndex(MemoryLib.heap, reservedIndex.toString().scratch)
                ),
                CallFunction(
                    toScratch(func), listOf( //call the entrypoint
                        ListExpressions.ItemAtIndex(MemoryLib.heap, reservedIndex.toString().scratch)
                    )
                )
            )
        } else {
            listOf(
                CallFunction(
                    MemoryLib.alloc.precompiledCode,
                    listOf(localSize.toString().scratch, reservedIndex.toString().scratch) //allocate slots for the entrypoint
                ),
                CallFunction(
                    toScratch(func), listOf( //call the entrypoint
                        ListExpressions.ItemAtIndex(MemoryLib.heap, reservedIndex.toString().scratch)
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
            obfuscate("compiler@reset"),
            runWithoutScreenRefresh = true,
            args = listOf(),
            code = mutableListOf<ScratchStatement>(
                ListStatements.ClearList(MemoryLib.heap),
                ListStatements.ClearList(MemoryLib.freeList)
            ).also { list ->
                if (CompilationConstants.MARK_AND_SWEEP_GC) {
                    list.add(ListStatements.ClearList(GCLib.rootsList))
                }
                repeat(entrypointCount) {
                    list.add(ListStatements.AddToList(MemoryLib.heap, "reserved".scratch))
                }
                val index = if (topLevelInitLocals.first > 0) {
                    list.add(ListStatements.AddToList(MemoryLib.heap, "reserved".scratch)) //reserve for initLocals
                    entrypointCount + 1
                } else -1

                if (index != -1) {
                    list.add(CallFunction(
                        MemoryLib.alloc.precompiledCode, //alloc for initLocals
                        mutableListOf(topLevelInitLocals.first.toString().scratch, index.toString().scratch).also {
                            if (CompilationConstants.MARK_AND_SWEEP_GC) it.add(1, topLevelInitLocals.second.name.toString().scratch)
                        }
                    ))
                }

                list.add(CallFunction(
                    topLevelInit,
                    listOf(index.toString().scratch)
                ))
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