package dev.betterclient.scratcher.ast.parser

import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Type

object OperatorFunctions {
    fun check(function: Function) {
        val myEntry = OverloadableOperators.entries.find { it.funcName == function.name }
        if (myEntry == null) {
            throw GeneralCompilerException("\"overload\" function ${function.name} does not match any overload names, expected: ${
                OverloadableOperators.entries.joinToString("\n") { it.funcName }
            }")
        }

        if (!myEntry.signatureChecker(function.parameters.map { it.type }, function.returnType, function.isReceiver)) {
            throw GeneralCompilerException("\"overload\" function ${function.name} does not match the overload's expected parameters, Expected ${myEntry.expectedSignature}")
        }
    }

    val mathChecker: (args: List<Type>, returnType: Type, isReceiver: Boolean) -> Boolean = { args, returnType, receiver ->
        if (args.size != 2 || receiver) false
        else returnType != PrimitiveType.Void
    }

    enum class OverloadableOperators(
        val funcName: String,
        val signatureChecker: (args: List<Type>, returnType: Type, isReceiver: Boolean) -> Boolean,
        val expectedSignature: String
    ) {
        ADD("plus", mathChecker, "Any plus(Any, Any)"),
        SUB("minus", mathChecker, "Any minus(Any, Any)"),
        DIV("div", mathChecker, "Any div(Any, Any)"),
        MUL("times", mathChecker, "Any times(Any, Any)"),
        MOD("mod", mathChecker, "Any mod(Any, Any)"),

        INDEX_GET("get", { args, returnType, receiver ->
            if (!receiver) false
            else if (args.size != 2) false
            else returnType != PrimitiveType.Void
        }, "Any Any.get(Any index)"),
        INDEX_SET("set", { args, returnType, receiver ->
            if (!receiver) false
            else if (args.size != 3) false
            else returnType == PrimitiveType.Void
        }, "void Any.set(Any index, Any item)"),
    }
}