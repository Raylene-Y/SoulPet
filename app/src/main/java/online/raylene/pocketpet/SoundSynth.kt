package online.raylene.pocketpet

import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** 程序化 8-bit 音效合成器：正弦滑音 + 指数衰减包络，生成 WAV */
object SoundSynth {
    private const val SR = 22050

    /** 一条滑音：freq 从 f0 滑到 f1，durMs 时长，指数衰减 */
    private fun chirp(f0: Double, f1: Double, durMs: Int, vol: Double = 0.5): ShortArray {
        val n = SR * durMs / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * t
            phase += 2 * PI * f / SR
            val env = exp(-3.0 * t)                       // 指数衰减
            out[i] = (sin(phase) * env * vol * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 多条滑音拼接 */
    private fun seq(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var off = 0
        for (p in parts) { p.copyInto(out, off); off += p.size }
        return out
    }

    private fun writeWav(samples: ShortArray, file: File) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort(s)
        val pcm = data.array()
        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()); header.putInt(36 + pcm.size)
            header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
            header.putInt(16); header.putShort(1); header.putShort(1)   // PCM mono
            header.putInt(SR); header.putInt(SR * 2); header.putShort(2); header.putShort(16)
            header.put("data".toByteArray()); header.putInt(pcm.size)
            fos.write(header.array()); fos.write(pcm)
        }
    }

    /** 生成整套 retro 音效包到指定目录（幂等：存在则跳过） */
    fun generateRetroPack(dir: File) {
        dir.mkdirs()
        val defs = mapOf(
            // 点击：短促上挑 "哔"
            "click" to chirp(880.0, 1320.0, 90, 0.4),
            // 拖拽落地：下滑 "咻呜"
            "dragLand" to chirp(1200.0, 500.0, 200, 0.45),
            // 思考：两声低哼 "嗯…嗯…"
            "thinking" to seq(chirp(440.0, 460.0, 120, 0.35), chirp(420.0, 440.0, 140, 0.3)),
            // 成功：上行琶音 "叮咚叮"
            "celebrating" to seq(chirp(660.0, 660.0, 90, 0.4), chirp(880.0, 880.0, 90, 0.4), chirp(1320.0, 1320.0, 160, 0.45)),
            // 失败：下行滑音 "呜哇"
            "failed" to chirp(500.0, 200.0, 350, 0.45),
            // 主动找你：好奇双响 "哔哔"
            "inspecting" to seq(chirp(990.0, 1100.0, 80, 0.4), chirp(1100.0, 1240.0, 90, 0.42)),
            // 被摸：呼噜般的三连低鸣
            "petted" to seq(chirp(300.0, 340.0, 100, 0.35), chirp(320.0, 300.0, 100, 0.35), chirp(300.0, 340.0, 120, 0.35))
        )
        for ((name, samples) in defs) {
            val f = File(dir, "$name.wav")
            if (!f.exists()) writeWav(samples, f)
        }
        // 音效清单（CoPet sound.json 格式，但用 .wav）
        File(dir, "sound.json").writeText(JSONObject().apply {
            put("id", "retro"); put("displayName", "8-bit Retro"); put("schemaVersion", 1)
            put("interactionSounds", JSONObject()
                .put("click", "click.wav").put("petted", "petted.wav").put("dragLand", "dragLand.wav"))
            put("agentSounds", JSONObject()
                .put("thinking", "thinking.wav").put("celebrating", "celebrating.wav")
                .put("failed", "failed.wav").put("inspecting", "inspecting.wav"))
        }.toString(2))
    }
}
