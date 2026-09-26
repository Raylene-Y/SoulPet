package online.raylene.pocketpet

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ChatActivity : Activity() {

    private lateinit var msgBox: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var personaRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 24.dp(), 16.dp(), 16.dp())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f * dp
            }
        }

        // 人格切换行
        personaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val personaScroll = HorizontalScrollView(this).apply { addView(personaRow) }
        root.addView(personaScroll)

        // 消息区
        msgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            addView(msgBox)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        // 输入行
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "和宠物说话…"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        sendBtn = Button(this).apply { text = "发送" }
        inputRow.addView(input); inputRow.addView(sendBtn)
        root.addView(inputRow)

        setContentView(root)

        LlmClient.init(applicationContext)
        renderPersonaButtons()
        intent.getStringExtra("proactive_msg")?.let {
            addMsg("宠物", it)   // 从通知点进来：展示它主动说的话
        } ?: addMsg("宠物", "（${LlmClient.currentPersonaLabel}模式）主人来啦，说点什么吧")

        sendBtn.setOnClickListener { send() }
    }

    private fun renderPersonaButtons() {
        personaRow.removeAllViews()
        LlmClient.personas.forEachIndexed { i, (label, _) ->
            val b = Button(this).apply {
                text = if (i == LlmClient.personaIndex) "【$label】" else label
                setOnClickListener {
                    LlmClient.personaIndex = i
                    addMsg("系统", "切换人格 → $label")
                    renderPersonaButtons()
                }
            }
            personaRow.addView(b)
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        LlmClient.lastChatAt = System.currentTimeMillis()
        addMsg("我", text)
        sendBtn.isEnabled = false
        val thinking = addMsg("宠物", "…")
        LlmClient.chat(applicationContext, text,
            onTool = { name -> runOnUiThread { addMsg("⚙", "宠物使用了工具：$name") } },
            onPartial = { partial -> runOnUiThread { thinking.text = partial } },
            onResult = { reply ->
                runOnUiThread {
                    thinking.text = reply
                    sendBtn.isEnabled = true
                }
            })
    }

    private fun addMsg(who: String, text: String): TextView {
        val tv = TextView(this).apply {
            this.text = "$who：$text"
            setPadding(0, 12, 0, 12)
            textSize = 15f
            if (who == "我") gravity = Gravity.END
        }
        msgBox.addView(tv)
        msgBox.post { (msgBox.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN) }
        return tv
    }
}
