package online.raylene.pocketpet

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI 兼容接口客户端，zai/DeepSeek/OpenAI 通用 */
object LlmClient {

    data class Msg(val role: String, val content: String)

    // 会话记忆（MVP：内存态，进程活多久记多久）
    private val history = mutableListOf<Msg>()
    private const val MAX_HISTORY = 20

    var persona: Persona = Persona.HEALING

    @Synchronized
    fun chat(userText: String, onResult: (String) -> Unit) {
        Thread {
            val reply = try {
                call(userText)
            } catch (e: Exception) {
                "（网络出错了：${e.message}）"
            }
            onResult(reply)
        }.start()
    }

    @Synchronized
    private fun call(userText: String): String {
        history.add(Msg("user", userText))
        while (history.size > MAX_HISTORY) history.removeAt(0)

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", persona.prompt))
        for (m in history) {
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }

        val body = JSONObject()
            .put("model", BuildConfig.DEFAULT_MODEL)
            .put("messages", messages)
            .put("stream", false)

        val url = URL(BuildConfig.DEFAULT_BASE_URL + "chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.DEFAULT_API_KEY}")
            doOutput = true
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).readText()
        if (code !in 200..299) return "（API $code：${text.take(200)}）"

        val reply = JSONObject(text)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")

        history.add(Msg("assistant", reply))
        return reply
    }
}
