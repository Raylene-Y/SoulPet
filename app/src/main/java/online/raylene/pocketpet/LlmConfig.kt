package online.raylene.pocketpet

import android.content.Context

/** LLM 配置：用户设置优先，BuildConfig 默认兜底（开发自用） */
object LlmConfig {
    private const val PREFS = "llm"

    fun baseUrl(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("base_url", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_BASE_URL

    fun apiKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("api_key", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_API_KEY

    fun model(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("model", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_MODEL

    fun save(ctx: Context, baseUrl: String, apiKey: String, model: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("base_url", baseUrl.trim())
            .putString("api_key", apiKey.trim())
            .putString("model", model.trim())
            .apply()
    }

    fun isConfigured(ctx: Context): Boolean = apiKey(ctx).isNotBlank()
}
