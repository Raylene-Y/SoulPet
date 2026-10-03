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
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "pet_reply")
    }

    @Synchronized
    fun stop() { tts?.stop() }
}
