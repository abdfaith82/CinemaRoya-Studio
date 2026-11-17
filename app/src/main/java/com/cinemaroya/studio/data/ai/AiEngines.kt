package com.cinemaroya.studio.data.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Text engines the user can choose from. Gemini runs through Firebase AI; the others are free OpenAI-compatible APIs. */
enum class AiEngine(
    val code: String,
    val label: String,
    val endpoint: String,
    val defaultModel: String,
    val keyHint: String
) {
    GEMINI("gemini", "Gemini (Firebase)", "", "gemini-3.8-flash", ""),
    GROQ(
        "groq", "Groq (سريع ومجاني)",
        "https://api.groq.com/openai/v1/chat/completions",
        "llama-3.3-70b-versatile", "console.groq.com/keys"
    ),
    OPENROUTER(
        "openrouter", "OpenRouter (نماذج مجانية)",
        "https://openrouter.ai/api/v1/chat/completions",
        "meta-llama/llama-3.3-70b-instruct:free", "openrouter.ai/keys"
    );

    val isExternal: Boolean get() = this != GEMINI

    companion object {
        fun fromCode(code: String?): AiEngine = entries.firstOrNull { it.code == code } ?: GEMINI
    }
}

object AiEngineSettings {
    private const val PREFS_NAME = "ai_engine_settings"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var engine: AiEngine
        get() = AiEngine.fromCode(prefs?.getString("engine", null))
        set(value) {
            prefs?.edit()?.putString("engine", value.code)?.apply()
        }

    var autoFallback: Boolean
        get() = prefs?.getBoolean("auto_fallback", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("auto_fallback", value)?.apply()
        }

    fun apiKey(engine: AiEngine): String =
        prefs?.getString("key_${engine.code}", "").orEmpty().trim()

    fun setApiKey(engine: AiEngine, value: String) {
        prefs?.edit()?.putString("key_${engine.code}", value.trim())?.apply()
    }

    fun model(engine: AiEngine): String {
        val saved = prefs?.getString("model_${engine.code}", null)?.trim()
        return if (saved.isNullOrEmpty()) engine.defaultModel else saved
    }

    fun setModel(engine: AiEngine, value: String) {
        prefs?.edit()?.putString("model_${engine.code}", value.trim())?.apply()
    }

    /** Gemini is always available; external engines need an API key. */
    fun isAvailable(engine: AiEngine): Boolean = !engine.isExternal || apiKey(engine).isNotEmpty()
}

class AiEngineException(message: String) : IOException(message)

object ExternalAiClient {

    suspend fun chat(engine: AiEngine, prompt: String, jsonMode: Boolean): String =
        withContext(Dispatchers.IO) {
            val key = AiEngineSettings.apiKey(engine)
            if (key.isEmpty()) throw AiEngineException("لم يتم إدخال مفتاح ${engine.label}")

            val body = JSONObject().apply {
                put("model", AiEngineSettings.model(engine))
                put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                )
                put("temperature", 0.8)
                if (jsonMode && engine == AiEngine.GROQ) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }

            val conn = (URL(engine.endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
                if (engine == AiEngine.OPENROUTER) {
                    setRequestProperty("HTTP-Referer", "https://github.com/abdfaith82/CinemaRoya-Studio")
                    setRequestProperty("X-Title", "CinemaRoya Studio")
                }
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw AiEngineException("${engine.label}: HTTP $code ${shortError(text)}")
                }
                val content = JSONObject(text)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isBlank()) throw AiEngineException("${engine.label}: ردّ فارغ")
                content
            } finally {
                conn.disconnect()
            }
        }

    /** Pulls the JSON object out of a reply that may be wrapped in code fences or extra prose. */
    fun extractJsonObject(raw: String): String {
        val s = raw.trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun shortError(body: String): String {
        val fromJson = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return (fromJson?.takeIf { it.isNotBlank() } ?: body).take(180)
    }
}
