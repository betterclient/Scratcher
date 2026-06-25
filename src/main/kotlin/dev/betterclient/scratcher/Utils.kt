package dev.betterclient.scratcher

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

private var counter = 1L
private val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

fun newMinification(): String {
    var temp = counter++
    val sb = StringBuilder()

    while (temp > 0) {
        temp-- // Adjust for 1-based indexing
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
    return Random.nextInt(2000)
}