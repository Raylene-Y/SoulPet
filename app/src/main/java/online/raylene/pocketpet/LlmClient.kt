package online.raylene.pocketpet

import android.content.Context
import online.raylene.pocketpet.tools.PetTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI 兼容接口 + function calling 循环 */
object LlmClient {

    private val history = JSONArray()          // 原始 message JSON（含 tool_calls / tool 消息）
    private const val MAX_HISTORY = 30
    private const val MAX_TOOL_ROUNDS = 4

    var persona: Persona = Persona.HEALING

    fun chat(ctx: Context, userText: String,
             onTool: (String) -> Unit = {},
             onResult: (String) -> Unit) {
        Thread {
            val reply = try { agentLoop(ctx, userText, onTool) }
            catch (e: Exception) { "（网络出错了：${e.message}）" }
            onResult(reply)
        }.start()
    }

    @Synchronized
    private fun agentLoop(ctx: Context, userText: String, onTool: (String) -> Unit): String {
        history.put(JSONObject().put("role", "user").put("content", userText))
        trimHistory()

        repeat(MAX_TOOL_ROUNDS) {
            val resp = request()

            val msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val toolCalls = msg.optJSONArray("tool_calls")

            if (toolCalls == null || toolCalls.length() == 0) {
                val content = msg.optString("content", "（它没说话）")
                history.put(JSONObject().put("role", "assistant").put("content", content))
                return content
            }

            // 记录带 tool_calls 的 assistant 消息，然后逐个执行
            history.put(msg)
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                val name = fn.getString("name")
                val args = fn.optString("arguments", "{}")
                onTool(name)
                val result = PetTools.execute(ctx, name, args)
                history.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", tc.getString("id"))
                    .put("content", result))
            }
        }
        return "（工具调用太多轮了，先这样吧）"
    }

    private fun request(): JSONObject {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content",
            persona.prompt + "\n\n你可以使用提供的工具来帮主人做事（比如查电量、看时间、读剪贴板）。需要时直接调用，用完工具用你的人格口吻汇报结果。"))
        for (i in 0 until history.length()) messages.put(history.get(i))

        val tools = JSONArray()
        for (t in PetTools.ALL) tools.put(t.toSchema())

        val body = JSONObject()
            .put("model", BuildConfig.DEFAULT_MODEL)
            .put("messages", messages)
            .put("tools", tools)
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
        if (code !in 200..299) throw RuntimeException("API $code：${text.take(200)}")
        return JSONObject(text)
    }

    private fun trimHistory() {
        while (history.length() > MAX_HISTORY) history.remove(0)
        // 裁剪后开头不能是 tool 消息（孤儿 tool_call 会让 API 报错）
        while (history.length() > 0 &&
               history.getJSONObject(0).optString("role") == "tool") {
            history.remove(0)
        }
    }
}
