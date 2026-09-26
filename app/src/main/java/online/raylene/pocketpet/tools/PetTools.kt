package online.raylene.pocketpet.tools

import android.content.Context
import online.raylene.pocketpet.MemoryStore
import org.json.JSONObject

object PetTools {
    lateinit var store: MemoryStore

    fun all(): List<PetTool> = listOf(
        BatteryTool,
        DateTimeTool,
        ClipboardTool,
        RememberTool(store),
    )

    fun execute(ctx: Context, name: String, argsJson: String): String {
        val tool = all().find { it.name == name } ?: return "（没有这个工具：$name）"
        return try {
            val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            tool.run(ctx, args)
        } catch (e: Exception) {
            "（工具 $name 执行出错：${e.message}）"
        }
    }
}
