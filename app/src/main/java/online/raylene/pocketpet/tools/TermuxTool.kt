package online.raylene.pocketpet.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Termux 桥：通过 RUN_COMMAND intent 让 Termux 后台跑 shell。
 * MVP 为 fire-and-forget（异步无回执）；结果回传后续版本用 Termux 写共享文件实现。
 */
object TermuxTool : PetTool {
    override val name = "termux_run"
    override val description = "在用户手机的 Termux（Linux 环境）里执行一条 shell 命令。可以调 termux-api（拍照/发短信/定位/震动等）或任何已安装的 Linux 工具。命令在后台执行，看不到输出，需要结果时让命令自己发通知（termux-notification）。仅当手机装有 Termux 时可用。"
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().put("command", JSONObject()
            .put("type", "string")
            .put("description", "要执行的 shell 命令，如 termux-battery-status")))
        .put("required", JSONArray().put("command"))

    fun isAvailable(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    override fun run(ctx: Context, args: JSONObject): String {
        if (!isAvailable(ctx)) return "手机没装 Termux，这条手臂不存在（告诉主人需要安装 Termux app）"
        val cmd = args.optString("command", "").trim()
        if (cmd.isEmpty()) return "命令是空的"

        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        // 调试：记录发送现场
        val dbg = java.io.File(ctx.filesDir, "pet/termux_debug.log")
        fun log(s: String) = dbg.appendText("${java.util.Date()} $s\n")
        val resolved = ctx.packageManager.resolveService(i, 0)
        log("resolveService=${resolved?.serviceInfo?.name}")
        return try {
            val cn = ctx.startService(i)
            log("startService result=$cn")
            "命令已发给 Termux 后台执行：$cmd（无回执，如需结果让命令里调 termux-notification 发通知）"
        } catch (e: Exception) {
            log("startService EX: $e")
            "Termux 拒绝了：${e.message}（可能要在系统设置里给 PocketPet 允许「运行 Termux 命令」权限）"
        }
    }
}
