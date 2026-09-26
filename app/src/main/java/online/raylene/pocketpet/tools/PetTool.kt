package online.raylene.pocketpet.tools

import android.content.Context
import org.json.JSONObject

/** 一个工具 = schema + 执行体。加新工具：新建实例丢进 PetTools.ALL */
interface PetTool {
    val name: String
    val description: String
    val parameters: JSONObject  // JSON Schema；无参就空 object
    fun run(ctx: Context, args: JSONObject): String

    fun toSchema(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters))
}

fun emptyParams() = JSONObject().put("type", "object").put("properties", JSONObject())
