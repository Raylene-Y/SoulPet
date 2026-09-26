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

        setContentView(root)
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
