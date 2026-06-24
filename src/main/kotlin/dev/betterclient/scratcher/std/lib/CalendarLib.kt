package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.opcode.CalendarMenu
import dev.betterclient.scratcher.std.dsl.compile

object CalendarLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        fun gen(menu: CalendarMenu, name: String) {
            editor.compile(lib, name) {
                val returnArg = returnArg(Type.int)
                MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.CalendarData(menu)]
            }
        }

        val names = mutableMapOf(
            CalendarMenu.DAYOFWEEK to "getDayOfWeek",
            CalendarMenu.YEAR to "getYear",
            CalendarMenu.MONTH to "getMonth",
            CalendarMenu.DAY to "getDayOfMonth",
            CalendarMenu.HOUR to "getHour",
            CalendarMenu.MINUTE to "getMinute",
            CalendarMenu.SECOND to "getSecond",
            CalendarMenu.DAY to "getDay",
        )
        names.forEach { (menu, name) -> gen(menu, name) }
    }
}