package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.opcode.CalendarMenu
import dev.betterclient.scratcher.std.dsl.compileInline

object CalendarLib {
    fun init(lib: ASTFile) {
        fun gen(menu: CalendarMenu, name: String) {
            compileInline(
                library = lib,
                name = name,
                returnType = PrimitiveType.Integer
            ) { _ ->
                SensingExpressions.SenseExpression(
                    SensingExpressions.SensingData.CalendarData(menu)
                )
            }
        }

        val names = mutableMapOf(
            CalendarMenu.DAYOFWEEK to "getDayOfWeek",
            CalendarMenu.YEAR to "getYear",
            CalendarMenu.MONTH to "getMonth",
            CalendarMenu.DAY to "getDayOfMonth",
            CalendarMenu.HOUR to "getHour",
            CalendarMenu.MINUTE to "getMinute",
            CalendarMenu.SECOND to "getSecond"
        )
        names.forEach { (menu, name) -> gen(menu, name) }
    }
}