package online.raylene.pocketpet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/**
 * CoPet/Codex 兼容宠物包：pet.json + spritesheet.webp
 * 网格：frameWidth×frameHeight 一帧，gridColumns 列（每帧一列），行 = 状态。
 * 行序（v1，9 行）：idle, running-right, running-left, waving, jumping, failed, waiting, running, review
 */
class PetPackage(val dir: File) {
    val id: String
    val displayName: String
    private val frameW: Int
    private val frameH: Int
    private val cols: Int
    private val rows: Int
    private val sheet: Bitmap

    enum class Row(val index: Int) {
        IDLE(0), RUN_RIGHT(1), RUN_LEFT(2), WAVING(3), JUMPING(4),
        FAILED(5), WAITING(6), RUNNING(7), REVIEW(8)
    }

    init {
        val manifest = JSONObject(File(dir, "pet.json").readText())
        id = manifest.getString("id")
        displayName = manifest.optString("displayName", id)
        frameW = manifest.optInt("frameWidth", 192)
        frameH = manifest.optInt("frameHeight", 208)
        cols = manifest.optInt("gridColumns", 8)
        rows = manifest.optInt("gridRows",
            if (manifest.optInt("spriteVersionNumber") == 2) 11 else 9)
        val spriteFile = File(dir, "spritesheet.webp").takeIf { it.exists() }
            ?: File(dir, "spritesheet.png")
        sheet = BitmapFactory.decodeFile(spriteFile.absolutePath)
            ?: throw IllegalStateException("spritesheet 解码失败")
    }

    // 每行有效帧数（剔除全透明空格，防止播放时闪没）
    private val validFrames = HashMap<Int, Int>()

    private fun validCount(row: Int): Int = validFrames.getOrPut(row) {
        var lastValid = -1
        for (c in 0 until cols) {
            if (!isBlankFrame(row, c)) lastValid = c
        }
        lastValid + 1
    }.coerceAtLeast(1)

    private fun isBlankFrame(row: Int, col: Int): Boolean {
        // 抽样检测：每 12px 取一个像素看 alpha
        var x = col * frameW
        while (x < (col + 1) * frameW) {
            var y = row * frameH
            while (y < (row + 1) * frameH) {
                if (sheet.getPixel(x, y) ushr 24 > 16) return false
                y += 12
            }
            x += 12
        }
        return true
    }

    fun frame(row: Row, frameIndex: Int): Bitmap {
        val r = row.index.coerceAtMost(rows - 1)
        val col = frameIndex % validCount(r)
        return Bitmap.createBitmap(sheet, col * frameW, r * frameH, frameW, frameH)
    }

    val frameCount get() = cols

    companion object {
        /** 扫描 filesDir/pet/pets/ 下所有宠物包 */
        fun scanAll(petsRoot: File): List<PetPackage> {
            if (!petsRoot.exists()) return emptyList()
            return petsRoot.listFiles { f -> f.isDirectory }
                ?.mapNotNull { d ->
                    try { if (File(d, "pet.json").exists()) PetPackage(d) else null }
                    catch (e: Exception) { null }
                } ?: emptyList()
        }
    }
}
