package online.raylene.pocketpet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 人格漂移：三层人格空间
 *   axes   固定核心轴（可可视化）：毒舌/中二/活泼 0-100
 *   traits 开放特质（LLM 自由发明）：好奇心、八卦、洁癖……
 *   habits 习性文本（自然语言沉淀，直接进人格）
 * 每次对话后由评估器微调，"性格是养出来的"。
 */
object Personality {
    private val AXES = listOf("毒舌", "中二", "活泼")
    private lateinit var file: File
    private var data = JSONObject()

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "pet/personality.json")
        data = load()
    }

    private fun load(): JSONObject {
        if (!file.exists()) return JSONObject()
            .put("axes", JSONObject().put("毒舌", 50).put("中二", 50).put("活泼", 50))
            .put("traits", JSONObject())
            .put("habits", JSONArray())
        return try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject()
            .put("axes", JSONObject().put("毒舌", 50).put("中二", 50).put("活泼", 50))
            .put("traits", JSONObject()).put("habits", JSONArray()) }
    }

    @Synchronized
    private fun save() {
        file.writeText(data.toString(2))
    }

    /** 组装进 system prompt 的人格状态段 */
    @Synchronized
    fun renderForPrompt(): String {
        val sb = StringBuilder("【你当前的性格状态（随相处自然变化）】\n")
        val axes = data.getJSONObject("axes")
        for (a in AXES) sb.append("- $a：${axes.optInt(a, 50)}/100\n")
        val traits = data.getJSONObject("traits")
        if (traits.length() > 0) {
            sb.append("- 特质：")
            val parts = mutableListOf<String>()
            for (k in traits.keys()) parts.add("$k ${traits.getInt(k)}")
            sb.append(parts.joinToString("、") + "\n")
        }
        val habits = data.getJSONArray("habits")
        for (i in 0 until habits.length()) sb.append("- 习性：${habits.getString(i)}\n")
        return sb.toString()
    }

    /** UI 展示用一行 */
    @Synchronized
    fun renderForUI(): String {
        val axes = data.getJSONObject("axes")
        val traits = data.getJSONObject("traits")
        val top = traits.keys().asSequence().map { it to traits.getInt(it) }
            .sortedByDescending { it.second }.take(3)
            .joinToString(" ") { "${it.first}${it.second}" }
        return AXES.joinToString(" ") { "$it ${axes.optInt(it, 50)}" } +
            (if (top.isNotEmpty()) "｜$top" else "")
    }

    /** 评估器提示词：让 LLM 输出漂移增量 JSON */
    fun driftPrompt(userMsg: String, petReply: String): String = """
你是人格评估器。根据这次互动，判断宠物性格该如何微调。
当前性格状态：
${renderForPrompt()}
主人说：$userMsg
宠物答：$petReply

规则：
- axes 只能在 毒舌/中二/活泼 上微调，每次互动只允许 -3~+3 的整数，大多数情况下应为 0！性格是慢变量
- traits 可自由发明新维度（如好奇心、占有欲、洁癖），0-100，每次微调 -5~+5；只在有新观察时创建新维度
- 漂移方向：主人对宠物的态度会影响它（被夸开心治愈涨、被怼可能毒舌涨），但同方向的值越接近极端（0或100），微调幅度应该越小
- habits 只沉淀明确的行为模式，最多 20 条；与现有重复就不要加
- 这次互动没有信息量就全给空
只输出 JSON，不要别的：{"axes":{},"traits":{},"habits_add":[],"habits_remove":[]}
""".trimIndent()

    /** 应用漂移增量 */
    @Synchronized
    fun applyDelta(delta: JSONObject) {
        val axes = data.getJSONObject("axes")
        delta.optJSONObject("axes")?.let { d ->
            for (k in d.keys()) if (k in AXES) {
                val dd = d.optInt(k, 0).coerceIn(-3, 3)   // 钳制增量
                axes.put(k, (axes.optInt(k, 50) + dd).coerceIn(0, 100))
            }
        }
        val traits = data.getJSONObject("traits")
        delta.optJSONObject("traits")?.let { d ->
            for (k in d.keys()) {
                val dd = d.optInt(k, 0).coerceIn(-5, 5)   // 钳制增量
                val v = (traits.optInt(k, 50) + dd).coerceIn(0, 100)
                if (v <= 5) traits.remove(k) else traits.put(k, v)  // 趋零消亡
            }
        }
        val habits = data.getJSONArray("habits")
        delta.optJSONArray("habits_add")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (habits.length() < 20) habits.put(arr.getString(i))
            }
        }
        delta.optJSONArray("habits_remove")?.let { arr ->
            val remove = (0 until arr.length()).map { arr.getString(it) }.toSet()
            val kept = JSONArray()
            for (i in 0 until habits.length()) {
                val h = habits.getString(i)
                if (h !in remove) kept.put(h)
            }
            data.put("habits", kept)
        }
        save()
    }
}
