package online.raylene.pocketpet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 宠物记忆存储。全部落在 filesDir/pet/ 下，纯文本/JSON，用户可直接读写：
 *   history.json   近期对话（滚动 30 条）
 *   memory.md      长期记忆（宠物通过 remember 工具主动写入的事实）
 *   personas/      人格脚本文件（<名字>.md，存在则覆盖内置人格）
 */
class MemoryStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "pet").apply { mkdirs() }
    private val historyFile = File(dir, "history.json")
    private val memoryFile = File(dir, "memory.md")
    val personaDir = File(dir, "personas").apply { mkdirs() }

    fun loadHistory(): JSONArray {
        if (!historyFile.exists()) return JSONArray()
        return try { JSONArray(historyFile.readText()) } catch (e: Exception) { JSONArray() }
    }

    fun saveHistory(history: JSONArray) {
        val tmp = File(dir, "history.json.tmp")
        tmp.writeText(history.toString())
        tmp.renameTo(historyFile)  // 原子写
    }

    fun readMemory(): String =
        if (memoryFile.exists()) memoryFile.readText().trim() else ""

    fun appendMemory(fact: String) {
        memoryFile.appendText("- $fact\n")
    }

    /** 梦境笔记 */
    fun saveDream(text: String) {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        File(dir, "dreams").mkdirs()
        File(dir, "dreams/$day.md").writeText(text)
    }

    fun readLatestDream(): String {
        val d = File(dir, "dreams")
        val latest = d.listFiles()?.maxByOrNull { it.name } ?: return ""
        return latest.readText().trim()
    }

    // ── 成长统计（亲密度等级）──
    private val statsFile get() = File(dir, "stats.json")
    private val stats: JSONObject by lazy {
        if (statsFile.exists()) try { JSONObject(statsFile.readText()) } catch (e: Exception) { JSONObject() }
        else JSONObject()
    }

    /** 记一次对话，返回 (等级, 是否升级) */
    @Synchronized
    fun bumpChat(): Pair<Int, Boolean> {
        val n = stats.optInt("msgCount", 0) + 1
        val oldLv = level(stats.optInt("msgCount", 0))
        val newLv = level(n)
        stats.put("msgCount", n)
        if (!stats.has("firstMeet")) stats.put("firstMeet", System.currentTimeMillis())
        statsFile.writeText(stats.toString())
        return newLv to (newLv > oldLv)
    }

    fun msgCount(): Int = stats.optInt("msgCount", 0)

    /** 等级：聊得越多越亲（Lv1 陌生 → Lv5 老友） */
    fun level(n: Int = msgCount()): Int = when {
        n >= 800 -> 5; n >= 300 -> 4; n >= 100 -> 3; n >= 30 -> 2; else -> 1
    }

    fun levelTitle(lv: Int = level()): String = when (lv) {
        5 -> "老友"; 4 -> "挚友"; 3 -> "熟人"; 2 -> "认识"; else -> "陌生"
    }

    /** 记忆条目列表（删除用） */
    fun memoryLines(): MutableList<String> {
        if (!memoryFile.exists()) return mutableListOf()
        return memoryFile.readLines().filter { it.isNotBlank() }.toMutableList()
    }

    fun rewriteMemory(lines: List<String>) {
        memoryFile.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "")
    }

    // ── 技能（养成资产：教过的命令）──
    private val skillsFile get() = File(dir, "skills.md")

    /** 技能列表，格式：- 技能名 → termux 命令 */
    fun skillLines(): MutableList<String> {
        if (!skillsFile.exists()) return mutableListOf()
        return skillsFile.readLines().filter { it.trim().startsWith("- ") }.toMutableList()
    }

    @Synchronized
    fun addSkill(name: String, command: String) {
        skillsFile.appendText("- $name → $command\n")
    }

    fun rewriteSkills(lines: List<String>) {
        skillsFile.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "")
    }

    /** 人格：磁盘文件优先，没有就用内置默认 */
    fun loadPersonas(): MutableList<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        for (p in Persona.values()) list.add(p.label to p.prompt)
        personaDir.listFiles { f -> f.extension == "md" }?.sortedBy { it.name }?.forEach { f ->
            val name = f.nameWithoutExtension
            val body = f.readText().trim()
            val idx = list.indexOfFirst { it.first == name }
            if (idx >= 0) list[idx] = name to body else list.add(name to body)
        }
        return list
    }
}
