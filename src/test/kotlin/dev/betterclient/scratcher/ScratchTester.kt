package dev.betterclient.scratcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.assertEquals

data class VmOutput(
    val variables: Map<String, Any>,
    val lists: Map<String, List<Any>>,
    val logs: List<String>,
    val questions: List<String>
)

class ScratchTestScope {
    internal val asks = mutableListOf<Pair<String, String>>()
    internal val expectedSays = mutableListOf<String>()

    fun assertAsk(question: String, answer: String) {
        asks.add(question to answer)
    }

    fun assertSay(text: String) {
        expectedSays.add(text)
    }

    internal fun verify(output: VmOutput) {
        if (asks.isNotEmpty()) {
            val expectedQuestions = asks.map { it.first }
            assertEquals(expectedQuestions, output.questions, "Questions asked do not match")
        }
        if (expectedSays.isNotEmpty()) {
            assertEquals(expectedSays, output.logs, "Say logs do not match")
        }
    }
}

private val nodeBinary: String = System.getProperty("node.executable")
    ?: error("executable property not set")
private val runnerScript = File("test/run.mjs")

fun run(
    sb3: File,
    timeoutMs: Long = 1000,
    expects: ScratchTestScope.() -> Unit = {}
): VmOutput {
    val scope = ScratchTestScope().apply(expects)

    val process = ProcessBuilder(
        nodeBinary,
        runnerScript.absolutePath,
        sb3.absolutePath,
        timeoutMs.toString()
    )
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val configJson = JSONObject().apply {
        val asksArray = JSONArray()
        for ((question, answer) in scope.asks) {
            asksArray.put(JSONObject().apply {
                put("question", question)
                put("answer", answer)
            })
        }
        put("asks", asksArray)
    }

    process.outputStream.bufferedWriter().use { writer ->
        writer.write(configJson.toString())
        writer.flush()
    }

    val stdout = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    assertEquals(0, exitCode, "Node script failed to run .sb3 file")

    val json = stdout.trim().lines().last().let {
        runCatching { JSONObject(it) }.getOrElse { _ ->
            println("Failed to parse json: \n$it")
            throw UnsupportedOperationException()
        }
    }
    val varsJson = json.getJSONObject("variables")
    val listsJson = json.getJSONObject("lists")
    val logsJson = json.getJSONArray("logs")
    val questionsJson = json.optJSONArray("questions") ?: JSONArray()

    val variables = varsJson.keySet().associateWith { varsJson.get(it) }
    val lists = listsJson.keySet().associateWith { key ->
        val arr = listsJson.getJSONArray(key)
        (0 until arr.length()).map { i -> arr.get(i) }
    }
    val logs = (0 until logsJson.length()).map { logsJson.getString(it) }
    val questions = (0 until questionsJson.length()).map { questionsJson.getString(it) }

    val output = VmOutput(variables, lists, logs, questions)

    scope.verify(output)
    return output
}