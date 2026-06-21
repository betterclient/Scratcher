package dev.betterclient.codegen

import dev.betterclient.codegen.ast.ScratchFunction
import dev.betterclient.codegen.opcode.ScratchList
import dev.betterclient.codegen.ast.ScratchOpcode
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
                println("Traversing: ${opcode.opcode} (ID: ${opcode.id}) | alsoAdd size: ${opcode.alsoAdd.size}")
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
                    println("Traversing: ${next.opcode} (ID: ${next.id}) | alsoAdd size: ${next.alsoAdd.size}")
                    next.put()
                    next.alsoAdd.forEach { put(it) }
                    next = next.next
                }
            }

            put(func.first)
        }
    }

    fun writeTo(file: File) {
        editor.writeTo(file)
    }
}