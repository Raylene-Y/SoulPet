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

    companion object {
        @Volatile var running = false
        @Volatile var chatOpen = false   // 聊天窗开着时宠物定住陪聊
    }

    private lateinit var wm: WindowManager
    private lateinit var pet: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    // 皮肤：null = 内置史莱姆，非空 = CoPet 宠物包
    private var pack: PetPackage? = null
    private var frameIdx = 0

    private val scale by lazy { (resources.displayMetrics.density * 4).toInt().coerceAtLeast(6) }
    private val petPx get() = pack?.let { (resources.displayMetrics.density * 110).toInt() }
        ?: (SlimeFrames.W * scale)

    // 主动陪伴：触发冷却记录
    private val lastTriggerAt = mutableMapOf<String, Long>()
    private val TRIGGER_COOLDOWN = 4 * 3600_000L   // 同类触发 4 小时一次
    private val CHECK_INTERVAL = 60_000L        // 每分钟检查一次（定时任务需要精度）

    private val proactiveLoop = object : Runnable {
        override fun run() {
            try {
                java.io.File(filesDir, "pet/proactive_debug.log")
                    .appendText("${java.util.Date()} tick\n")
                checkTriggers()
            } catch (e: Exception) {
                java.io.File(filesDir, "pet/proactive_debug.log")
                    .appendText("${java.util.Date()} LOOP EX: $e\n")
            }
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

        // 触发器 4：到点的定时任务（调教对话产生的）
        checkScheduledTasks(now)
    }

    private fun checkScheduledTasks(now: Long) {
        val tasks = online.raylene.pocketpet.tools.ScheduleTool.loadFrom(applicationContext)
        if (tasks.length() == 0) return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        var dirty = false
        for (i in tasks.length() - 1 downTo 0) {
            val t = tasks.getJSONObject(i)
            val at = if (t.isNull("at")) 0L else t.getLong("at")
            val daily = if (t.isNull("daily")) "" else t.getString("daily")
            val dueOnce = at in 1..now
            val dueDaily = daily.isNotEmpty() && t.optString("lastFiredDay") != today &&
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()) >= daily
            if (dueOnce || dueDaily) {
                val taskText = t.getString("task")
                if (dueOnce) tasks.remove(i) else t.put("lastFiredDay", today)
                dirty = true
                LlmClient.init(applicationContext)
                LlmClient.proactive(applicationContext,
                    "到了主人之前安排的时间，任务：$taskText。现在就主动开口执行/提醒。") { msg ->
                    handler.post { notifyProactive(msg) }
                }
            }
        }
        if (dirty) online.raylene.pocketpet.tools.ScheduleTool.saveTo(applicationContext, tasks)
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

    // ── 行为状态机 ──
    private enum class State { IDLE, WALK, SLEEP }
    private var state = State.IDLE
    private var stateTicks = 0
    private var facing = 1          // 1=右 -1=左
    private var facingY = 1         // 1=下 -1=上
    private var dragging = false
    private var tick = 0
    private val rng = java.util.Random()

    private fun pickNextState(): State {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val night = hour >= 23 || hour < 7
        val roll = rng.nextInt(100)
        return when {
            night && roll < 50 -> State.SLEEP
            roll < 40 -> State.IDLE
            roll < 85 -> State.WALK
            else -> State.SLEEP
        }
    }

    private fun stateDuration(s: State) = when (s) {
        State.IDLE -> 8 + rng.nextInt(12)      // 3~8 秒
        State.WALK -> 25 + rng.nextInt(50)     // 10~30 秒，基本能走到墙
        State.SLEEP -> 20 + rng.nextInt(25)    // 8~18 秒
    }

    private val animLoop = object : Runnable {
        override fun run() {
            tick++; stateTicks++
            val dur = stateDuration(state)

            val pose: SlimeFrames.Pose = when {
                dragging -> SlimeFrames.Pose.HAPPY
                chatOpen -> SlimeFrames.Pose.IDLE_A   // 陪聊时安静坐着
                state == State.SLEEP -> SlimeFrames.Pose.SLEEP
                state == State.WALK && !chatOpen -> {
                    // 走路：二维位移 + 颠簸帧，撞墙即转向并结束本次行走
                    params.x += facing * (scale + 6)
                    params.y += facingY * (scale / 2 + 2)
                    val maxX = resources.displayMetrics.widthPixels - petPx
                    val maxY = resources.displayMetrics.heightPixels - petPx - 80
                    var hitWall = false
                    if (params.x <= 0) { params.x = 0; facing = 1; hitWall = true }
                    if (params.x >= maxX) { params.x = maxX; facing = -1; hitWall = true }
                    if (params.y <= 60) { params.y = 60; facingY = 1; hitWall = true }
                    if (params.y >= maxY) { params.y = maxY; facingY = -1; hitWall = true }
                    try { wm.updateViewLayout(pet, params) } catch (_: Exception) {}
                    if (hitWall) stateTicks += 15   // 撞墙加速结束这段行走
                    if (tick % 2 == 0) SlimeFrames.Pose.IDLE_A else SlimeFrames.Pose.IDLE_B
                }
                else -> if (tick % 2 == 0) SlimeFrames.Pose.IDLE_A else SlimeFrames.Pose.IDLE_B
            }

            if (!dragging && !chatOpen && stateTicks >= dur) {
                state = pickNextState()
                stateTicks = 0
                if (state == State.WALK) {
                    if (rng.nextBoolean()) facing = -facing
                    facingY = if (rng.nextBoolean()) 1 else -1
                }
            }

            // 聊天窗打开：强制回安静态（不半路定格在跑姿）
            if (chatOpen && state != State.IDLE) {
                state = State.IDLE
                stateTicks = 0
                frameIdx = 0
            }

            if (pack != null) {
                renderPack()
                handler.postDelayed(this, tickDelay())
                return
            }
            var bmp = SlimeFrames.draw(pose, scale)
            if (facing < 0) bmp = SlimeFrames.flip(bmp)
            pet.setImageBitmap(bmp)
            handler.postDelayed(this, 400)
        }
    }

    /** 加载皮肤：SharedPreferences 里 pet_id，默认 slime */
    private fun loadSkin() {
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        val petId = prefs.getString("pet_id", "slime") ?: "slime"
        pack = null
        if (petId != "slime") {
            val root = java.io.File(filesDir, "pet/pets/$petId")
            if (root.exists()) {
                pack = try { PetPackage(root) } catch (e: Exception) { null }
            }
        }
        frameIdx = 0
    }

    /** 宠物包模式渲染：状态 → 行映射 */
    private fun packPose(): PetPackage.Row = when {
        dragging -> PetPackage.Row.JUMPING
        state == State.SLEEP -> PetPackage.Row.IDLE
        state == State.WALK -> if (facing > 0) PetPackage.Row.RUN_RIGHT else PetPackage.Row.RUN_LEFT
        else -> PetPackage.Row.IDLE
    }

    private fun renderPack() {
        val p = pack ?: return
        val row = packPose()
        val step = if (state == State.SLEEP) 3 else 1
        if (tick % step == 0) frameIdx = (frameIdx + 1) % 1000  // frame() 内部按有效帧数取模
        pet.setImageBitmap(p.frame(row, frameIdx))
    }

    private fun tickDelay(): Long = if (pack != null) 150 else 400

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        running = true
        startForeground(1, buildNotification())
        loadSkin()

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
        val p = pack
        if (p != null) pet.setImageBitmap(p.frame(PetPackage.Row.WAVING, 0))
        else pet.setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.SQUISH, scale))
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
