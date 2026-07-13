package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.codegen.ast.SensingBoolExpressions
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.ast.SensingStatements
import dev.betterclient.scratcher.std.dsl.compileInline

object SensingLib {
    fun init(lib: ASTFile) {
        compileInline(
            library = lib,
            name = "ask",
            parameters = mutableListOf(Parameter("message", PrimitiveType.Str)),
            returnType = PrimitiveType.Str,
            warp = false,
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
            returnType = PrimitiveType.Str
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.DistanceToMouse)
        }

        compileInline(
            library = lib,
            name = "getMouseX",
            returnType = PrimitiveType.Integer
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.MouseX)
        }

        compileInline(
            library = lib,
            name = "getMouseY",
            returnType = PrimitiveType.Integer
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.MouseY)
        }

        compileInline(
            library = lib,
            name = "getTimer",
            returnType = PrimitiveType.Float
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.Timer)
        }

        compileInline(
            library = lib,
            name = "resetTimer",
            returnType = PrimitiveType.Void
        ) { _ ->
            SensingStatements.ResetTimer()
        }

        compileInline(
            library = lib,
            name = "getUsername",
            returnType = PrimitiveType.Str
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.Username)
        }

        compileInline(
            library = lib,
            name = "getDaysSince2000",
            returnType = PrimitiveType.Float
        ) { _ ->
            SensingExpressions.SenseExpression(SensingExpressions.SensingData.DaysSince2000)
        }

        compileInline(
            library = lib,
            name = "isOnline",
            returnType = PrimitiveType.Bool
        ) { _ ->
            SensingBoolExpressions.IsOnlineExpression()
        }

        compileInline(
            library = lib,
            name = "isMousePressed",
            returnType = PrimitiveType.Bool
        ) { _ ->
            SensingBoolExpressions.MousePressedExpression()
        }

        compileInline(
            library = lib,
            name = "isKeyPressed",
            parameters = mutableListOf(Parameter("key", PrimitiveType.Str)),
            returnType = PrimitiveType.Bool
        ) { args ->
            SensingBoolExpressions.KeyPressedExpression(args[0])
        }
    }
}