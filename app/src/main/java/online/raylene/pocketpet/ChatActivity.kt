package online.raylene.pocketpet

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.Toast
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ChatActivity : Activity() {

    companion object {
        const val SLIME_BLUE = 0xFF5AC8FA.toInt()
        const val SLIME_DEEP = 0xFF32A0DC.toInt()
        const val BG_SOFT = 0xFFF2F7FB.toInt()
        const val INK = 0xFF2B3445.toInt()
        const val INK_LIGHT = 0xFF8A94A6.toInt()
    }

    private lateinit var msgBox: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: TextView
    private lateinit var personaRow: LinearLayout
    private lateinit var personalityLine: TextView
    private lateinit var titleText: TextView

    private fun refreshPersonalityLine() {
        personalityLine.text = "性格  ${Personality.renderForUI()}"
    }

    private val dp get() = resources.displayMetrics.density
    private fun Int.dp() = (this * dp).toInt()

    private fun round(radius: Float, color: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius * dp }

    override fun onResume() {
        super.onResume()
        PetService.chatOpen = true
    }

    override fun onPause() {
        super.onPause()
        PetService.chatOpen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LlmClient.init(applicationContext)   // 提前：UI 里要用 Personality 数据

        // 全屏聊天页（adjustResize 生效，键盘自动避开输入框）
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 18.dp(), 14.dp(), 20.dp())
            background = GradientDrawable().apply {
                setColor(BG_SOFT)
            }
        }

        // ── 顶部：标题 + 语音开关 + 人格胶囊 ──
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(this).apply {
            val (lv, lvTitle) = LlmClient.levelInfo()
            text = "✦ 你的史莱姆  Lv.$lv $lvTitle"
            textSize = 13f
            setTextColor(INK_LIGHT)
            setPadding(4.dp(), 0, 0, 8.dp())
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        titleText = title
        val voiceToggle = TextView(this).apply {
            text = if (TtsManager.enabled) "🔊" else "🔇"
            textSize = 16f
            setPadding(8.dp(), 0, 4.dp(), 0)
            setOnClickListener {
                TtsManager.enabled = !TtsManager.enabled
                if (!TtsManager.enabled) TtsManager.stop()
                text = if (TtsManager.enabled) "🔊" else "🔇"
            }
        }
        titleRow.addView(title); titleRow.addView(voiceToggle)
        root.addView(titleRow)

        // 人格坐标行（漂移可视化）
        val personalityLine = TextView(this).apply {
            textSize = 11f
            setTextColor(INK_LIGHT)
            setPadding(4.dp(), 0, 0, 6.dp())
        }
        root.addView(personalityLine)
        this.personalityLine = personalityLine
        refreshPersonalityLine()

        personaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val personaScroll = HorizontalScrollView(this).apply {
            addView(personaRow)
            isHorizontalScrollBarEnabled = false
        }
        root.addView(personaScroll)

        // ── 消息区 ──
        msgBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp(), 8.dp(), 2.dp(), 2.dp())
        }
        scroller = ScrollView(this).apply {
            addView(msgBox)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(scroller)

        // ── 输入行（胶囊） ──
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = round(22f, Color.WHITE)
            setPadding(14.dp(), 4.dp(), 6.dp(), 4.dp())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = 10.dp()
            }
        }
        input = EditText(this).apply {
            hint = "和它说点什么…"
            setHintTextColor(INK_LIGHT)
            setTextColor(INK)
            textSize = 15f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        sendBtn = TextView(this).apply {
            text = "➤"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(SLIME_BLUE); shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        }
        inputRow.addView(input); inputRow.addView(sendBtn)
        root.addView(inputRow)

        setContentView(root)

        SoundManager.init(applicationContext)
        renderPersonaChips()
        intent.getStringExtra("proactive_msg")?.let {
            addPetMsg(it)
        } ?: addPetMsg("主人来啦，说点什么吧")

        sendBtn.setOnClickListener { send() }
    }

    // ── 人格胶囊 ──
    private fun renderPersonaChips() {
        personaRow.removeAllViews()
        LlmClient.personas.forEachIndexed { i, (label, _) ->
            val selected = i == LlmClient.personaIndex
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(if (selected) Color.WHITE else SLIME_DEEP)
                setPadding(14.dp(), 6.dp(), 14.dp(), 6.dp())
                background = round(16f, if (selected) SLIME_BLUE else Color.WHITE)
                val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                lp.setMargins(0, 0, 8.dp(), 0)
                layoutParams = lp
                setOnClickListener {
                    LlmClient.personaIndex = i
                    LlmClient.notePersonaSwitch(label)
                    addSysMsg("切换人格 → $label")
                    renderPersonaChips()
                }
            }
            personaRow.addView(chip)
        }
    }

    // ── 气泡消息 ──
    private fun bubble(text: CharSequence, isUser: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, 6.dp(), 0, 6.dp())
        }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(if (isUser) Color.WHITE else INK)
            setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
            background = round(14f, if (isUser) SLIME_BLUE else Color.WHITE)
            maxWidth = (resources.displayMetrics.widthPixels * 0.68).toInt()
        }
        if (!isUser) {
            val avatar = ImageView(this).apply {
                setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.IDLE_A, 2))
                val lp = LinearLayout.LayoutParams(30.dp(), 30.dp())
                lp.setMargins(2.dp(), 0, 8.dp(), 0)
                lp.gravity = Gravity.BOTTOM
                layoutParams = lp
            }
            row.addView(avatar)
        }
        row.addView(tv)
        return row
    }

    private fun addRow(v: android.view.View): TextView? {
        msgBox.addView(v)
        msgBox.post { scroller.fullScroll(ScrollView.FOCUS_DOWN) }
        return null
    }

    private fun addPetMsg(text: CharSequence): TextView {
        val row = bubble(text, false)
        msgBox.addView(row)
        msgBox.post { scroller.fullScroll(ScrollView.FOCUS_DOWN) }
        return row.getChildAt(1) as TextView
    }

    private fun addUserMsg(text: CharSequence) = addRow(bubble(text, true))

    private fun addSysMsg(text: String) {
        val tv = TextView(this).apply {
            this.text = "· $text ·"
            textSize = 12f
            setTextColor(INK_LIGHT)
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 8.dp())
        }
        addRow(tv)
    }

    private fun addToolMsg(name: String) {
        val tv = TextView(this).apply {
            this.text = "⚙ 使用了工具：$name"
            textSize = 12f
            setTextColor(SLIME_DEEP)
            setPadding(0, 2.dp(), 0, 2.dp())
        }
        addRow(tv)
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        LlmClient.lastChatAt = System.currentTimeMillis()
        addUserMsg(text)
        sendBtn.isEnabled = false
        SoundManager.play("thinking")   // 开始思考
        val thinking = addPetMsg("…")
        val lvBefore = LlmClient.levelInfo().first
        LlmClient.chat(applicationContext, text,
            onTool = { name -> runOnUiThread { addToolMsg(name) } },
            onPartial = { partial -> runOnUiThread { thinking.text = partial } },
            onResult = { reply ->
                runOnUiThread {
                    thinking.text = reply
                    sendBtn.isEnabled = true
                    SoundManager.play(if (reply.startsWith("（")) "failed" else "celebrating")
                    TtsManager.speak(applicationContext, reply)
                    // 升级放礼花
                    val lvAfter = LlmClient.levelInfo().first
                    if (lvAfter > lvBefore) {
                        SoundManager.play("celebrating")
                        val (_, lvTitle) = LlmClient.levelInfo()
                        Toast.makeText(this@ChatActivity,
                            "🎉 亲密度升到 Lv.$lvAfter（$lvTitle）！", Toast.LENGTH_LONG).show()
                        titleText.text = "✦ 你的史莱姆  Lv.$lvAfter $lvTitle"
                    }
                }
                // 后台跑人格漂移，完事刷新坐标显示
                LlmClient.drift(applicationContext, text, reply) {
                    runOnUiThread { refreshPersonalityLine() }
                }
            })
    }
}
