package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

fun JSONArray.toObjectArray(): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    for (i in 0 until this.length()) {
        out += this.getJSONObject(i)
    }
    return out
}

fun Random.rand(): String {
    var out = ""
    val possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:',.<>/?"

    for (i in 0..CompilationConstants.NON_MINIFICATION_LENGTH) {
        out += possible.random(this)
    }

    return out
}

private var counter = 9999L
private val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

fun newMinification(): String {
    var temp = counter++
    val sb = StringBuilder()

    while (temp > 0) {
        temp--
        val rem = (temp % 52).toInt()
        sb.append(ALPHABET[rem])
        temp /= 52
    }

    return sb.reverse().toString()
}

inline fun obfuscate(nonObfuscatedName: String): String {
    return if (CompilationConstants.OBFUSCATION) {
        getUniqueName()
    } else nonObfuscatedName
}

inline fun getUniqueName(): String {
    return if (CompilationConstants.OBFUSCATION_MINIFICATION) {
        newMinification()
    } else {
        Random.rand()
    }
}

fun nextBlockPosition(): Int {
    return Random.nextInt(10000)
}

internal val Expression.simple: Boolean
    get() = when(this) {
        is BinaryExpression -> this.left.simple && this.right.simple
        is ConcatExpression -> this.left.simple && this.right.simple
        is UnaryExpression -> this.expression.simple
        is MemberExpression -> this.expression.simple
        is NonNullAssertExpression -> this.expression.simple
        is NonNullOrElseExpression -> this.operand1.simple && this.operand2.simple
        is SafeDotExpression -> this.target.simple

        is LocalVariableExpression -> true
        is ParameterExpression -> true
        is TemporaryLocalVariableIndexExpression -> true
        is TemporaryStackNameExpression -> true
        is TemporaryStackSizeExpression -> true
        is VariableExpression -> true
        is Literal -> true

        is TemporaryScratchExpr -> false
        is StatementExpression -> false
        is WhenExpression -> false
        is DynamicCallExpression -> false
        is CallExpression -> false
        is TemporaryHeapGetExpression -> false
        is LambdaExpression -> false
        is CheckSealedEnumTypeExpression -> this.expr.simple
        is SealedEnumCastExpression -> this.expr.simple
    }