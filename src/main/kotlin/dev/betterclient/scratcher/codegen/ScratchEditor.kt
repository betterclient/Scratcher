package dev.betterclient.scratcher.codegen

import dev.betterclient.scratcher.codegen.ast.ScratchASTEventListener
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.compile
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.wrapper.ScratchOpcode
import dev.betterclient.scratcher.codegen.opcode.EventListenerFunction
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import org.json.JSONArray
import java.io.File

@JvmInline
value class ScratchEditor(private val editor: JSONEditor) {
    fun addList(list: ScratchList) {
        val obj = editor.workerSprite.getJSONObject("lists")
        obj.put(list.id, JSONArray(
            listOf(list.name, JSONArray(listOf<Any?>()))
        ))
    }

    fun addVariable(variable: ScratchVariable) {
        val obj = editor.workerSprite.getJSONObject("variables")
        obj.put(variable.id, JSONArray(
            listOf(variable.name, 0)
        ))
    }

    private fun addFunction(func: ScratchFunction) {
        func.parent.next?.parent = func.parent

        editor.workerSprite.getJSONObject("blocks").apply {
            fun ScratchOpcode.put() { put(this.id, this.toJson()) }
            func.parent.put()
            func.parent.prototype.put()
            func.parent.prototype.alsoAdd.forEach { it.put() }
            fun put(start: ScratchOpcode?) {
                var next = start
                while (next != null) {
                    next.put()
                    next.alsoAdd.forEach { put(it) }
                    next = next.next
                }
            }

            put(func.first)
        }
    }

    private fun addEventListener(listener: EventListenerFunction) {
        listener.parent.next?.parent = listener.parent

        editor.workerSprite.getJSONObject("blocks").apply {
            fun ScratchOpcode.put() { put(this.id, this.toJson()) }
            listener.parent.put()
            fun put(start: ScratchOpcode?) {
                var next = start
                while (next != null) {
                    next.put()
                    next.alsoAdd.forEach { put(it) }
                    next = next.next
                }
            }

            put(listener.first)
        }
    }

    fun addFunction(func: ScratchASTFunction) {
        func.internal.first = compile(func.code)
        func.internal.parent.next = func.internal.first
        addFunction(func.internal)
    }

    fun addEventListener(listener: ScratchASTEventListener) {
        addEventListener(listener.internal)
    }

    fun writeTo(file: File) {
        editor.writeTo(file)
    }
}