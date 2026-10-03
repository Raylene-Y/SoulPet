package online.raylene.pocketpet.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Termux 桥（带回传）：命令执行完 Termux 把 stdout/stderr/exit code 写进
 * /sdcard/PocketPet/results/<id>.txt（ResultDirectory 机制），宠物轮询读取。
 * 前置：① PocketPet 允许「所有文件访问」② Termux 跑过 termux-setup-storage
 */
object TermuxTool : PetTool {
    override val name = "termux_run"
    override val description = """在用户手机的 Termux（Linux 环境）里执行 shell 命令并返回输出。
常用能力（termux-api 命令，直接当命令用）：
- termux-battery-status 电量 | termux-vibrate [-d 毫秒] 震动 | termux-torch on/off 手电筒
- termux-location 定位(GPS) | termux-clipboard-set/get 剪贴板 | termux-tts-speak "文本" 语音播报
- termux-notification -t 标题 -c 内容 发通知 | termux-toast "文本" 弹提示 | termux-volume 音量
- termux-camera-photo -c 0 文件.jpg 拍照 | termux-sms-send -n 号码 "文本" 发短信 | termux-contact-list 通讯录
- termux-brightness 0-255 亮度 | termux-dialog 弹窗 | termux-wifi-connectioninfo WiFi信息
也支持任何已装的 Linux 工具（python3、ffmpeg、curl、git 等）。
多步骤命令用 && 串联。输出会自动回传给你，用人格口吻向主人汇报结果。""".trimIndent()
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().put("command", JSONObject()
            .put("type", "string")
            .put("description", "要执行的 shell 命令")))
        .put("required", JSONArray().put("command"))

    private val resultDir = File(Environment.getExternalStorageDirectory(), "PocketPet/results")
    private const val TIMEOUT_MS = 25_000L

    fun isAvailable(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun resultReady(): Boolean = Environment.isExternalStorageManager() || resultDir.exists()

    override fun run(ctx: Context, args: JSONObject): String {
        if (!isAvailable(ctx)) return "手机没装 Termux，这条手臂不存在（告诉主人需要安装 Termux app）"
        val cmd = args.optString("command", "").trim()
        if (cmd.isEmpty()) return "命令是空的"

        val canResult = Environment.isExternalStorageManager()
        val id = "r${System.currentTimeMillis()}"
        val resultFile = File(resultDir, "$id.txt")
        if (canResult) resultDir.mkdirs()

        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            if (canResult) {
                putExtra("com.termux.RUN_COMMAND_RESULT_DIRECTORY", resultDir.absolutePath)
                putExtra("com.termux.RUN_COMMAND_RESULT_SINGLE_FILE", true)
                putExtra("com.termux.RUN_COMMAND_RESULT_FILE_BASENAME", id)
                putExtra("com.termux.RUN_COMMAND_RESULT_FILES_SUFFIX", ".txt")
            }
        }
        try {
            ctx.startService(i)
        } catch (e: Exception) {
            return "Termux 拒绝了：${e.message}"
        }

        if (!canResult) return "命令已在后台执行（未开回传，看不到输出；要开回传需授予 PocketPet 所有文件访问权限 + Termux 跑 termux-setup-storage）"

        // 轮询等结果
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (resultFile.exists() && resultFile.length() > 0) {
                Thread.sleep(300)  // 等写完
                val out = resultFile.readText().trim()
                resultFile.delete()
                return if (out.isEmpty()) "命令执行完，没有输出"
                       else "命令输出：\n${out.take(2000)}"
            }
            Thread.sleep(500)
        }
        return "命令已执行，但 ${TIMEOUT_MS / 1000} 秒内没等到输出文件（可能命令还在跑，或 Termux 没开存储权限——让它跑一下 termux-setup-storage）"
    }
}
