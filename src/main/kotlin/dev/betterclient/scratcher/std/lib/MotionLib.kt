package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.MotionExpressions
import dev.betterclient.scratcher.codegen.ast.MotionStatements
import dev.betterclient.scratcher.codegen.opcode.GotoMode
import dev.betterclient.scratcher.codegen.opcode.RotationStyle
import dev.betterclient.scratcher.std.dsl.compileInline

object MotionLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(lib, "getX", returnType = PrimitiveType.Float) {
            MotionExpressions.XPosition()
        }
        compileInline(lib, "getY", returnType = PrimitiveType.Float) {
            MotionExpressions.YPosition()
        }
        compileInline(lib, "getDirection", returnType = PrimitiveType.Float) {
            MotionExpressions.Direction()
        }

        compileGotoFunctions(lib)
        compileGlideToFunctions(lib)

        compileInline(lib, "turnLeft", parameters = mutableListOf(
            Parameter("degrees", PrimitiveType.Float)
        )) {
            MotionStatements.TurnLeft(it[0])
        }

        compileInline(lib, "turnRight", parameters = mutableListOf(
            Parameter("degrees", PrimitiveType.Float)
        )) {
            MotionStatements.TurnRight(it[0])
        }

        compileInline(lib, "setTurnStyleToLeftRight") {
            MotionStatements.SetRotationStyle(RotationStyle.LEFT_RIGHT)
        }
        compileInline(lib, "setTurnStyleToDontRotate") {
            MotionStatements.SetRotationStyle(RotationStyle.DONT_ROTATE)
        }
        compileInline(lib, "setTurnStyleToAllAround") {
            MotionStatements.SetRotationStyle(RotationStyle.ALL_AROUND)
        }
    }

    private fun compileGlideToFunctions(lib: ASTFile) {
        compileInline(lib, "glideToXY", warp = false, parameters = mutableListOf(
            Parameter("x", PrimitiveType.Float),
            Parameter("y", PrimitiveType.Float),
            Parameter("secs", PrimitiveType.Float)
        )) {
            MotionStatements.GlideTo(
                MotionStatements.GotoPosition.XY(it[1], it[2]),
                it[0]
            )
        }

        compileInline(lib, "glideToX", warp = false, parameters = mutableListOf(
            Parameter("x", PrimitiveType.Float),
            Parameter("secs", PrimitiveType.Float),
        )) {
            MotionStatements.GlideTo(
                MotionStatements.GotoPosition.X(it[1]),
                it[0]
            )
        }

        compileInline(lib, "glideToY", warp = false, parameters = mutableListOf(
            Parameter("y", PrimitiveType.Float),
            Parameter("secs", PrimitiveType.Float),
        )) {
            MotionStatements.GlideTo(
                MotionStatements.GotoPosition.Y(it[1]),
                it[0],
            )
        }

        compileInline(lib, "glideToMouse", warp = false, parameters = mutableListOf(
            Parameter("secs", PrimitiveType.Float),
        )) {
            MotionStatements.GlideTo(
                MotionStatements.GotoPosition.Mode(GotoMode.MOUSE),
                it[0]
            )
        }

        compileInline(lib, "glideToRandom", warp = false, parameters = mutableListOf(
            Parameter("secs", PrimitiveType.Float)
        )) {
            MotionStatements.GlideTo(
                MotionStatements.GotoPosition.Mode(GotoMode.RANDOM),
                it[0]
            )
        }
    }

    private fun compileGotoFunctions(lib: ASTFile) {
        compileInline(
            lib, "gotoXY", parameters = mutableListOf(
                Parameter("x", PrimitiveType.Float),
                Parameter("y", PrimitiveType.Float)
            )
        ) {
            MotionStatements.Goto(MotionStatements.GotoPosition.XY(it[0], it[1]))
        }

        compileInline(
            lib, "gotoX", parameters = mutableListOf(
                Parameter("x", PrimitiveType.Float),
            )
        ) {
            MotionStatements.Goto(MotionStatements.GotoPosition.X(it[0]))
        }

        compileInline(
            lib, "gotoY", parameters = mutableListOf(
                Parameter("y", PrimitiveType.Float)
            )
        ) {
            MotionStatements.Goto(MotionStatements.GotoPosition.Y(it[0]))
        }

        compileInline(lib, "gotoMouse") {
            MotionStatements.Goto(MotionStatements.GotoPosition.Mode(GotoMode.MOUSE))
        }

        compileInline(lib, "gotoRandom") {
            MotionStatements.Goto(MotionStatements.GotoPosition.Mode(GotoMode.RANDOM))
        }
    }
}