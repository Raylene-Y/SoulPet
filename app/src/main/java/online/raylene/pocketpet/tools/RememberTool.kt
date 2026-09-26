package online.raylene.pocketpet.tools

import android.content.Context
import online.raylene.pocketpet.MemoryStore
import org.json.JSONObject

/** 宠物主动记住关于主人的事实，写进 memory.md（长期记忆） */
class RememberTool(private val store: MemoryStore) : PetTool {
    override val name = "remember"
    override val description = "把关于主人的重要事实写进长期记忆（偏好、习惯、重要事件、名字、约定等）。值得以后记住的才存。"
    override val parameters = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().put("fact", JSONObject()
            .put("type", "string")
            .put("description", "要记住的事实，一句话")))
        .put("required", listOf("fact").fold(org.json.JSONArray()) { a, s -> a.put(s) })

    override fun run(ctx: Context, args: JSONObject): String {
        val fact = args.optString("fact", "").trim()
        if (fact.isEmpty()) return "没什么好记的"
        store.appendMemory(fact)
        return "已记住：$fact"
    }
}
