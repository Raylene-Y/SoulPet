package online.raylene.pocketpet.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 定时任务工具：宠物接下"X分钟后叫我 / 每天8点提醒我"类请求。
 * 任务存 tasks.json，由 PetService 的触发器引擎消费。
 */
class ScheduleTool(ctx: Context) : PetTool {
    override val name = "schedule_task"
    override val description = "安排一个定时任务：到时间后你会主动找主人执行/提醒。支持两种：几分钟后一次（minutes_from_now），或每天固定时间（daily_time，如 08:30）。"
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("task", JSONObject().put("type", "string").put("description", "到点要做/要说的事，如：提醒主人喝水"))
            .put("minutes_from_now", JSONObject().put("type", "integer").put("description", "几分钟后触发（一次性）"))
            .put("daily_time", JSONObject().put("type", "string").put("description", "每天触发时间 HH:mm")))

    private val file = File(ctx.filesDir, "pet/tasks.json")

    override fun run(ctx: Context, args: JSONObject): String {
        val task = args.optString("task", "").trim()
        if (task.isEmpty()) return "任务内容是空的"
        val minutes = if (args.has("minutes_from_now")) args.getInt("minutes_from_now") else null
        val daily = args.optString("daily_time", "").trim().ifEmpty { null }
        if (minutes == null && daily == null) return "要告诉我什么时候触发（几分钟后，或每天几点）"

        val tasks = load()
        tasks.put(JSONObject()
            .put("id", "t${System.currentTimeMillis()}")
            .put("task", task)
            .put("at", minutes?.let { System.currentTimeMillis() + it * 60_000L } ?: JSONObject.NULL)
            .put("daily", daily ?: JSONObject.NULL)
            .put("lastFiredDay", ""))
        save(tasks)
        return "已安排：$task（" +
            (minutes?.let { "${it}分钟后" } ?: "每天 $daily") + "）"
    }

    companion object {
        fun loadFrom(ctx: Context): JSONArray =
            try { JSONArray(File(ctx.filesDir, "pet/tasks.json").readText()) }
            catch (e: Exception) { JSONArray() }

        fun saveTo(ctx: Context, tasks: JSONArray) =
            File(ctx.filesDir, "pet/tasks.json").writeText(tasks.toString())
    }

    private fun load(): JSONArray =
        try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
    private fun save(t: JSONArray) = file.writeText(t.toString())
}
