package online.raylene.pocketpet.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject

object BatteryTool : PetTool {
    override val name = "get_battery"
    override val description = "查询手机电量和充电状态"
    override val parameters = emptyParams()

    override fun run(ctx: Context, args: JSONObject): String {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "读不到电量信息"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scaleMax = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = level * 100 / scaleMax
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL
        return "电量 $pct%，${if (charging) "正在充电" else "未在充电"}"
    }
}
