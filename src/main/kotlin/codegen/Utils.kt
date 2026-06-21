package dev.betterclient.codegen

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

    for (i in 0..20) {
        out += possible.random(this)
    }

    return out
}

fun rand(): String {
    return Random.rand()
}