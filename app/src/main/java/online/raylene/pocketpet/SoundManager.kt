package online.raylene.pocketpet

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import org.json.JSONObject
import java.io.File

/** CoPet 兼容音效包：sounds/<pack>/sound.json + mp3。SoundPool 低延迟播放 */
object SoundManager {
    private var pool: SoundPool? = null
    private val sounds = HashMap<String, Int>()   // event -> soundId
    private var loaded = false

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val packDir = File(ctx.filesDir, "pet/sounds/copet")
        val jsonFile = File(packDir, "sound.json")
        if (!jsonFile.exists()) return
        try {
            val json = JSONObject(jsonFile.readText())
            pool = SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                ).build()
            val map = mutableMapOf<String, String>()
            json.optJSONObject("interactionSounds")?.let { o ->
                for (k in o.keys()) map[k] = o.getString(k)
            }
            json.optJSONObject("agentSounds")?.let { o ->
                for (k in o.keys()) map[k] = o.getString(k)
            }
            for ((event, file) in map) {
                val f = File(packDir, file)
                if (f.exists()) sounds[event] = pool!!.load(f.absolutePath, 1)
            }
        } catch (e: Exception) { /* 音效坏了不致命 */ }
    }

    fun play(event: String) {
        val id = sounds[event] ?: return
        pool?.play(id, 0.8f, 0.8f, 1, 0, 1f)
    }
}
