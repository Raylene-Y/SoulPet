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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val dp get() = resources.displayMetrics.density
    private fun Int.dp() = (this * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                "com.termux.permission.RUN_COMMAND"
            ), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 80.dp(), 24.dp(), 24.dp())
            setBackgroundColor(ChatActivity.BG_SOFT)
        }

        // 大史莱姆
        val slime = ImageView(this).apply {
            setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.HAPPY, 8))
            layoutParams = LinearLayout.LayoutParams(160.dp(), 160.dp())
        }
        root.addView(slime)

        val title = TextView(this).apply {
            text = "PocketPet"
            textSize = 26f
            setTextColor(ChatActivity.INK)
            setPadding(0, 20.dp(), 0, 6.dp())
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "住在你屏幕上的小东西"
            textSize = 14f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 0, 0, 40.dp())
            gravity = Gravity.CENTER
        }
        root.addView(title); root.addView(sub)

        val toggle = TextView(this).apply {
            text = if (PetService.running) "收起来" else "召唤它"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 16.dp())
            background = GradientDrawable().apply {
                setColor(ChatActivity.SLIME_BLUE)
                cornerRadius = 28f * dp
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setOnClickListener {
                if (PetService.running) {
                    stopService(Intent(this@MainActivity, PetService::class.java))
                    text = "召唤它"
                } else summon()
            }
        }
        root.addView(toggle)

        val testBridge = android.widget.Button(this).apply {
            text = "测试 Termux 桥"
            setOnClickListener {
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
                    startService(i)
                    "已发送，看通知栏有没有【桥测试】通知"
                } catch (e: Exception) { "失败：${e.message}" }
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("桥测试结果").setMessage(r).setPositiveButton("好", null).show()
            }
        }
        root.addView(testBridge)

        val grantStorage = android.widget.Button(this).apply {
            text = "开启命令回传（授权文件访问）"
            setOnClickListener {
                if (android.os.Environment.isExternalStorageManager()) {
                    Toast.makeText(this@MainActivity, "已授权", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
        }
        root.addView(grantStorage)

        // ── 皮肤选择 ──
        val skinTitle = TextView(this).apply {
            text = "皮肤"
            textSize = 14f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 32.dp(), 0, 8.dp())
        }
        root.addView(skinTitle)
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        val currentSkin = prefs.getString("pet_id", "slime") ?: "slime"
        val skins = mutableListOf("slime" to "像素史莱姆（内置）")
        val petsRoot = java.io.File(filesDir, "pet/pets")
        PetPackage.scanAll(petsRoot).forEach { skins.add(it.id to it.displayName) }
        for ((id, label) in skins) {
            val b = android.widget.Button(this).apply {
                text = (if (id == currentSkin) "✓ " else "") + label
                setOnClickListener {
                    prefs.edit().putString("pet_id", id).apply()
                    if (PetService.running) {
                        stopService(Intent(this@MainActivity, PetService::class.java))
                    }
                    startForegroundService(Intent(this@MainActivity, PetService::class.java))
                    recreate()
                }
            }
            root.addView(b)
        }

        setContentView(root)

        // 有权限且未在跑 → 直接召唤（自动化/冷启动友好）
        if (!PetService.running && Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, PetService::class.java))
        }
    }

    private fun summon() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, PetService::class.java))
        finish()
    }
}
