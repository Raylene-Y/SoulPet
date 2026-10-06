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

    /** 内置人格预设表（真机实测初始化数据固化）。自定义人格走 LLM 评定兜底 */
    private val PRESETS = mapOf(
        "毒舌" to (triple(100, 60, 10) to mapOf("暴躁" to 90, "傲慢" to 85, "不耐烦" to 80)),
        "治愈" to (triple(0, 0, 20) to mapOf("温柔" to 90, "关心" to 90, "体贴" to 85)),
        "中二" to (triple(20, 95, 85) to mapOf("夸张" to 90, "戏剧化" to 85, "忠诚" to 80)),
        "管家" to (triple(0, 0, 0) to mapOf("恭敬" to 90, "高效" to 90, "体贴" to 90)),
        "猫娘" to (triple(0, 50, 90) to mapOf("粘人" to 100, "撒娇" to 90, "委屈" to 80)),
        "诗人" to (triple(0, 0, 0) to mapOf("诗意" to 100, "文艺" to 90, "敏感细腻" to 95))
    )
    private fun triple(a: Int, b: Int, c: Int) = Triple(a, b, c)

    /** 预设命中则直接初始化，返回 true（零调用）；没命中走 LLM */
    @Synchronized
    fun applyPresetIfAny(): Boolean {
        val preset = PRESETS[current] ?: return false
        val p = profile()
        val axes = p.getJSONObject("axes")
        axes.put("毒舌", preset.first.first)
        axes.put("中二", preset.first.second)
        axes.put("活泼", preset.first.third)
        val traits = p.getJSONObject("traits")
        for ((k, v) in preset.second) traits.put(k, v)
        p.put("inited", true)
        save()
        return true
    }

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

    /** 该人格是否还没定初始坐标（applyInit 打过标记才算初始化过） */
    @Synchronized
    fun needsInit(): Boolean {
        val personas = root.optJSONObject("personas") ?: return true
        return personas.optJSONObject(current)?.optBoolean("inited") != true
    }

    /** 人格初始化提示词：读人格文件 → 一次性定起始坐标 */
    fun initPrompt(personaPrompt: String): String = """
你是人格测评师。读下面的人格设定，给出它的初始性格坐标。

人格设定：
$personaPrompt

规则：
- axes：毒舌/中二/活泼 各 0-100，按人格设定的本色给分（毒舌人格毒舌就该 80+，别客气）
- traits：从设定里提炼 0-3 个突出的特质（如粘人、好奇心），给 0-100 的分；没有就给空对象
只输出 JSON：{"axes":{"毒舌":x,"中二":y,"活泼":z},"traits":{}}
""".trimIndent()

    /** 应用初始坐标（覆盖式，仅初始化用） */
    @Synchronized
    fun applyInit(init: JSONObject) {
        val p = profile()
        init.optJSONObject("axes")?.let { a ->
            val axes = p.getJSONObject("axes")
            for (k in a.keys()) if (k in AXES) axes.put(k, a.optInt(k, 50).coerceIn(0, 100))
        }
        init.optJSONObject("traits")?.let { t ->
            val traits = p.getJSONObject("traits")
            for (k in t.keys()) traits.put(k, t.optInt(k, 50).coerceIn(0, 100))
        }
        p.put("inited", true)
        save()
    }

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
