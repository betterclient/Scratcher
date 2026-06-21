package dev.betterclient.codegen

import dev.betterclient.codegen.ast.ScratchFunction
import dev.betterclient.codegen.opcode.ScratchList
import dev.betterclient.codegen.ast.ScratchOpcode
import dev.betterclient.codegen.opcode.EventListenerFunction
import dev.betterclient.codegen.opcode.ScratchVariable
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

    fun addFunction(func: ScratchFunction) {
        //automatic parenting
        run {
            var opcode = func.parent.next
            while (opcode != null) {
                opcode.parent = func.parent
                opcode = opcode.next
            }
        }

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

    fun addEventListener(listener: EventListenerFunction) {
        //automatic parenting
        run {
            var opcode = listener.parent.next
            while (opcode != null) {
                opcode.parent = listener.parent
                opcode = opcode.next
            }
        }

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

    fun writeTo(file: File) {
        editor.writeTo(file)
    }
}