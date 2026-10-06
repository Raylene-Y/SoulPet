package online.raylene.pocketpet.tools

import android.content.Context
import online.raylene.pocketpet.MemoryStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 技能教学：主人教宠物"说法 → 命令"的映射，存进 skills.md（养成资产，可克隆分享）。
 * 教过的技能进 system prompt，之后主人再说这个词，宠物直接 termux_run 执行，不现编。
 */
class TeachSkillTool(private val store: MemoryStore) : PetTool {
    override val name = "teach_skill"
    override val description = "学会一个技能：把主人说的词和 Termux 命令绑定保存。主人说\"记住/以后我说X就是Y\"时使用。已学技能下次直接执行，不要重复教学。"
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("name", JSONObject().put("type", "string").put("description", "主人会说的词，如：拍照、定位、手电"))
            .put("command", JSONObject().put("type", "string").put("description", "对应的 Termux 命令")))
        .put("required", JSONArray().put("name").put("command"))

    override fun run(ctx: Context, args: JSONObject): String {
        val name = args.optString("name", "").trim()
        val command = args.optString("command", "").trim()
        if (name.isEmpty() || command.isEmpty()) return "技能名和命令都不能空"
        // 同名覆盖（重教 = 升级技能）
        val lines = store.skillLines().filter { !it.removePrefix("- ").substringBefore("→").trim().equals(name, true) }
        store.rewriteSkills(lines)
        store.addSkill(name, command)
        return "技能「$name」已学会（→ $command），存进 skills.md。下次主人说这个词直接执行。\n【重要】你刚学会了一个新技能——用自己的性格表达一下学会新东西的心情，别太平静。"
    }
}
