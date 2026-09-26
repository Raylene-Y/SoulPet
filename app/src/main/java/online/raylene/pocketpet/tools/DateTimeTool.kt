package online.raylene.pocketpet.tools

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeTool : PetTool {
    override val name = "get_datetime"
    override val description = "获取当前日期、时间、星期"
    override val parameters = emptyParams()

    override fun run(ctx: Context, args: JSONObject): String {
        val now = Date()
        val dt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA).format(now)
        return "现在是 $dt"
    }
}
