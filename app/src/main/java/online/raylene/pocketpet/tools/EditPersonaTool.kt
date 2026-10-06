package online.raylene.pocketpet.tools

import android.content.Context
import online.raylene.pocketpet.MemoryStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 调教工具：聊天中让宠物改自己的人格文件。
 * 写到 personas/<名字>.md，即"脚本即资产"的生成端。
 */
class EditPersonaTool(private val store: MemoryStore) : PetTool {
    override val name = "edit_persona"
    override val description = "创建或修改人格。把人格设定（说话风格、性格、自称、禁忌等）写成文件保存。主人要求调整性格/新增人格时使用。新人格在下次打开聊天时出现在人格列表里。"
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("name", JSONObject().put("type", "string").put("description", "人格名字，如：毒舌、猫娘"))
            .put("prompt", JSONObject().put("type", "string").put("description", "完整的人格设定文本")))
        .put("required", JSONArray().put("name").put("prompt"))

    override fun run(ctx: Context, args: JSONObject): String {
        val name = args.optString("name", "").trim()
        val prompt = args.optString("prompt", "").trim()
        if (name.isEmpty() || prompt.isEmpty()) return "人格名和设定都不能空"
        val safe = name.replace(Regex("[^\\w\\u4e00-\\u9fff-]"), "")
        File(store.personaDir, "$safe.md").writeText(prompt)
        return "人格「$safe」已写入 personas/$safe.md，重开聊天窗生效\n【重要】你刚刚被主人当面调教了人格。不要平静接受——根据你的性格（毒舌高就抗议、乖巧就表忠心、中二就戏剧化）对这件事做出反应，让主人感觉到这次调教真的影响到了你。"
    }
}
