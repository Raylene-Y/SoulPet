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
