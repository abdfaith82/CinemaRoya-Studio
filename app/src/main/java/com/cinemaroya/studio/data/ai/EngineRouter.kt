package com.cinemaroya.studio.data.ai

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class RouteStrategy(val code: String, val label: String) {
    SELECTED("selected", "المختار أولاً"),
    BALANCED("balanced", "متوازن"),
    FASTEST("fastest", "الأسرع"),
    RELIABLE("reliable", "الأوثق"),
    SMARTEST("smartest", "الأذكى");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: SELECTED
    }
}

/**
 * Smart routing between engines (ported from Claude Agents Team):
 * - exponential back-off with jitter after consecutive failures, Retry-After honoured;
 * - exhausted daily quota => rest until UTC midnight;
 * - rejected key / unknown model (401/403/404) => engine disabled until the key is changed;
 * - latency (EWMA) and success ratio feed the Fastest / Most reliable / Smartest strategies.
 */
object EngineRouter {
    class Health {
        var failures = 0
        var failuresTotal = 0
        var successes = 0
        var cooldownUntil = 0L
        var lastStart = 0L
        var disabled = false
        var note = ""
        var latencyMs = 0.0
    }

    private const val MAX_COOLDOWN_MS = 600_000L
    private const val DAY_MS = 86_400_000L
    private val DAILY = Regex("""(?i)(per[ -]day|daily|requests per day|\bRPD\b|tokens per day|\bTPD\b)""")
    private val health = ConcurrentHashMap<AiEngine, Health>()

    private fun h(e: AiEngine) = health.getOrPut(e) { Health() }
    private fun now() = System.currentTimeMillis()
    private fun reliability(x: Health) = (x.successes + 1.0) / (x.successes + x.failuresTotal + 2.0)

    fun note(e: AiEngine): String {
        val x = h(e)
        return when {
            x.disabled -> x.note
            x.cooldownUntil > now() -> x.note
            else -> ""
        }
    }

    /** Called when the user changes a key, so a previously rejected engine gets a fresh start. */
    fun reset(e: AiEngine) {
        health[e] = Health()
    }

    fun resetAll() = health.clear()

    fun order(candidates: List<AiEngine>, selected: AiEngine, strategy: RouteStrategy): List<AiEngine> {
        val t = now()
        val alive = candidates.filter { !h(it).disabled }
        val ready = alive.filter { h(it).cooldownUntil <= t }
        val cooling = alive.filter { h(it).cooldownUntil > t }.sortedBy { h(it).cooldownUntil }
        val sorted = when (strategy) {
            RouteStrategy.SELECTED -> ready.sortedWith(compareBy({ it != selected }, { h(it).lastStart }))
            RouteStrategy.BALANCED -> ready.sortedBy { h(it).lastStart }
            RouteStrategy.FASTEST -> ready.sortedBy { if (h(it).latencyMs > 0) h(it).latencyMs else 1_000.0 }
            RouteStrategy.RELIABLE -> ready.sortedByDescending { reliability(h(it)) }
            RouteStrategy.SMARTEST -> ready.sortedByDescending {
                score(reliability(h(it)), speed(h(it).latencyMs), intelligence(AiEngineSettings.model(it)))
            }
        }
        return sorted + cooling
    }

    fun markStart(e: AiEngine) {
        h(e).lastStart = now()
    }

    fun onSuccess(e: AiEngine, ms: Long) {
        h(e).apply {
            failures = 0
            successes++
            note = ""
            cooldownUntil = 0L
            if (ms > 0) latencyMs = if (latencyMs == 0.0) ms.toDouble() else latencyMs * 0.7 + ms * 0.3
        }
    }

    fun onFailure(e: AiEngine, error: Exception) {
        val x = h(e)
        val msg = error.message.orEmpty()
        var code = (error as? AiEngineException)?.code ?: 0
        if (code == 0 && (msg.contains("quota", true) || msg.contains("429"))) code = 429
        val retryAfter = (error as? AiEngineException)?.retryAfterMs
        val detail = msg.replace('\n', ' ').take(90)

        // Gemini (Firebase) is never disabled: its errors are usually quota / App Check and transient.
        if (e.isExternal && (code == 401 || code == 403 || code == 404)) {
            x.disabled = true
            x.note = (if (code == 404) "نموذج أو مسار غير صالح" else "المفتاح مرفوض") + " [$code]"
            return
        }
        x.failures++
        x.failuresTotal++
        if (code == 429 && DAILY.containsMatchIn(msg)) {
            val t = now()
            x.cooldownUntil = (t / DAY_MS + 1) * DAY_MS
            x.note = "نفدت الحصة اليومية، تتجدد منتصف الليل UTC"
            return
        }
        val cooldown = if (retryAfter != null && retryAfter > 0) {
            min(retryAfter, MAX_COOLDOWN_MS)
        } else {
            val base = when {
                code == 429 -> 30_000L
                code in 500..599 -> 15_000L
                else -> 5_000L
            }
            min((base * (1L shl min(x.failures - 1, 6)) * (0.8 + Random.nextDouble() * 0.4)).toLong(), MAX_COOLDOWN_MS)
        }
        x.cooldownUntil = now() + cooldown
        x.note = "راحة ${max(cooldown / 1000, 1)} ث $detail".trim()
    }

    // ---- scoring (reliability, speed, intelligence) ----
    private fun speed(latencyMs: Double): Int = when {
        latencyMs <= 0.0 -> 50
        latencyMs <= 300.0 -> 100
        latencyMs >= 8000.0 -> 0
        else -> (100 * (1 - ln(latencyMs / 300.0) / ln(8000.0 / 300.0))).roundToInt()
    }

    private val PARAMS = Regex("""(\d+(?:\.\d+)?)\s*b\b""")

    private fun intelligence(model: String): Int {
        val m = model.lowercase()
        val params = PARAMS.findAll(m).mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull()
        val tokens = m.split(Regex("[^a-z0-9]+")).toSet()
        val base = when {
            params != null && params > 0 -> 25 + 11 * log2(params)
            listOf("haiku", "flash", "mini", "lite", "small", "nano", "instant").any { it in tokens } -> 65.0
            "gpt-5" in m || listOf("opus", "ultra", "pro").any { it in tokens } -> 92.0
            "gpt-4" in m || "gemini" in m || listOf("sonnet", "large").any { it in tokens } -> 85.0
            else -> 60.0
        }
        return base.coerceIn(10.0, 100.0).roundToInt()
    }

    private fun score(reliability: Double, speed: Int, intelligence: Int): Double =
        (0.2 * reliability * 100 + 0.1 * speed + 0.7 * intelligence) / 100.0
}
