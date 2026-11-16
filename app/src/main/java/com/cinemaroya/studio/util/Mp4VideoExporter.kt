package com.cinemaroya.studio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.model.VideoScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Mp4VideoExporter {

    private const val MIME_TYPE = "video/avc"
    private const val FRAME_RATE = 15
    private const val I_FRAME_INTERVAL = 1
    private const val BIT_RATE = 4_000_000

    suspend fun exportProjectToMp4(
        context: Context,
        project: VideoProject,
        scenes: List<VideoScene>,
        sceneBitmaps: Map<String, Bitmap>,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        if (scenes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("لا توجد مشاهد لتصديرها."))
        }

        val (width, height) = when (project.aspectRatio) {
            "9:16" -> 720 to 1280
            "1:1" -> 720 to 720
            "21:9" -> 1280 to 544
            else -> 1280 to 720
        }

        val outputDir = File(context.cacheDir, "exported_videos").apply { mkdirs() }
        val outputFile = File(outputDir, "cineroya_${System.currentTimeMillis()}.mp4")

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            // Cap per-scene rendered preview duration to keep export responsive on mobile
            val sceneFrameCounts = scenes.map { scene ->
                val cappedSec = scene.durationSeconds.coerceIn(3L, 8L).toInt()
                cappedSec * FRAME_RATE
            }
            val totalFrames = sceneFrameCounts.sum().coerceAtLeast(1)
            var renderedFrames = 0
            val ptsTracker = longArrayOf(0L)

            for ((sceneIdx, scene) in scenes.withIndex()) {
                val framesInScene = sceneFrameCounts[sceneIdx]
                val sceneBitmap = sceneBitmaps[scene.id]

                for (f in 0 until framesInScene) {
                    coroutineContext.ensureActive()
                    val progressInScene = f.toFloat() / framesInScene.coerceAtLeast(1).toFloat()

                    val surfaceCanvas = inputSurface.lockCanvas(null)
                    try {
                        drawCinematicSceneFrame(
                            canvas = surfaceCanvas,
                            width = width,
                            height = height,
                            scene = scene,
                            sceneNumber = sceneIdx + 1,
                            totalScenes = scenes.size,
                            sourceBitmap = sceneBitmap,
                            progress = progressInScene
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(surfaceCanvas)
                    }

                    drainEncoder(encoder, muxer, bufferInfo, endOfStream = false, ptsFrameCounter = ptsTracker, onTrackAdded = { idx ->
                        trackIndex = idx
                        muxerStarted = true
                    }, currentTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted })

                    renderedFrames++
                    onProgress((renderedFrames.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.99f))
                }
            }

            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true, ptsFrameCounter = ptsTracker, onTrackAdded = { idx ->
                trackIndex = idx
                muxerStarted = true
            }, currentTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted })

            inputSurface.release()
            onProgress(1f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                encoder?.stop()
            } catch (_: Exception) {}
            try {
                encoder?.release()
            } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        ptsFrameCounter: LongArray,
        onTrackAdded: (Int) -> Unit,
        currentTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean
    ) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        val frameDurationUs = 1_000_000L / FRAME_RATE
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!isMuxerStarted()) {
                    val newFormat = encoder.outputFormat
                    val trackIdx = muxer.addTrack(newFormat)
                    muxer.start()
                    onTrackAdded(trackIdx)
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && isMuxerStarted()) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = ptsFrameCounter[0] * frameDurationUs
                        ptsFrameCounter[0] = ptsFrameCounter[0] + 1L
                        muxer.writeSampleData(currentTrackIndex(), encodedData, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    fun drawCinematicSceneFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: VideoScene,
        sceneNumber: Int,
        totalScenes: Int,
        sourceBitmap: Bitmap?,
        progress: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Base background or AI keyframe with camera motion matrix
        canvas.save()
        val scale = when (scene.cameraMovement) {
            "ZOOM_IN" -> 1.02f + 0.18f * progress
            "ZOOM_OUT" -> 1.20f - 0.16f * progress
            "DOLLY_FORWARD" -> 1.04f + 0.22f * progress
            "ORBIT_3D" -> 1.12f
            else -> 1.08f
        }
        val translateX = when (scene.cameraMovement) {
            "PAN_LEFT" -> (0.5f - progress) * w * 0.08f
            "PAN_RIGHT" -> (progress - 0.5f) * w * 0.08f
            "ORBIT_3D" -> sin(progress * Math.PI.toFloat()) * w * 0.04f
            else -> 0f
        }
        val translateY = when (scene.cameraMovement) {
            "DOLLY_FORWARD" -> -progress * h * 0.03f
            else -> 0f
        }

        if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            val matrix = Matrix()
            val bw = sourceBitmap.width.toFloat().coerceAtLeast(1f)
            val bh = sourceBitmap.height.toFloat().coerceAtLeast(1f)
            val baseScale = max(w / bw, h / bh) * scale
            matrix.postTranslate(-bw / 2f, -bh / 2f)
            matrix.postScale(baseScale, baseScale)
            matrix.postTranslate(w / 2f + translateX, h / 2f + translateY)
            canvas.drawBitmap(sourceBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            // Procedural atmospheric stage backdrop when no AI bitmap generated yet
            val kelvinColor = kelvinToRgbColor(scene.colorTempKelvin)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                         Color.rgb(12, 18, 32),
                        blendColor(Color.rgb(20, 30, 50), kelvinColor, 0.32f),
                        Color.rgb(8, 11, 20)
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            // Draw horizon perspective grid lines
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(45, 245, 158, 11)
                strokeWidth = 1.5f
            }
            val horizonY = h * 0.62f
            canvas.drawLine(0f, horizonY, w, horizonY, gridPaint)
            for (i in -6..6) {
                val startX = w / 2f + i * (w * 0.06f) + translateX * 0.4f
                val endX = w / 2f + i * (w * 0.22f) + translateX
                canvas.drawLine(startX, horizonY, endX, h, gridPaint)
            }
        }
        canvas.restore()

        // 2. Apply 3-Point Studio Lighting Rig (Key, Fill, Rim, Kelvin, Volumetric Fog)
        val kelvinColor = kelvinToRgbColor(scene.colorTempKelvin)
        val angleRad = Math.toRadians(scene.lightAngleDeg.toDouble())
        val elevFactor = (90L - scene.lightElevationDeg.coerceIn(0L, 90L)) / 90f
        val lightCenterX = (w * 0.5f + cos(angleRad).toFloat() * w * 0.42f * elevFactor).coerceIn(0f, w)
        val lightCenterY = (h * 0.45f + sin(angleRad).toFloat() * h * 0.38f * elevFactor).coerceIn(0f, h)

        val keyAlpha = ((scene.lightingKeyIntensity.coerceIn(0L, 100L) / 100f) * 145f).toInt()
        if (keyAlpha > 5) {
            val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    lightCenterX,
                    lightCenterY,
                    max(w, h) * 0.72f,
                    intArrayOf(
                        Color.argb(keyAlpha, Color.red(kelvinColor), Color.green(kelvinColor), Color.blue(kelvinColor)),
                        Color.argb(keyAlpha / 3, Color.red(kelvinColor), Color.green(kelvinColor), Color.blue(kelvinColor)),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, keyPaint)
        }

        // Shadow vignette inversely proportional to Fill Light
        val shadowAlpha = (((100L - scene.lightingFillIntensity.coerceIn(0L, 100L)) / 100f) * 165f).toInt()
        if (shadowAlpha > 8) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.5f,
                    max(w, h) * 0.68f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(shadowAlpha / 2, 4, 7, 14),
                        Color.argb(shadowAlpha, 2, 4, 9)
                    ),
                    floatArrayOf(0.25f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, shadowPaint)
        }

        // Rim backlight edge glow
        val rimAlpha = ((scene.lightingRimIntensity.coerceIn(0L, 100L) / 100f) * 115f).toInt()
        if (rimAlpha > 5) {
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h * 0.35f,
                    Color.argb(rimAlpha, 56, 189, 248),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h * 0.35f, rimPaint)
        }

        // Volumetric fog haze
        val fogAlpha = ((scene.atmosphereFog.coerceIn(0L, 100L) / 100f) * 95f).toInt()
        if (fogAlpha > 5) {
            val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, h * 0.4f, 0f, h,
                    Color.TRANSPARENT,
                    Color.argb(fogAlpha, Color.red(kelvinColor), Color.green(kelvinColor), Color.blue(kelvinColor)),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, h * 0.4f, w, h, fogPaint)
        }

        // 3. Subtitle & Scene HUD Overlay
        if (scene.narrationAr.isNotBlank()) {
            val subtitleBoxTop = h * 0.76f
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 9, 13, 22)
            }
            canvas.drawRoundRect(
                RectF(w * 0.08f, subtitleBoxTop, w * 0.92f, h * 0.93f),
                18f,
                18f,
                barPaint
            )

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = min(w, h) * 0.036f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textWidth = (w * 0.78f).toInt().coerceAtLeast(100)
            val staticLayout = StaticLayout.Builder
                .obtain(scene.narrationAr, 0, scene.narrationAr.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .build()

            canvas.save()
            canvas.translate(w * 0.11f, subtitleBoxTop + (h * 0.17f - staticLayout.height) / 2f)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        // Top watermark badge with scene index & Kelvin
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 15, 23, 42)
        }
        canvas.drawRoundRect(RectF(24f, 24f, 320f, 74f), 12f, 12f, badgePaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "SCENE $sceneNumber/$totalScenes • ${scene.colorTempKelvin}K",
            40f,
            56f,
            badgeTextPaint
        )
    }

    fun kelvinToRgbColor(kelvin: Long): Int {
        val temp = (kelvin.coerceIn(2000L, 10000L) / 100.0)
        val r: Double
        val g: Double
        val b: Double

        if (temp <= 66.0) {
            r = 255.0
            g = (99.4708025861 * kotlin.math.ln(temp) - 161.1195681661).coerceIn(0.0, 255.0)
            b = if (temp <= 19.0) {
                0.0
            } else {
                (138.5177312231 * kotlin.math.ln(temp - 10.0) - 305.0447927307).coerceIn(0.0, 255.0)
            }
        } else {
            r = (329.698727446 * Math.pow(temp - 60.0, -0.1332047592)).coerceIn(0.0, 255.0)
            g = (288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)).coerceIn(0.0, 255.0)
            b = 255.0
        }
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    fun generateThumbnailBase64(
        aspectRatio: String,
        scenes: List<VideoScene>,
        sceneBitmaps: Map<String, Bitmap>
    ): String {
        val firstScene = scenes.firstOrNull() ?: return ""
        val (w, h) = when (aspectRatio) {
            "9:16" -> 180 to 320
            "1:1" -> 240 to 240
            "21:9" -> 320 to 136
            else -> 320 to 180
        }
        val thumbBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumbBitmap)
        drawCinematicSceneFrame(
            canvas = canvas,
            width = w,
            height = h,
            scene = firstScene,
            sceneNumber = 1,
            totalScenes = scenes.size.coerceAtLeast(1),
            sourceBitmap = sceneBitmaps[firstScene.id],
            progress = 0.25f
        )
        val baos = ByteArrayOutputStream()
        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 68, baos)
        thumbBitmap.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}

