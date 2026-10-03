package online.raylene.pocketpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * 程序化像素史莱姆。无素材依赖，代码画帧。
 * 帧格：32x32 逻辑像素，scale 放大到实际尺寸（滤镜关闭保持像素感）。
 */
object SlimeFrames {
    const val W = 32
    const val H = 32

    private val body = Paint().apply { color = Color.rgb(90, 200, 250); isAntiAlias = false }
    private val bodyDark = Paint().apply { color = Color.rgb(50, 160, 220); isAntiAlias = false }
    private val eye = Paint().apply { color = Color.rgb(30, 40, 60); isAntiAlias = false }
    private val blush = Paint().apply { color = Color.rgb(255, 150, 170); isAntiAlias = false }
    private val shine = Paint().apply { color = Color.argb(180, 255, 255, 255); isAntiAlias = false }

    enum class Pose { IDLE_A, IDLE_B, SQUISH, HAPPY, SLEEP }

    fun draw(pose: Pose, scale: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W * scale, H * scale, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = scale.toFloat()
        fun px(x: Int, y: Int, w: Int = 1, h: Int = 1, paint: Paint) {
            c.drawRect(x * p, y * p, (x + w) * p, (y + h) * p, paint)
        }

        // 身体轮廓随姿态变化（squish = 压扁帧，idle_b = 拉伸帧）
        val (top, bottom, left, right) = when (pose) {
            Pose.IDLE_A -> intArrayOf(10, 27, 6, 26)
            Pose.IDLE_B -> intArrayOf(8, 27, 7, 25)
            Pose.SQUISH -> intArrayOf(14, 27, 4, 28)
            Pose.HAPPY -> intArrayOf(9, 27, 6, 26)
            Pose.SLEEP -> intArrayOf(13, 27, 5, 27)
        }
        // 圆角身体：逐行画
        for (y in top..bottom) {
            val edge = when {
                y < top + 3 -> 3 - (y - top)          // 顶部收圆
                y > bottom - 2 -> 1                    // 底部略收
                else -> 0
            }
            px(left + edge, y, right - left - edge * 2, 1, body)
        }
        // 底部暗色描边
        px(left + 1, bottom, right - left - 2, 1, bodyDark)
        // 高光
        px(left + 4, top + 3, 3, 2, shine)

        // 眼睛（随姿态）
        val eyeY = top + 8
        when (pose) {
            Pose.HAPPY -> { // ^ ^ 闭眼笑
                px(11, eyeY, 3, 1, eye); px(18, eyeY, 3, 1, eye)
                px(11, eyeY + 1, 1, 1, eye); px(20, eyeY + 1, 1, 1, eye)
            }
            Pose.SQUISH -> {
                px(11, eyeY + 1, 3, 3, eye); px(18, eyeY + 1, 3, 3, eye)
            }
            Pose.SLEEP -> { // — — 闭眼睡觉
                px(11, eyeY + 2, 3, 1, eye); px(18, eyeY + 2, 3, 1, eye)
            }
            else -> {
                px(11, eyeY, 3, 4, eye); px(18, eyeY, 3, 4, eye)
                px(12, eyeY, 1, 1, shine); px(19, eyeY, 1, 1, shine)
            }
        }
        // 腮红 + 嘴
        px(8, eyeY + 5, 3, 1, blush); px(21, eyeY + 5, 3, 1, blush)
        px(15, eyeY + 5, 2, 1, eye)
        return bmp
    }

    fun flip(src: Bitmap): Bitmap {
        val m = android.graphics.Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }
}
