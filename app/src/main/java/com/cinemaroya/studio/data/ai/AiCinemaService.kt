package com.cinemaroya.studio.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.cinemaroya.studio.model.CameraShotAnglePreset
import com.cinemaroya.studio.model.ColorGradePreset
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.model.VideoScene
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.roundToInt

private const val TAG = "AiCinemaService"

private const val STORYBOARD_JSON_SHAPE = """
أعد كائن JSON صالحاً فقط، بدون أي نص قبله أو بعده، بهذا الشكل بالضبط:
{"title":"عنوان قصير","synopsis":"ملخص جملة أو جملتين","globalLightingPreset":"CINEMATIC_GOLD|TEAL_ORANGE|NOIR_CONTRAST|DESERT_WARM|CYBER_NEON|NATURAL_8K","scenes":[{"title":"عنوان المشهد","narrationAr":"سرد عربي","visualPrompt":"English 8K photorealistic prompt","durationSeconds":10,"cameraMovement":"STATIC|PAN_LEFT|PAN_RIGHT|ZOOM_IN|ZOOM_OUT|DOLLY_FORWARD|ORBIT_3D","cameraShotAngle":"EYE_LEVEL|LOW_ANGLE|HIGH_ANGLE|AERIAL_DRONE|DUTCH_ANGLE|CLOSE_UP_MACRO","colorGradePreset":"CINEMATIC_GOLD|TEAL_ORANGE|NOIR_CONTRAST|DESERT_WARM|CYBER_NEON|NATURAL_8K"}]}
اختر قيمة واحدة فقط من كل قائمة مفصولة بـ |.
"""

private const val SCENE_JSON_SHAPE = """
أعد كائن JSON صالحاً فقط، بدون أي نص آخر، بهذا الشكل:
{"title":"","narrationAr":"","visualPrompt":"","cameraShotAngle":"EYE_LEVEL|LOW_ANGLE|HIGH_ANGLE|AERIAL_DRONE|DUTCH_ANGLE|CLOSE_UP_MACRO","cameraPitchDeg":0,"lensFocalMm":35,"lightingKeyIntensity":70,"lightingFillIntensity":40,"lightingRimIntensity":50,"colorTempKelvin":5600,"atmosphereFog":20,"colorGradePreset":"CINEMATIC_GOLD|TEAL_ORANGE|NOIR_CONTRAST|DESERT_WARM|CYBER_NEON|NATURAL_8K"}
اختر قيمة واحدة فقط من كل قائمة مفصولة بـ |.
"""

data class GeneratedStoryboard(
    val title: String,
    val synopsis: String,
    val globalLightingPreset: String,
    val scenes: List<VideoScene>
)

class AiCinemaService {

    // Lean 7-field scene schema for ultra-fast structured JSON decoding without latency timeouts.
    // Detailed 3-point lighting, Kelvin, fog, pitch, and lens defaults are derived deterministically
    // from ColorGradePreset and CameraShotAnglePreset and remain fully editable in the CAD studio.
    private val storyboardSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short Arabic film title"),
            "synopsis" to Schema.string(description = "Concise 1-2 sentence Arabic story summary"),
            "globalLightingPreset" to Schema.enumeration(
                values = listOf("CINEMATIC_GOLD", "TEAL_ORANGE", "NOIR_CONTRAST", "DESERT_WARM", "CYBER_NEON", "NATURAL_8K"),
                description = "Recommended global lighting preset"
            ),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short Arabic scene title"),
                        "narrationAr" to Schema.string(description = "Concise 1-2 sentence Arabic cinematic voiceover"),
                        "visualPrompt" to Schema.string(description = "Concise English 8K photorealistic prompt (subject, lighting, lens)"),
                        "durationSeconds" to Schema.integer(description = "Scene duration in seconds (6-20)"),
                        "cameraMovement" to Schema.enumeration(
                            values = listOf("STATIC", "PAN_LEFT", "PAN_RIGHT", "ZOOM_IN", "ZOOM_OUT", "DOLLY_FORWARD", "ORBIT_3D"),
                            description = "Cinematic camera movement"
                        ),
                        "cameraShotAngle" to Schema.enumeration(
                            values = listOf("EYE_LEVEL", "LOW_ANGLE", "HIGH_ANGLE", "AERIAL_DRONE", "DUTCH_ANGLE", "CLOSE_UP_MACRO"),
                            description = "Cinematic camera shot angle"
                        ),
                        "colorGradePreset" to Schema.enumeration(
                            values = listOf("CINEMATIC_GOLD", "TEAL_ORANGE", "NOIR_CONTRAST", "DESERT_WARM", "CYBER_NEON", "NATURAL_8K"),
                            description = "Scene lighting and color grading preset"
                        )
                    )
                ),
                description = "Ordered list of video scenes"
            )
        )
    )

    private val sceneEnhanceSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Refined Arabic scene title"),
            "narrationAr" to Schema.string(description = "Refined Arabic voiceover narration (1-2 sentences)"),
            "visualPrompt" to Schema.string(description = "Enhanced photorealistic 8K visual prompt"),
            "cameraShotAngle" to Schema.enumeration(
                values = listOf("EYE_LEVEL", "LOW_ANGLE", "HIGH_ANGLE", "AERIAL_DRONE", "DUTCH_ANGLE", "CLOSE_UP_MACRO"),
                description = "Recommended camera shot angle"
            ),
            "cameraPitchDeg" to Schema.integer(description = "Recommended camera pitch angle -45 to 45"),
            "lensFocalMm" to Schema.integer(description = "Recommended lens focal length (16, 24, 35, 50, 85)"),
            "lightingKeyIntensity" to Schema.integer(description = "Recommended key light 0-100"),
            "lightingFillIntensity" to Schema.integer(description = "Recommended fill light 0-100"),
            "lightingRimIntensity" to Schema.integer(description = "Recommended rim light 0-100"),
            "colorTempKelvin" to Schema.integer(description = "Recommended color temperature 2000-10000"),
            "atmosphereFog" to Schema.integer(description = "Recommended volumetric fog 0-100"),
            "colorGradePreset" to Schema.enumeration(
                values = listOf("CINEMATIC_GOLD", "TEAL_ORANGE", "NOIR_CONTRAST", "DESERT_WARM", "CYBER_NEON", "NATURAL_8K"),
                description = "Recommended color grade preset"
            )
        )
    )

    private val storyboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private val fastStoryboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private val sceneEnhanceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sceneEnhanceSchema
            }
        )
    }

    private val textSequenceModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateLongVideoStoryboard(
        ideaPrompt: String,
        genre: String,
        visualStyle: String,
        requestedSceneCount: Int,
        referenceBitmap: Bitmap? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): GeneratedStoryboard {
        val safeSceneCount = requestedSceneCount.coerceIn(3, 8)
        val systemInstruction = """
            أنت مخرج سينمائي محترف. أنشئ مخطط فيلم من $safeSceneCount مشاهد مترابطة.
            النوع: $genre | الأسلوب البصري: $visualStyle.
            اجعل السرد العربي (narrationAr) مكثفاً وجميلاً في جملتين كحد أقصى لكل مشهد، واجعل visualPrompt بالإنجليزية مركّزاً (20-30 كلمة) يصف اللقطة والإضاءة الواقعية 8K.
        """.trimIndent()

        val scaledBitmap = referenceBitmap?.let { downscaleBitmap(it, maxDimensionPx = 768) }
        val requestContent: Content = if (scaledBitmap != null) {
            content {
                image(scaledBitmap)
                text("$systemInstruction\n\nاستلهم من الصورة المرجعية لتوليد المشاهد حول الفكرة:\n$ideaPrompt")
            }
        } else {
            content {
                text("$systemInstruction\n\nفكرة الفيديو:\n$ideaPrompt")
            }
        }

        val geminiRun: suspend () -> String = {
            try {
                withTimeout(45_000L) {
                    collectStreamText(storyboardModel, requestContent) { charsCount ->
                        onStatusUpdate?.invoke("جاري صياغة المشاهد السينمائية عبر Gemini ($charsCount حرف)...")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Primary Gemini storyboard failed, trying gemini-3.5-flash-lite", e)
                onStatusUpdate?.invoke("تم تحويل الطلب لنموذج Gemini 3.5 Flash-Lite السريع...")
                withTimeout(45_000L) {
                    collectStreamText(fastStoryboardModel, requestContent) { charsCount ->
                        onStatusUpdate?.invoke("جاري استكمال المشاهد بسرعة عالية ($charsCount حرف)...")
                    }
                }
            }
        }
        val externalPrompt = "$systemInstruction\n\n$STORYBOARD_JSON_SHAPE\n\nفكرة الفيديو:\n$ideaPrompt"
        val jsonText = runWithEngines(
            onStatus = onStatusUpdate,
            external = { engine ->
                if (scaledBitmap != null) {
                    onStatusUpdate?.invoke("${engine.label} يتجاهل الصورة المرجعية ويعتمد على النص فقط...")
                }
                ExternalAiClient.extractJsonObject(ExternalAiClient.chat(engine, externalPrompt, jsonMode = true))
            },
            gemini = geminiRun
        )

        if (jsonText.isBlank()) {
            throw IllegalStateException("لم يرجع النموذج استجابة نصية.")
        }
        return parseStoryboardJson(jsonText)
    }

    suspend fun enhanceSceneAndLighting(
        scene: VideoScene,
        directorInstructionAr: String
    ): VideoScene {
        val prompt = """
            أنت مخرج إضاءة وتصوير سينمائي (Director of Photography).
            لدينا المشهد التالي:
            العنوان: ${scene.title}
            السرد العربي: ${scene.narrationAr}
            الوصف البصري الحالي: ${scene.visualPrompt}
            زاوية الكاميرا الحالية: ${scene.cameraShotAngle} (Pitch=${scene.cameraPitchDeg}°, Lens=${scene.lensFocalMm}mm)
            إعدادات الإضاءة الحالية: Key=${scene.lightingKeyIntensity}%, Fill=${scene.lightingFillIntensity}%, Rim=${scene.lightingRimIntensity}%, Kelvin=${scene.colorTempKelvin}K, Fog=${scene.atmosphereFog}%

            مطلوب تعديل وتطوير هذا المشهد وزاوية الكاميرا وإضاءته بناءً على توجيه المخرج التالي:
            "$directorInstructionAr"
        """.trimIndent()

        val jsonText = runWithEngines(
            onStatus = null,
            external = { engine ->
                ExternalAiClient.extractJsonObject(
                    ExternalAiClient.chat(engine, "$prompt\n\n$SCENE_JSON_SHAPE", jsonMode = true)
                )
            },
            gemini = {
                collectStreamText(
                    model = sceneEnhanceModel,
                    inputContent = content { text(prompt) }
                )
            }
        )
        if (jsonText.isBlank()) {
            throw IllegalStateException("تعذر تحديث المشهد بالذكاء الاصطناعي.")
        }
        val obj = JSONObject(jsonText)

        return scene.copy(
            title = obj.optString("title", scene.title),
            narrationAr = obj.optString("narrationAr", scene.narrationAr),
            visualPrompt = obj.optString("visualPrompt", scene.visualPrompt),
            cameraShotAngle = obj.optString("cameraShotAngle", scene.cameraShotAngle),
            cameraPitchDeg = obj.optLong("cameraPitchDeg", scene.cameraPitchDeg).coerceIn(-45L, 45L),
            lensFocalMm = normalizeLensMm(obj.optLong("lensFocalMm", scene.lensFocalMm)),
            lightingKeyIntensity = obj.optLong("lightingKeyIntensity", scene.lightingKeyIntensity).coerceIn(0L, 100L),
            lightingFillIntensity = obj.optLong("lightingFillIntensity", scene.lightingFillIntensity).coerceIn(0L, 100L),
            lightingRimIntensity = obj.optLong("lightingRimIntensity", scene.lightingRimIntensity).coerceIn(0L, 100L),
            colorTempKelvin = obj.optLong("colorTempKelvin", scene.colorTempKelvin).coerceIn(2000L, 10000L),
            atmosphereFog = obj.optLong("atmosphereFog", scene.atmosphereFog).coerceIn(0L, 100L),
            colorGradePreset = obj.optString("colorGradePreset", scene.colorGradePreset)
        )
    }

    suspend fun generateVideoPromptSequenceDocument(
        project: VideoProject,
        scenes: List<VideoScene>
    ): String {
        val scenesSummary = scenes.mapIndexed { idx, s ->
            "مشهد ${idx + 1} (${s.title} - ${s.durationSeconds}ث): حركة=${s.cameraMovement}, زاوية=${s.cameraShotAngle}, عدسة=${s.lensFocalMm}mm, إضاءة=${s.colorTempKelvin}K Key=${s.lightingKeyIntensity}% | الوصف: ${s.visualPrompt}"
        }.joinToString("\n")

        val prompt = """
            أنت مهندس موجهات فيديو سينمائية (AI Video Prompt Sequence Engineer).
            قم بصياغة دليل تسلسل موجهات الفيديو (Video Prompt Sequence) باللغتين العربية والإنجليزية للمشروع التالي:
            عنوان الفيلم: ${project.title}
            الأبعاد: ${project.aspectRatio} | الأسلوب: ${project.visualStyle}
            المشاهد الحالية:
            $scenesSummary

            اكتب لكل مشهد:
            1. وصف اللقطة وزاوية الكاميرا والإضاءة بالعربية.
            2. موجه التوليد الفيزيائي الدقيق (Master Video Prompt) جاهز للنسخ والاستخدام.
        """.trimIndent()

        val resultText = runWithEngines(
            onStatus = null,
            external = { engine -> ExternalAiClient.chat(engine, prompt, jsonMode = false) },
            gemini = {
                collectStreamText(
                    model = textSequenceModel,
                    inputContent = content { text(prompt) }
                )
            }
        )
        if (resultText.isBlank()) {
            throw IllegalStateException("لم يرجع النموذج تسلسل الموجهات.")
        }
        return resultText
    }

    suspend fun generatePhotorealisticSceneFrame(
        scene: VideoScene,
        referenceBitmap: Bitmap? = null
    ): Bitmap? {
        val shotAngle = CameraShotAnglePreset.fromCode(scene.cameraShotAngle)
        val lightingSpec = buildString {
            append("Photorealistic 8K cinematic film still, ARRI Alexa 65 ${scene.lensFocalMm}mm anamorphic lens, ")
            append("${shotAngle.promptTag}, camera vertical pitch ${scene.cameraPitchDeg} degrees, ")
            append("PBR materials, ray-traced reflections. ")
            append("Scene description: ${scene.visualPrompt}. ")
            append("Studio 3-Point Lighting setup: ")
            append("Key light intensity ${scene.lightingKeyIntensity}% at azimuth angle ${scene.lightAngleDeg} degrees and elevation ${scene.lightElevationDeg} degrees, ")
            append("Fill light ${scene.lightingFillIntensity}%, ")
            append("Rim backlight ${scene.lightingRimIntensity}%, ")
            append("Color temperature ${scene.colorTempKelvin}K, ")
            append("Volumetric atmospheric fog ${scene.atmosphereFog}%, ")
            append("Color grading preset: ${scene.colorGradePreset}. No text or watermarks.")
        }

        val scaledBitmap = referenceBitmap?.let { downscaleBitmap(it, maxDimensionPx = 768) }
        val response = if (scaledBitmap != null) {
            imageGenModel.generateContent(
                content {
                    image(scaledBitmap)
                    text("Transform and relight this reference image into a photorealistic cinematic frame matching: $lightingSpec")
                }
            )
        } else {
            imageGenModel.generateContent(lightingSpec)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
    }

    /**
     * Runs the request on the selected engine first; if it fails (quota, network, empty reply) and
     * auto-fallback is on, tries the other engines that are available.
     */
    private suspend fun runWithEngines(
        onStatus: ((String) -> Unit)?,
        external: suspend (AiEngine) -> String,
        gemini: suspend () -> String
    ): String {
        val selected = AiEngineSettings.engine
        val order = buildList {
            add(selected)
            if (AiEngineSettings.autoFallback) {
                AiEngine.entries.filterTo(this) { it != selected }
            }
        }.filter { AiEngineSettings.isAvailable(it) }
        if (order.isEmpty()) {
            throw IllegalStateException("أدخل مفتاح ${selected.label} من إعدادات المحركات أو اختر Gemini.")
        }

        val errors = mutableListOf<String>()
        for (engine in order) {
            try {
                onStatus?.invoke("جاري التوليد عبر ${engine.label}...")
                val text = if (engine.isExternal) external(engine) else gemini()
                if (text.isNotBlank()) return text
                errors += "${engine.label}: ردّ فارغ"
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                errors += "${engine.label}: انتهت المهلة"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Engine ${engine.code} failed", e)
                errors += "${engine.label}: ${(e.message ?: e.javaClass.simpleName).take(160)}"
            }
        }
        throw IllegalStateException(errors.joinToString("\n"))
    }

    private suspend fun collectStreamText(
        model: GenerativeModel,
        inputContent: Content,
        onCharProgress: ((Int) -> Unit)? = null
    ): String {
        val builder = StringBuilder()
        try {
            model.generateContentStream(inputContent).collect { chunk ->
                val chunkText = chunk.text
                if (!chunkText.isNullOrEmpty()) {
                    builder.append(chunkText)
                    onCharProgress?.invoke(builder.length)
                }
            }
            return builder.toString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The stream can be cut mid-response on unstable mobile networks, which surfaces as
            // "Expected end of the object '}', but had 'EOF'". Retry once without streaming.
            Log.w(TAG, "Streaming failed after ${builder.length} chars; retrying without streaming", e)
            onCharProgress?.invoke(0)
            return model.generateContent(inputContent).text.orEmpty()
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimensionPx && height <= maxDimensionPx) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (width >= height) {
            maxDimensionPx to (maxDimensionPx / ratio).roundToInt().coerceAtLeast(1)
        } else {
            (maxDimensionPx * ratio).roundToInt().coerceAtLeast(1) to maxDimensionPx
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun parseStoryboardJson(jsonString: String): GeneratedStoryboard {
        val root = JSONObject(jsonString)
        val title = root.optString("title", "فيلم وثائقي سينمائي")
        val synopsis = root.optString("synopsis", "")
        val globalPresetCode = root.optString("globalLightingPreset", "CINEMATIC_GOLD")
        val scenesArray = root.optJSONArray("scenes")
        val parsedScenes = mutableListOf<VideoScene>()

        if (scenesArray != null) {
            for (i in 0 until scenesArray.length()) {
                val item = scenesArray.getJSONObject(i)
                val shotAngleCode = item.optString("cameraShotAngle", "EYE_LEVEL")
                val shotPreset = CameraShotAnglePreset.fromCode(shotAngleCode)
                val gradeCode = item.optString("colorGradePreset", globalPresetCode)
                val gradePreset = ColorGradePreset.fromCode(gradeCode)
                val lens = deriveLensMm(shotPreset)

                parsedScenes.add(
                    VideoScene(
                        sceneIndex = i.toLong(),
                        title = item.optString("title", "المشهد ${i + 1}"),
                        narrationAr = item.optString("narrationAr", ""),
                        visualPrompt = item.optString("visualPrompt", "Photorealistic 8K cinematic shot"),
                        durationSeconds = item.optLong("durationSeconds", 10L).coerceIn(3L, 60L),
                        cameraMovement = item.optString("cameraMovement", "DOLLY_FORWARD"),
                        transitionType = if (i == scenesArray.length() - 1) "DIP_TO_BLACK" else "CROSSFADE",
                        lightingKeyIntensity = gradePreset.keyLight,
                        lightingFillIntensity = gradePreset.fillLight,
                        lightingRimIntensity = gradePreset.rimLight,
                        colorTempKelvin = gradePreset.defaultKelvin,
                        lightAngleDeg = ((i * 35L + 45L) % 360L),
                        lightElevationDeg = 25L + (i % 3) * 10L,
                        atmosphereFog = gradePreset.fog,
                        colorGradePreset = gradePreset.code,
                        cameraShotAngle = shotPreset.code,
                        cameraPitchDeg = shotPreset.defaultPitchDeg,
                        lensFocalMm = lens
                    )
                )
            }
        }

        return GeneratedStoryboard(
            title = title,
            synopsis = synopsis,
            globalLightingPreset = ColorGradePreset.fromCode(globalPresetCode).code,
            scenes = parsedScenes
        )
    }

    private fun deriveLensMm(shotAngle: CameraShotAnglePreset): Long = when (shotAngle) {
        CameraShotAnglePreset.AERIAL_DRONE,
        CameraShotAnglePreset.HIGH_ANGLE,
        CameraShotAnglePreset.LOW_ANGLE -> 24L
        CameraShotAnglePreset.CLOSE_UP_MACRO -> 85L
        CameraShotAnglePreset.DUTCH_ANGLE,
        CameraShotAnglePreset.EYE_LEVEL -> 35L
    }

    private fun normalizeLensMm(mm: Long): Long {
        val supported = listOf(16L, 24L, 35L, 50L, 85L)
        return supported.minByOrNull { kotlin.math.abs(it - mm) } ?: 35L
    }
}
