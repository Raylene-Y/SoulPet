package online.raylene.pocketpet.tools

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONObject

object ClipboardTool : PetTool {
    override val name = "read_clipboard"
    override val description = "读取用户剪贴板里的文本内容"
    override val parameters = emptyParams()

    override fun run(ctx: Context, args: JSONObject): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0 ||
            !cm.primaryClipDescription!!.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return "剪贴板是空的"
        }
        val text = clip.getItemAt(0).text?.toString()?.take(500) ?: "剪贴板内容读不出来"
        return "剪贴板内容：$text"
    }
}
