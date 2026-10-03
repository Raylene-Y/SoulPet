package online.raylene.pocketpet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val dp get() = resources.displayMetrics.density
    private fun Int.dp() = (this * dp).toInt()

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 28f * dp
    }

    /** 蓝色胶囊按钮 */
    private fun pillButton(label: String, filled: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
            if (filled) {
                setTextColor(Color.WHITE)
                background = pill(ChatActivity.SLIME_BLUE)
            } else {
                setTextColor(ChatActivity.SLIME_DEEP)
                background = pill(Color.WHITE)
            }
            setOnClickListener { onClick() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TtsManager.warmUp(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                "com.termux.permission.RUN_COMMAND"
            ), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 60.dp(), 24.dp(), 24.dp())
            setBackgroundColor(ChatActivity.BG_SOFT)
        }

        // 大史莱姆
        val slime = ImageView(this).apply {
            setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.HAPPY, 8))
            layoutParams = LinearLayout.LayoutParams(140.dp(), 140.dp())
        }
        root.addView(slime)

        val title = TextView(this).apply {
            text = "PocketPet"
            textSize = 26f
            setTextColor(ChatActivity.INK)
            setPadding(0, 16.dp(), 0, 4.dp())
        }
        val sub = TextView(this).apply {
            text = "住在你屏幕上的小东西"
            textSize = 14f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 0, 0, 28.dp())
        }
        root.addView(title); root.addView(sub)

        // 召唤按钮（主按钮）
        val summon = pillButton(if (PetService.running) "收起来" else "召唤它", true) {
            if (PetService.running) {
                stopService(Intent(this, PetService::class.java))
                (it as TextView).text = "召唤它"
            } else {
                summonPet()
                (it as TextView).text = "收起来"
            }
        }
        summon.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        summon.textSize = 17f
        summon.setPadding(0, 16.dp(), 0, 16.dp())
        root.addView(summon)

        // ── 皮肤 ──
        val skinTitle = TextView(this).apply {
            text = "皮肤"
            textSize = 13f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(4.dp(), 24.dp(), 0, 8.dp())
        }
        root.addView(skinTitle)
        val skinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(skinRow)
        renderSkins(skinRow)

        // ── 高级设置（折叠） ──
        val advancedBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        val advToggle = TextView(this).apply {
            text = "▸ 高级设置"
            textSize = 13f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(4.dp(), 28.dp(), 0, 8.dp())
            setOnClickListener {
                val show = advancedBox.visibility == android.view.View.GONE
                advancedBox.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
                text = if (show) "▾ 高级设置" else "▸ 高级设置"
            }
        }
        root.addView(advToggle)

        // 高级项：模型设置 / 命令回传授权 / Termux 桥测试
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(pillButton("模型设置", false) { showLlmSettings() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8.dp(), 0) }
        })
        row1.addView(pillButton("命令回传授权", false) { grantStorage() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        advancedBox.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(pillButton("测试 Termux 桥", false) { testTermuxBridge() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 8.dp(), 0, 0) }
        })
        advancedBox.addView(row2)

        root.addView(advancedBox)
        setContentView(root)

        // 有权限且未在跑 → 直接召唤，并同步按钮文字
        if (!PetService.running && Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, PetService::class.java))
            summon.postDelayed({ summon.text = "收起来" }, 500)
        }
    }

    private fun renderSkins(row: LinearLayout) {
        row.removeAllViews()
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        val current = prefs.getString("pet_id", "slime") ?: "slime"
        val skins = mutableListOf("slime" to "史莱姆")
        PetPackage.scanAll(java.io.File(filesDir, "pet/pets")).forEach { skins.add(it.id to it.displayName) }
        for ((id, label) in skins) {
            val chip = pillButton((if (id == current) "✓ " else "") + label, id == current) {
                prefs.edit().putString("pet_id", id).apply()
                if (PetService.running) stopService(Intent(this, PetService::class.java))
                startForegroundService(Intent(this, PetService::class.java))
                recreate()
            }
            chip.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 8.dp(), 0)
            }
            row.addView(chip)
        }
    }

    private fun grantStorage() {
        if (android.os.Environment.isExternalStorageManager()) {
            Toast.makeText(this, "已授权", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        }
    }

    private fun testTermuxBridge() {
        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/termux-notification")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-t", "桥测试", "-c", "看到这条说明桥通了"))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        val r = try {
            startService(i); "已发送，看通知栏有没有【桥测试】通知"
        } catch (e: Exception) { "失败：${e.message}" }
        android.app.AlertDialog.Builder(this)
            .setTitle("桥测试结果").setMessage(r).setPositiveButton("好", null).show()
    }

    private fun showLlmSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }
        val etUrl = EditText(this).apply {
            hint = "Base URL（OpenAI 兼容接口）"; setText(LlmConfig.baseUrl(this@MainActivity))
        }
        val etKey = EditText(this).apply {
            hint = "API Key"; setText(LlmConfig.apiKey(this@MainActivity))
        }
        val etModel = EditText(this).apply {
            hint = "模型名，如 glm-4.5-flash / deepseek-chat"; setText(LlmConfig.model(this@MainActivity))
        }
        box.addView(etUrl); box.addView(etKey); box.addView(etModel)
        android.app.AlertDialog.Builder(this)
            .setTitle("模型设置")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                LlmConfig.save(this, etUrl.text.toString(), etKey.text.toString(), etModel.text.toString())
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun summonPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, PetService::class.java))
    }
}
