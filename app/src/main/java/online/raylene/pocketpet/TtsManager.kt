package online.raylene.pocketpet

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 原生 TTS，懒初始化。朗读宠物的完整回复 */
object TtsManager : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    var enabled = true

    @Synchronized
    fun init(ctx: Context) {
        if (tts == null) tts = TextToSpeech(ctx.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.CHINA)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    @Synchronized
    fun speak(ctx: Context, text: String) {
        if (!enabled) return
        init(ctx)
        if (!ready) return
        // 清理不适合朗读的字符（markdown/调试痕迹）
        val clean = text
            .replace(Regex("</?think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[*#`>_~]"), "")
            .trim()
        if (clean.isEmpty()) return
        // 拷贝一份交给队列，speak 本身不阻塞
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "pet_reply")
    }

    /** 提前到 app 启动时初始化（主线程，启动期卡顿无所谓，聊天时才要顺） */
    fun warmUp(ctx: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { init(ctx) }
    }

    @Synchronized
    fun stop() { tts?.stop() }
}
