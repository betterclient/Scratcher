package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.SensingBoolExpressions
import dev.betterclient.scratcher.codegen.ast.SensingExpressions
import dev.betterclient.scratcher.codegen.ast.SensingStatements
import dev.betterclient.scratcher.std.dsl.DSLBoolFromCreator
import dev.betterclient.scratcher.std.dsl.compile

object SensingLib {
    fun init(lib: ASTFile, editor: ScratchEditor) {
        editor.compile(lib, "ask") {
            val message = arg("message", Type.str)
            val returnArg = returnArg(Type.str)
            sensing.ask(message)
            MemoryLib.heap[returnArg] = sensing.answer
        }

        editor.compile(lib, "getDistanceToMouse") {
            val returnArg = returnArg(Type.str)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.DistanceToMouse]
        }

        editor.compile(lib, "getMouseX") {
            val returnArg = returnArg(Type.int)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.MouseX]
        }

        editor.compile(lib, "getMouseY") {
            val returnArg = returnArg(Type.int)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.MouseY]
        }

        editor.compile(lib, "getTimer") {
            val returnArg = returnArg(Type.float)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.Timer]
        }

        editor.compile(lib, "resetTimer") {
            addStatement(SensingStatements.ResetTimer())
        }

        editor.compile(lib, "getUsername") {
            val returnArg = returnArg(Type.str)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.Username]
        }

        editor.compile(lib, "getDaysSince2000") {
            val returnArg = returnArg(Type.float)
            MemoryLib.heap[returnArg] = sensing[SensingExpressions.SensingData.DaysSince2000]
        }

        editor.compile(lib, "isOnline") {
            val returnArg = returnArg(Type.bool)
            MemoryLib.heap[returnArg] = DSLBoolFromCreator {
                SensingBoolExpressions.IsOnlineExpression()
            }
        }

        editor.compile(lib, "isMousePressed") {
            val returnArg = returnArg(Type.bool)
            MemoryLib.heap[returnArg] = DSLBoolFromCreator {
                SensingBoolExpressions.MousePressedExpression()
            }
        }

        editor.compile(lib, "isKeyPressed") {
            val key = arg("key", Type.str)
            val returnArg = returnArg(Type.bool)
            MemoryLib.heap[returnArg] = DSLBoolFromCreator {
                SensingBoolExpressions.KeyPressedExpression(key.lower())
            }
        }
    }
}