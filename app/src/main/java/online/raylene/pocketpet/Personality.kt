package online.raylene.pocketpet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 人格漂移：三层人格空间（按人格分档）
 *   每个人格有独立的 axes（毒舌/中二/活泼 0-100）和 traits（LLM 发明的开放特质）
 *   habits 全局共享（是"和主人的关系"，不随人格切换）
 * 文件结构：{"personas": {"毒舌": {"axes":{},"traits":{}}}, "habits": []}
 */
object Personality {
    private val AXES = listOf("毒舌", "中二", "活泼")
    private lateinit var file: File
    private var root = JSONObject()
    private var current = "默认"

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "pet/personality.json")
        root = load()
    }

    private fun defaultAxes() = JSONObject().put("毒舌", 50).put("中二", 50).put("活泼", 50)

    private fun load(): JSONObject {
        if (!file.exists()) return JSONObject()
        return try {
            val j = JSONObject(file.readText())
            // 旧版平铺结构迁移：整体变成"默认"人格的档案
            if (!j.has("personas")) {
                val migrated = JSONObject()
                migrated.put("personas", JSONObject().put("默认", JSONObject()
                    .put("axes", j.optJSONObject("axes") ?: defaultAxes())
                    .put("traits", j.optJSONObject("traits") ?: JSONObject())))
                migrated.put("habits", j.optJSONArray("habits") ?: JSONArray())
                migrated
            } else j
        } catch (e: Exception) { JSONObject() }
    }

    @Synchronized
    fun setPersona(label: String) {
        current = label
    }

    private fun profile(): JSONObject {
        val personas = root.optJSONObject("personas") ?: JSONObject().also { root.put("personas", it) }
        return personas.optJSONObject(current) ?: JSONObject()
            .put("axes", defaultAxes())
            .put("traits", JSONObject())
            .also { personas.put(current, it) }
    }

    private fun habits(): JSONArray =
        root.optJSONArray("habits") ?: JSONArray().also { root.put("habits", it) }

    @Synchronized
    private fun save() {
        file.writeText(root.toString(2))
    }

    /** 组装进 system prompt 的人格状态段 */
    @Synchronized
    fun renderForPrompt(): String {
        val p = profile()
        val sb = StringBuilder("【你当前人格「$current」的性格状态（随相处自然变化）】\n")
        val axes = p.getJSONObject("axes")
        for (a in AXES) sb.append("- $a：${axes.optInt(a, 50)}/100\n")
        val traits = p.getJSONObject("traits")
        if (traits.length() > 0) {
            sb.append("- 特质：")
            val parts = mutableListOf<String>()
            for (k in traits.keys()) parts.add("$k ${traits.getInt(k)}")
            sb.append(parts.joinToString("、") + "\n")
        }
        val h = habits()
        for (i in 0 until h.length()) sb.append("- 习性：${h.getString(i)}\n")
        return sb.toString()
    }

    /** UI 展示用一行 */
    @Synchronized
    fun renderForUI(): String {
        val p = profile()
        val axes = p.getJSONObject("axes")
        val traits = p.getJSONObject("traits")
        val top = traits.keys().asSequence().map { it to traits.getInt(it) }
            .sortedByDescending { it.second }.take(3)
            .joinToString(" ") { "${it.first}${it.second}" }
        return AXES.joinToString(" ") { "$it ${axes.optInt(it, 50)}" } +
            (if (top.isNotEmpty()) "｜$top" else "")
    }

    /** 会话级评估提示词：整段对话 → 总漂移增量 */
    fun driftSessionPrompt(transcript: String): String = """
你是人格评估器。这是宠物「$current」和主人的一段对话，评估整场下来性格该如何微调。
当前性格状态：
${renderForPrompt()}

对话：
$transcript

规则：
- axes 只能在 毒舌/中二/活泼 上微调，整段对话总量 -5~+5 的整数，大多数时候应为 0！性格是慢变量
- traits 可自由发明新维度（如好奇心、占有欲、洁癖），0-100，微调 -5~+5；只在有新观察时创建新维度
- 越接近极端（0或100），微调幅度应该越小
- habits 只沉淀明确的行为模式，最多 20 条；与现有重复就不要加
- 这段对话没有信息量就全给空
只输出 JSON，不要别的：{"axes":{},"traits":{},"habits_add":[],"habits_remove":[]}
""".trimIndent()

    /** 应用漂移增量 */
    @Synchronized
    fun applyDelta(delta: JSONObject) {
        val p = profile()
        val axes = p.getJSONObject("axes")
        delta.optJSONObject("axes")?.let { d ->
            for (k in d.keys()) if (k in AXES) {
                val dd = d.optInt(k, 0).coerceIn(-5, 5)   // 会话级：总量钳制
                axes.put(k, (axes.optInt(k, 50) + dd).coerceIn(0, 100))
            }
        }
        val traits = p.getJSONObject("traits")
        delta.optJSONObject("traits")?.let { d ->
            for (k in d.keys()) {
                val dd = d.optInt(k, 0).coerceIn(-5, 5)   // 钳制增量
                val v = (traits.optInt(k, 50) + dd).coerceIn(0, 100)
                if (v <= 5) traits.remove(k) else traits.put(k, v)  // 趋零消亡
            }
        }
        val h = habits()
        delta.optJSONArray("habits_add")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (h.length() < 20) h.put(arr.getString(i))
            }
        }
        delta.optJSONArray("habits_remove")?.let { arr ->
            val remove = (0 until arr.length()).map { arr.getString(it) }.toSet()
            val kept = JSONArray()
            for (i in 0 until h.length()) {
                val v = h.getString(i)
                if (v !in remove) kept.put(v)
            }
            root.put("habits", kept)
        }
        save()
    }
}
