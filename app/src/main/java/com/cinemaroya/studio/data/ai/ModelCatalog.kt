package com.cinemaroya.studio.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Live model discovery: per-key model lists and OpenRouter's public list of free models. */
object ModelCatalog {

    data class FreeModel(val id: String, val contextLength: Int)

    private fun get(url: String, key: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            if (key.isNotEmpty()) setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiEngineException("HTTP $code ${raw.take(120)}", code)
            return raw
        } finally {
            conn.disconnect()
        }
    }

    /** Models the given key can use. OpenRouter results are limited to free models. */
    suspend fun listModels(engine: AiEngine, key: String, customBase: String = ""): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(engine.isExternal) { "Gemini يُدار عبر Firebase ولا يحتاج قائمة" }
                val chatUrl = if (engine.custom) {
                    val b = customBase.trim().trimEnd('/')
                    require(b.isNotEmpty()) { "أدخل عنوان الخادم أولاً" }
                    if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
                } else engine.endpoint
                if (!engine.custom && engine != AiEngine.OPENROUTER) {
                    require(key.isNotBlank()) { "أدخل المفتاح أولاً" }
                }
                val raw = get(chatUrl.replace("/chat/completions", "/models"), key.trim())
                parse(JSONObject(raw), freeOnly = engine == AiEngine.OPENROUTER)
            }
        }

    /** Public OpenRouter catalogue (no key needed), filtered to models that cost nothing, longest context first. */
    suspend fun openRouterFree(): Result<List<FreeModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(get("https://openrouter.ai/api/v1/models", ""))
            val arr = j.optJSONArray("data") ?: JSONArray()
            val out = ArrayList<FreeModel>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank() || !isFree(o, id)) continue
                out += FreeModel(id, o.optInt("context_length", 0))
            }
            out.distinctBy { it.id }.sortedByDescending { it.contextLength }
        }
    }

    private fun parse(j: JSONObject, freeOnly: Boolean): List<String> {
        val arr = j.optJSONArray("data") ?: j.optJSONArray("models") ?: JSONArray()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { o.optString("name") }
            if (id.isBlank()) continue
            if (freeOnly && !isFree(o, id)) continue
            out += id
        }
        return out.distinct().sorted()
    }

    private fun isFree(o: JSONObject, id: String): Boolean {
        if (id.endsWith(":free")) return true
        val p = o.optJSONObject("pricing") ?: return false
        return p.optString("prompt") == "0" && p.optString("completion") == "0"
    }
}
