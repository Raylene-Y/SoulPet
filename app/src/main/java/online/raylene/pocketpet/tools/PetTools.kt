package online.raylene.pocketpet.tools

import android.content.Context
import org.json.JSONObject

object PetTools {
    val ALL: List<PetTool> = listOf(
        BatteryTool,
        DateTimeTool,
        ClipboardTool,
    )

    private val byName = ALL.associateBy { it.name }

    fun execute(ctx: Context, name: String, argsJson: String): String {
        val tool = byName[name] ?: return "（没有这个工具：$name）"
        return try {
            val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            tool.run(ctx, args)
        } catch (e: Exception) {
            "（工具 $name 执行出错：${e.message}）"
        }
    }
}
