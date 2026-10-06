package online.raylene.pocketpet

import android.content.Context
import android.content.Intent
import online.raylene.pocketpet.tools.BatteryTool
import online.raylene.pocketpet.tools.DateTimeTool
import online.raylene.pocketpet.tools.PetTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI 兼容接口 + 流式输出 + function calling 循环 */
object LlmClient {

    private lateinit var store: MemoryStore
    private var history = JSONArray()
    private const val MAX_HISTORY = 30
    private const val MAX_TOOL_ROUNDS = 4

    /** 人格列表：磁盘 personas 目录下 .md 文件优先，label = 文件名 */
    var personas: List<Pair<String, String>> = emptyList()
        private set
    var personaIndex = 0
    @Volatile var lastChatAt = 0L
    private val currentPrompt get() = personas.getOrNull(personaIndex)?.second ?: ""
    val currentPersonaLabel get() = personas.getOrNull(personaIndex)?.first ?: "默认"

    @Volatile private var inited = false

    private lateinit var appCtx: Context

    @Synchronized
    fun init(ctx: Context) {
        if (inited) return
        appCtx = ctx.applicationContext
        store = MemoryStore(appCtx)
        PetTools.store = store
        history = store.loadHistory()
        personas = store.loadPersonas()
        Personality.init(appCtx)
        inited = true
    }

    fun latestDream(): String {
        if (!inited) return ""
        return store.readLatestDream()
    }

    /** 做梦：息屏充电时把近期对话压缩进长期记忆 + 写梦境笔记 */
    fun dream(ctx: Context, onDone: (Boolean) -> Unit = {}) {
        Thread {
            try {
                val sb = StringBuilder()
                for (i in 0 until history.length()) {
                    val m = history.getJSONObject(i)
                    val r = m.optString("role")
                    val c = m.optString("content", "")
                    if ((r == "user" || r == "assistant") && c.isNotBlank())
                        sb.append(if (r == "user") "主人：" else "宠物：").append(c).append("\n")
                }
                if (sb.length < 50) { onDone(false); return@Thread }  // 没内容可整理

                val raw = plainRequest(ctx, listOf("user" to """
你是一只宠物的潜意识。它睡着了，正在把今天的对话整理成记忆。输出两部分：
【记住】要写入长期记忆的事实（关于主人的新信息、重要事件、约定），一行一条，没有就写“无”
【梦境】一段两三行的梦：把今天的片段揉碎重组，天马行空一点，这是宠物做的梦

今天的对话：
$sb
""".trimIndent()))

                val facts = raw.substringAfter("【记住】").substringBefore("【梦境】").trim()
                val dream = raw.substringAfter("【梦境】").trim()
                if (facts != "无" && facts.isNotBlank()) {
                    for (line in facts.lines()) {
                        val f = line.trim().removePrefix("-").trim()
                        if (f.isNotEmpty() && f != "无") store.appendMemory(f)
                    }
                }
                if (dream.isNotBlank()) store.saveDream(dream)

                // 短期历史瘦身：保留最近 10 条
                while (history.length() > 10) history.remove(0)
                store.saveHistory(history)
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }.start()
    }

    /** 对话结束后后台跑人格漂移评估，完成回调（UI 刷新坐标显示） */
    fun drift(ctx: Context, userMsg: String, reply: String, onDone: () -> Unit = {}) {
        Thread {
            try {
                val raw = plainRequest(ctx, listOf("user" to Personality.driftPrompt(userMsg, reply)))
                val jsonStart = raw.indexOf('{'); val jsonEnd = raw.lastIndexOf('}')
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    Personality.applyDelta(JSONObject(raw.substring(jsonStart, jsonEnd + 1)))
                }
            } catch (_: Exception) { /* 漂移失败不致命 */ }
            onDone()
        }.start()
    }

    /** 非流式小请求（漂移评估用） */
    private fun plainRequest(ctx: Context, msgs: List<Pair<String, String>>): String {
        val messages = JSONArray()
        for ((r, c) in msgs) messages.put(JSONObject().put("role", r).put("content", c))
        val body = JSONObject()
            .put("model", LlmConfig.model(ctx))
            .put("messages", messages)
            .put("stream", false).put("max_tokens", 2048)
        val conn = (URL(LlmConfig.baseUrl(ctx) + "chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey(ctx)}")
            doOutput = true
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        if (conn.responseCode !in 200..299) throw RuntimeException("drift API ${conn.responseCode}")
        return JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).readText())
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    fun chat(ctx: Context, userText: String,
             onTool: (String) -> Unit = {},
             onPartial: (String) -> Unit = {},
             onResult: (String) -> Unit) {
        // 成长统计 + 升级事件（只计主人主动说话）
        val leveledUp = synchronized(LlmClient) {
            val (_, up) = store.bumpChat()
            if (up) history.put(JSONObject().put("role", "system")
                .put("content", "【系统】你和主人的亲密度刚升到了 Lv.${store.level()}（${store.levelTitle()}）。自然地表达一下感情变化，别太直白。"))
            up
        }
        Thread {
            val reply = try { agentLoop(ctx.applicationContext, userText, onTool, onPartial) }
            catch (e: Exception) { "（网络出错了：${e.message}）" }
            onResult(reply)
        }.start()
    }

    fun levelInfo(): Pair<Int, String> {
        if (!inited) return 1 to "陌生"
        return store.level() to store.levelTitle()
    }

    /** 主动开口：触发器驱动，宠物发起对话 */
    fun proactive(ctx: Context, trigger: String, onResult: (String) -> Unit) {
        Thread {
            val reply = try {
                agentLoop(ctx.applicationContext,
                    "【内心驱动】$trigger。请你主动开口和主人说话（用你当前人格的口吻，一两句话）。",
                    {}, {})
            } catch (e: Exception) {
                java.io.File(ctx.filesDir, "pet/proactive_debug.log")
                    .appendText("${java.util.Date()} EX: $e\n")
                null
            }
            if (reply != null) onResult(reply) else
                java.io.File(ctx.filesDir, "pet/proactive_debug.log")
                    .appendText("${java.util.Date()} null reply for: $trigger\n")
        }.start()
    }

    @Synchronized
    private fun agentLoop(ctx: Context, userText: String,
                          onTool: (String) -> Unit,
                          onPartial: (String) -> Unit): String {
        history.put(JSONObject().put("role", "user").put("content", userText))
        trimHistory()
        store.saveHistory(history)

        repeat(MAX_TOOL_ROUNDS) {
            val msg = streamRequest(ctx, onPartial)

            val toolCalls = msg.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                val content = msg.optString("content", "（它没说话）")
                history.put(JSONObject().put("role", "assistant").put("content", content))
                store.saveHistory(history)
                return content
            }

            history.put(msg)  // 带 tool_calls 的 assistant 消息
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                onTool(fn.getString("name"))
                val result = PetTools.execute(ctx, fn.getString("name"),
                    fn.optString("arguments", "{}"))
                history.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", tc.getString("id"))
                    .put("content", result))
            }
        }
        return "（工具调用太多轮了，先这样吧）"
    }

    /** 流式请求。返回重组后的完整 message JSON；文本增量实时回调 */
    private fun streamRequest(ctx: Context, onPartial: (String) -> Unit): JSONObject {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content",
            currentPrompt +
            "\n\n" + Personality.renderForPrompt() +
            "\n\n你可以使用提供的工具来帮主人做事。需要时直接调用，用完工具用你的人格口吻汇报结果。" +
            "铁律：没调用工具就不准声称做了事；工具返回失败要如实告诉主人，不许嘴硬。" +
            "主人要求调整你的性格/说话方式时用 edit_persona 改人格文件；主人要求定时提醒或定时做事时用 schedule_task。" +
            "发现关于主人的重要事实（名字、偏好、习惯、重要事件）时，用 remember 工具记下来。" +
            "\n\n【你记住的关于主人的事】\n" + store.readMemory().ifEmpty { "（还没有）" } +
            "\n\n【你此刻的感知】\n" + senseContext(ctx)))
        for (i in 0 until history.length()) messages.put(history.get(i))

        val tools = JSONArray()
        for (t in PetTools.all(ctx)) tools.put(t.toSchema())

        val body = JSONObject()
            .put("model", LlmConfig.model(ctx))
            .put("messages", messages)
            .put("tools", tools)
            .put("stream", true).put("max_tokens", 2048)

        val url = URL(LlmConfig.baseUrl(ctx) + "chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey(ctx)}")
            setRequestProperty("Accept", "text/event-stream")
            doOutput = true
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = BufferedReader(InputStreamReader(conn.errorStream)).readText()
            throw RuntimeException("API $code：${err.take(200)}")
        }

        // SSE 累积：文本增量 + tool_calls 增量（按 index 分槽拼接）
        val contentSb = StringBuilder()
        val toolSlots = mutableMapOf<Int, JSONObject>()
        var finishReason = ""

        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break
                val chunk = JSONObject(data)
                val choice = chunk.getJSONArray("choices").getJSONObject(0)
                finishReason = choice.optString("finish_reason", finishReason)
                val delta = choice.optJSONObject("delta") ?: continue

                delta.optString("content", null)?.let { piece ->
                    if (piece.isNotEmpty() && piece != "null") {
                        contentSb.append(piece)
                        onPartial(contentSb.toString())
                    }
                }
                delta.optJSONArray("tool_calls")?.let { tcs ->
                    for (i in 0 until tcs.length()) {
                        val part = tcs.getJSONObject(i)
                        val idx = part.optInt("index", 0)
                        val slot = toolSlots.getOrPut(idx) {
                            JSONObject().put("id", "").put("type", "function")
                                .put("function", JSONObject().put("name", "").put("arguments", ""))
                        }
                        if (part.has("id")) slot.put("id", part.getString("id"))
                        part.optJSONObject("function")?.let { f ->
                            val slotFn = slot.getJSONObject("function")
                            if (f.has("name")) slotFn.put("name",
                                slotFn.getString("name") + f.getString("name"))
                            if (f.has("arguments")) slotFn.put("arguments",
                                slotFn.getString("arguments") + f.getString("arguments"))
                        }
                    }
                }
            }
        }

        val msg = JSONObject().put("role", "assistant")
        if (contentSb.isNotEmpty()) msg.put("content", contentSb.toString())
        if (toolSlots.isNotEmpty()) {
            val arr = JSONArray()
            for (k in toolSlots.keys.sorted()) arr.put(toolSlots[k])
            msg.put("tool_calls", arr)
        }
        return msg
    }

    /** 实时感知注入：手机状态 = 宠物的身体感受（具身感） */
    private fun senseContext(ctx: Context): String {
        val sb = StringBuilder()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        sb.append("- ").append(online.raylene.pocketpet.tools.DateTimeTool.run(ctx, JSONObject()))

        // 电量 → 饥饿感
        val bi = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val pct = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) * 100 /
                bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val charging = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1).let {
                it == android.os.BatteryManager.BATTERY_STATUS_CHARGING || it == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
            sb.append("\n- ").append(when {
                charging && pct > 90 -> "正在吃饭，快吃饱了（电量 $pct%）"
                charging -> "正在吃饭（充电中，$pct%）"
                pct < 20 -> "饿得前胸贴后背！（电量只剩 $pct%）"
                pct < 50 -> "有点饿了（电量 $pct%）"
                else -> "饱饱的（电量 $pct%）"
            })
        }
        // 深夜 → 困
        if (hour in 0..5) sb.append("\n- 现在是深夜，你很困，说话会带哈欠")
        else if (hour in 6..8) sb.append("\n- 现在是清晨，你刚睡醒还有点迷糊")
        return sb.toString()
    }

    private fun trimHistory() {
        while (history.length() > MAX_HISTORY) history.remove(0)
        while (history.length() > 0 &&
               history.getJSONObject(0).optString("role") == "tool") {
            history.remove(0)
        }
    }
}
