package com.cinemaroya.studio.data.video

import android.content.Context
import com.cinemaroya.studio.background.BackgroundWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/** Free text-to-video engine = a Gradio Space on Hugging Face. Placeholders: {{prompt}} {{negative}} {{image}} {{seed}}. */
data class ProviderConfig(
    val spaceUrl: String,
    val apiName: String,
    val dataTemplate: String,
    val hfToken: String,
    val negativePrompt: String
)

object VideoEngineStore {
    // Editable defaults: free Spaces change, sleep or hit GPU quota. Use the in-app search to find a live one.
    const val DEFAULT_URL = "https://kingnish-ltx-video-distilled.hf.space"
    const val DEFAULT_API = "generate"
    const val DEFAULT_TEMPLATE = """["{{prompt}}", {{image}}, null, null, 3]"""
    const val DEFAULT_NEGATIVE = "worst quality, blurry, jittery, distorted"

    private var appContext: Context? = null
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val sp get() = appContext?.getSharedPreferences("video_engine", Context.MODE_PRIVATE)

    fun load() = ProviderConfig(
        spaceUrl = sp?.getString("spaceUrl", DEFAULT_URL) ?: DEFAULT_URL,
        apiName = sp?.getString("apiName", DEFAULT_API) ?: DEFAULT_API,
        dataTemplate = sp?.getString("template", DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE,
        hfToken = sp?.getString("token", "") ?: "",
        negativePrompt = sp?.getString("negative", DEFAULT_NEGATIVE) ?: DEFAULT_NEGATIVE
    )

    fun save(c: ProviderConfig) {
        sp?.edit()
            ?.putString("spaceUrl", c.spaceUrl.trim())
            ?.putString("apiName", c.apiName.trim())
            ?.putString("template", c.dataTemplate)
            ?.putString("token", c.hfToken.trim())
            ?.putString("negative", c.negativePrompt)
            ?.apply()
    }

    fun saveToken(token: String) = save(load().copy(hfToken = token))

    /** When on, exporting a project generates a real video clip for every scene that has none. */
    var useInExport: Boolean
        get() = sp?.getBoolean("use_in_export", true) ?: true
        set(v) {
            sp?.edit()?.putBoolean("use_in_export", v)?.apply()
        }
}

data class ClipUi(
    val running: Boolean = false,
    val status: String = "",
    val path: String? = null,
    val error: String? = null
)

/** Generates one video clip per scene in an app-wide scope, so it survives screen changes and runs in the background. */
object VideoClipManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = GradioClient()
    private var appContext: Context? = null

    private val _clips = MutableStateFlow<Map<String, ClipUi>>(emptyMap())
    val clips: StateFlow<Map<String, ClipUi>> = _clips

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun existingClip(sceneId: String): String? {
        val ctx = appContext ?: return null
        val f = File(File(ctx.filesDir, "clips"), "${safe(sceneId)}.mp4")
        return if (f.exists() && f.length() > 0) f.absolutePath else null
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun set(id: String, ui: ClipUi) = _clips.update { it + (id to ui) }

    private suspend fun produce(ctx: Context, sceneId: String, prompt: String): String {
        val cfg = VideoEngineStore.load()
        val seed = Random.nextInt(0, 1_000_000)
        fun esc(s: String): String = JSONObject.quote(s).let { it.substring(1, it.length - 1) }
        val filled = cfg.dataTemplate
            .replace("{{prompt}}", esc(prompt))
            .replace("{{negative}}", esc(cfg.negativePrompt))
            .replace("{{image}}", "null")
            .replace("{{seed}}", seed.toString())
        val data = try {
            JSONArray(filled)
        } catch (e: Exception) {
            throw IllegalArgumentException("قالب البيانات ليس JSON صالحاً. افتح إعدادات محرك الفيديو واضغط «اكتشاف تلقائي».")
        }
        set(sceneId, ClipUi(running = true, status = "المحرك يولّد المقطع… قد يستغرق دقائق"))
        val result = client.call(cfg, data)
        set(sceneId, ClipUi(running = true, status = "تحميل المقطع…"))
        val dir = File(ctx.filesDir, "clips").apply { mkdirs() }
        val target = File(dir, "${safe(sceneId)}.mp4")
        val tmp = File(dir, "${safe(sceneId)}.tmp")
        client.download(cfg, result, tmp)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        set(sceneId, ClipUi(status = "تم توليد المقطع", path = target.absolutePath))
        return target.absolutePath
    }

    fun generate(sceneId: String, prompt: String) {
        val ctx = appContext ?: return
        if (_clips.value[sceneId]?.running == true) return
        if (prompt.isBlank()) {
            set(sceneId, ClipUi(error = "اكتب وصف المشهد أولاً"))
            return
        }
        set(sceneId, ClipUi(running = true, status = "إرسال الطلب إلى محرك الفيديو…", path = existingClip(sceneId)))
        scope.launch {
            try {
                BackgroundWork.keepAlive("توليد مقطع فيديو") { produce(ctx, sceneId, prompt) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set(sceneId, ClipUi(error = e.message ?: "فشل توليد المقطع", path = existingClip(sceneId)))
            }
        }
    }

    /** Used by the exporter: returns the clip path (existing or freshly generated), or null if the engine failed. */
    suspend fun ensureClip(sceneId: String, prompt: String, timeoutMs: Long = 300_000L): String? {
        existingClip(sceneId)?.let { return it }
        val ctx = appContext ?: return null
        return try {
            withContext(Dispatchers.IO) { withTimeoutOrNull(timeoutMs) { produce(ctx, sceneId, prompt) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            set(sceneId, ClipUi(error = e.message ?: "فشل توليد المقطع"))
            null
        }
    }
}

/** Detects API keys by their prefix so every key can be pasted at once. Returns a short summary. */
object KeyImporter {
    fun importFrom(text: String): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        Regex("""[A-Za-z0-9_\-]{20,}""").findAll(text).forEach { m ->
            val t = m.value
            when {
                t.startsWith("gsk_") -> found += "groq" to t
                t.startsWith("sk-or-") -> found += "openrouter" to t
                t.startsWith("hf_") -> found += "hf" to t
            }
        }
        return found
    }
}
