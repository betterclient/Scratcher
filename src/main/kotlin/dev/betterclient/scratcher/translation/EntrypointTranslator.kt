package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.*
import dev.betterclient.scratcher.codegen.opcode.EventListener
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.gc.GCInfo
import dev.betterclient.scratcher.gc.GCLib
import dev.betterclient.scratcher.gc.name
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.MemoryLib

class EntrypointTranslator(
    val getFunctionLocalSize: (Function) -> Pair<Int, GCInfo>,
    val toScratch: (Function) -> ScratchASTFunction,
    val toScratchTL: (TLVariable) -> ScratchVariable,
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
        val alloc = if (localSize == 0) { listOf() } else if (CompilationConstants.MARK_AND_SWEEP_GC) {
            listOf(
                CallFunction(
                    MemoryLib.alloc.precompiledCode,
                    listOf(localSize.toString().scratch, gc.name.toString().scratch, reservedIndex.toString().scratch) //allocate slots for the entrypoint
                ),
                ListStatements.AddToList( //tell the garbage collector about us
                    GCLib.rootsList,
                    ListExpressions.ItemAtIndex(MemoryLib.heap, reservedIndex.toString().scratch)
                )
            )
        } else {
            listOf(
                CallFunction(
                    MemoryLib.alloc.precompiledCode,
                    listOf(localSize.toString().scratch, reservedIndex.toString().scratch) //allocate slots for the entrypoint
                )
            )
        }

        val index = if (localSize == 0) "-1".scratch else ListExpressions.ItemAtIndex(MemoryLib.heap, reservedIndex.toString().scratch)

        val call = if (listener.event is EventListener.ProcedureCall) {
            CallFunction(
                toScratch(func), listOf(index) //call the entrypoint
                     + listener.event.func.parent.prototype.arguments.map { LiteralExpression(it.asValue!!) } //with our args
            )
        } else {
            CallFunction(
                toScratch(func), listOf(index) //call the entrypoint
            )
        }

        val wait = if (listener.event is EventListener.ProcedureCall) listOf() else {
            listOf(
                ControlStatements.Wait("0".scratch), //give time to the reset entrypoint so it can allocate slots for us (idk if this is actually needed)
            )
        }

        editor.addEventListener(ScratchASTEventListener(
            event = listener.event,
            code = wait + alloc + call
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
                    list.add(ListStatements.ClearList(GCLib.markedList))
                }
                repeat(entrypointCount) {
                    list.add(ListStatements.AddToList(MemoryLib.heap, "reserved".scratch))
                    if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        list.add(ListStatements.AddToList(GCLib.markedList, "0".scratch))
                    }
                }
                val index = if (topLevelInitLocals.first > 0) {
                    list.add(ListStatements.AddToList(MemoryLib.heap, "reserved".scratch)) //reserve for initLocals
                    if (CompilationConstants.MARK_AND_SWEEP_GC) {
                        list.add(ListStatements.AddToList(GCLib.markedList, "0".scratch))
                    }
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
                    listOf(
                        if (index == -1) {
                            "-1".scratch
                        } else {
                            ListExpressions.ItemAtIndex(MemoryLib.heap, index.toString().scratch)
                        }
                    )
                ))

                if (CompilationConstants.MARK_AND_SWEEP_GC) {
                    list.add(
                        VariableStatements.SetVariableTo(
                            variable = toScratchTL(StandardLibASTGenerator.gc.variables.find { it.name == "gc_reserved" }!!),
                            value = (entrypointCount + 1).toString().scratch
                        )
                    )
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