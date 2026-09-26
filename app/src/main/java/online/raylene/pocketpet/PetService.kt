package online.raylene.pocketpet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

class PetService : Service() {

    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private lateinit var pet: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private val scale by lazy { (resources.displayMetrics.density * 4).toInt().coerceAtLeast(6) }
    private val petPx get() = SlimeFrames.W * scale

    // 主动陪伴：触发冷却记录
    private val lastTriggerAt = mutableMapOf<String, Long>()
    private val TRIGGER_COOLDOWN = 4 * 3600_000L   // 同类触发 4 小时一次
    private val CHECK_INTERVAL = 15 * 60_000L       // 每 15 分钟检查一次

    private val proactiveLoop = object : Runnable {
        override fun run() {
            checkTriggers()
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    private fun checkTriggers() {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()

        // 触发器 1：电量低（<20% 且未充电）
        val bi = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val pct = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) * 100 /
                      bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val charging = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ==
                android.os.BatteryManager.BATTERY_STATUS_CHARGING
            if (pct < 20 && !charging) tryTrigger("battery_low", now,
                "主人手机电量只剩 $pct% 了，还没充电")
        }

        // 触发器 2：早安（9 点～11 点之间，今天还没打过招呼）
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 9..10) tryTrigger("morning_" + cal.get(java.util.Calendar.DAY_OF_YEAR), now,
            "现在是早上 $hour 点，新的一天开始了")

        // 触发器 3：被冷落（距上次对话超过 3 小时，且现在不是深夜）
        if (LlmClient.lastChatAt > 0 && now - LlmClient.lastChatAt > 3 * 3600_000L && hour in 8..23) {
            tryTrigger("lonely", now, "主人已经 ${(now - LlmClient.lastChatAt) / 3600_000} 小时没理你了")
        }
    }

    private fun tryTrigger(key: String, now: Long, desc: String) {
        val last = lastTriggerAt[key] ?: 0L
        if (now - last < TRIGGER_COOLDOWN) return
        lastTriggerAt[key] = now
        LlmClient.init(applicationContext)
        LlmClient.proactive(applicationContext, desc) { msg ->
            handler.post { notifyProactive(msg) }
        }
    }

    private fun notifyProactive(msg: String) {
        val chId = "proactive"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(chId, "宠物找你", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 1,
            Intent(this, ChatActivity::class.java)
                .putExtra("proactive_msg", msg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, chId)
            .setContentTitle("你的宠物找你")
            .setContentText(msg)
            .setStyle(Notification.BigTextStyle().bigText(msg))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
        // 宠物表情联动：squish 一下表示有话要说
        pet.setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.HAPPY, scale))
    }

    // 动画状态机（MVP：idle 呼吸 + 偶尔 squish，拖拽时 happy）
    private var dragging = false
    private var tick = 0
    private val animLoop = object : Runnable {
        override fun run() {
            tick++
            val pose = when {
                dragging -> SlimeFrames.Pose.HAPPY
                tick % 12 == 0 -> SlimeFrames.Pose.SQUISH
                tick % 2 == 0 -> SlimeFrames.Pose.IDLE_B
                else -> SlimeFrames.Pose.IDLE_A
            }
            pet.setImageBitmap(SlimeFrames.draw(pose, scale))
            handler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        running = true
        startForeground(1, buildNotification())

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        pet = ImageView(this)
        params = WindowManager.LayoutParams(
            petPx, petPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 300
        }

        pet.setOnTouchListener(TouchHandler())
        wm.addView(pet, params)
        handler.post(animLoop)
        handler.postDelayed(proactiveLoop, 60_000)  // 启动 1 分钟后开始检查
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::pet.isInitialized) wm.removeView(pet)
        super.onDestroy()
    }

    private inner class TouchHandler : android.view.View.OnTouchListener {
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var moved = false

        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false; dragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = startX + dx; params.y = startY + dy
                    wm.updateViewLayout(pet, params)
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    if (!moved) onPetTapped() else snapToEdge()
                }
            }
            return true
        }
    }

    private fun onPetTapped() {
        // 点宠物 → 打开对话
        pet.setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.SQUISH, scale))
        val i = Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun snapToEdge() {
        val screenW = resources.displayMetrics.widthPixels
        val targetX = if (params.x + petPx / 2 < screenW / 2) 0 else screenW - petPx
        // 简单动画贴边
        val steps = 8
        val fromX = params.x
        handler.post(object : Runnable {
            var i = 0
            override fun run() {
                i++
                params.x = fromX + (targetX - fromX) * i / steps
                wm.updateViewLayout(pet, params)
                if (i < steps) handler.postDelayed(this, 16)
            }
        })
    }

    private fun buildNotification(): Notification {
        val chId = "pet"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(chId, "PocketPet", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, chId)
            .setContentTitle("PocketPet 活着呢")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .build()
    }
}
