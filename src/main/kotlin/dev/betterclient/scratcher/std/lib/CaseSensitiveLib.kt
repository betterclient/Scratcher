package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ScratchSprite
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.not
import dev.betterclient.scratcher.std.dsl.sc

object CaseSensitiveLib {
    val alphabet = ScratchList(obfuscate("Case sensitive: Alphabet")).also { list ->
        list.items.addAll(
            (0x41..0x5A).map { String(Character.toChars(it)) }
        )
    }
    val spriteName = alphabet.items.joinToString("")

    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.addSprite(ScratchSprite(spriteName)) //just used for the check
        editor.addList(alphabet)

        editor.compile(lib, "isUppercase") {
            val letter = arg("letter", PrimitiveType.Char)
            val out = returnArg(PrimitiveType.Bool)

            val index = variable("isUppercase::index")
            val temp = variable("isUppercase::temp")
            val temp2 = variable("isUppercase::temp2")

            index.set(alphabet.indexOf(letter))
            temp2.set(alphabet[index])
            alphabet[index] = letter
            temp.set(alphabet.asString())
            alphabet[index] = temp2
            MemoryLib.heap[out] = ((sensing[SensingExpressions.SensingData.XPositionOf(temp.lower())]) equals 0.sc).not()
        }
    }
}