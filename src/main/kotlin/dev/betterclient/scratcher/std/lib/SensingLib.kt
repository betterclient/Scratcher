package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.SensingBoolExpressions
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.ast.SensingStatements
import dev.betterclient.scratcher.std.dsl.DSLBoolFromCreator
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline

object SensingLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        compileInline(
            library = lib,
            name = "ask",
            parameters = mutableListOf(Parameter("message", Type.str)),
            returnType = Type.str,
            prepend = { args ->
                listOf(SensingStatements.Ask(args[0]))
            },
            useLocal = true //if this was false, it would break if someFunc(ask("hi"), ask("other")), the answers would be the same
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.Answer)
        }

        compileInline(
            library = lib,
            name = "getDistanceToMouse",
            returnType = Type.str
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.DistanceToMouse)
        }

        compileInline(
            library = lib,
            name = "getMouseX",
            returnType = Type.int
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.MouseX)
        }

        compileInline(
            library = lib,
            name = "getMouseY",
            returnType = Type.int
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.MouseY)
        }

        compileInline(
            library = lib,
            name = "getTimer",
            returnType = Type.float
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.Timer)
        }

        compileInline(
            library = lib,
            name = "resetTimer",
            returnType = Type.void
        ) { _ ->
            SensingStatements.ResetTimer()
        }

        compileInline(
            library = lib,
            name = "getUsername",
            returnType = Type.str
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.Username)
        }

        compileInline(
            library = lib,
            name = "getDaysSince2000",
            returnType = Type.float
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.DaysSince2000)
        }

        compileInline(
            library = lib,
            name = "isOnline",
            returnType = Type.bool
        ) { _ ->
            SensingBoolExpressions.IsOnlineExpression()
        }

        compileInline(
            library = lib,
            name = "isMousePressed",
            returnType = Type.bool
        ) { _ ->
            SensingBoolExpressions.MousePressedExpression()
        }

        compileInline(
            library = lib,
            name = "isKeyPressed",
            parameters = mutableListOf(Parameter("key", Type.str)),
            returnType = Type.bool
        ) { args ->
            SensingBoolExpressions.KeyPressedExpression(args[0])
        }
    }
}