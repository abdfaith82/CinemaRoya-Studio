package com.cinemaroya.studio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cinemaroya.studio.R
import com.cinemaroya.studio.model.CameraLensPreset
import com.cinemaroya.studio.model.CameraMovementPreset
import com.cinemaroya.studio.model.CameraShotAnglePreset
import com.cinemaroya.studio.model.VideoScene
import com.cinemaroya.studio.ui.theme.CinemaGold
import com.cinemaroya.studio.ui.theme.StudioCyan
import com.cinemaroya.studio.ui.theme.StudioObsidian
import com.cinemaroya.studio.util.Mp4VideoExporter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

@Composable
fun CinemaViewportStage(
    scene: VideoScene,
    sceneIndex: Int,
    totalScenes: Int,
    sceneBitmap: Bitmap?,
    playbackProgress: Float,
    aspectRatioCode: String,
    showRuleOfThirdsGrid: Boolean,
    modifier: Modifier = Modifier
) {
    val ratio = when (aspectRatioCode) {
        "9:16" -> 9f / 16f
        "21:9" -> 21f / 9f
        "1:1" -> 1f
        else -> 16f / 9f
    }

    var userZoom by remember { mutableFloatStateOf(1f) }
    var userPan by remember { mutableStateOf(Offset.Zero) }

    val kelvinComposeColor = remember(scene.colorTempKelvin) {
        Color(Mp4VideoExporter.kelvinToRgbColor(scene.colorTempKelvin))
    }

    val lensPreset = remember(scene.lensFocalMm) {
        CameraLensPreset.fromFocal(scene.lensFocalMm)
    }
    val shotAnglePreset = remember(scene.cameraShotAngle) {
        CameraShotAnglePreset.fromCode(scene.cameraShotAngle)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.75f, 2.35f))
            .clip(RoundedCornerShape(16.dp))
            .background(StudioObsidian)
            .border(1.dp, CinemaGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    userZoom = (userZoom * zoom).coerceIn(1f, 3.0f)
                    if (userZoom > 1.01f) {
                        userPan += pan
                    } else {
                        userPan = Offset.Zero
                    }
                }
            }
            .testTag("cinema_viewport_stage")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val baseCamScale = when (scene.cameraMovement) {
                "ZOOM_IN" -> 1.02f + 0.16f * playbackProgress
                "ZOOM_OUT" -> 1.18f - 0.15f * playbackProgress
                "DOLLY_FORWARD" -> 1.04f + 0.20f * playbackProgress
                "ORBIT_3D" -> 1.12f
                else -> 1.05f
            } * lensPreset.fovZoomFactor * userZoom

            val camTx = when (scene.cameraMovement) {
                "PAN_LEFT" -> (0.5f - playbackProgress) * w * 0.08f
                "PAN_RIGHT" -> (playbackProgress - 0.5f) * w * 0.08f
                "ORBIT_3D" -> sin(playbackProgress * Math.PI.toFloat()) * w * 0.045f
                else -> 0f
            } + userPan.x

            // Camera pitch/tilt shifts the vertical perspective
            val pitchOffsetPx = (scene.cameraPitchDeg.coerceIn(-45L, 45L) / 45f) * (h * 0.16f)
            val camTy = when (scene.cameraMovement) {
                "DOLLY_FORWARD" -> -playbackProgress * h * 0.03f
                else -> 0f
            } + pitchOffsetPx + userPan.y

            val dutchRollDeg = if (scene.cameraShotAngle == "DUTCH_ANGLE") -9f else 0f

            withTransform({
                translate(left = camTx, top = camTy)
                rotate(degrees = dutchRollDeg, pivot = Offset(w / 2f, h / 2f))
                scale(scaleX = baseCamScale, scaleY = baseCamScale, pivot = Offset(w / 2f, h / 2f))
            }) {
                if (sceneBitmap != null && !sceneBitmap.isRecycled) {
                    val imgBitmap = sceneBitmap.asImageBitmap()
                    drawImage(
                        image = imgBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.toInt(), h.toInt())
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0F1D),
                                kelvinComposeColor.copy(alpha = 0.25f),
                                Color(0xFF070B14)
                            )
                        )
                    )

                    val horizonY = (h * 0.62f + pitchOffsetPx * 0.5f).coerceIn(h * 0.2f, h * 0.85f)
                    drawLine(
                        color = CinemaGold.copy(alpha = 0.35f),
                        start = Offset(0f, horizonY),
                        end = Offset(w, horizonY),
                        strokeWidth = 2f
                    )
                    for (i in -6..6) {
                        val topX = w / 2f + i * (w * 0.05f)
                        val botX = w / 2f + i * (w * 0.19f)
                        drawLine(
                            color = StudioCyan.copy(alpha = 0.18f),
                            start = Offset(topX, horizonY),
                            end = Offset(botX, h),
                            strokeWidth = 1.5f
                        )
                    }
                    for (row in 1..4) {
                        val yPos = horizonY + (h - horizonY) * (row / 4f)
                        drawLine(
                            color = StudioCyan.copy(alpha = 0.14f),
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = 1f
                        )
                    }
                }
            }

            // Real-time 3-Point Studio Lighting Shaders
            val angleRad = Math.toRadians(scene.lightAngleDeg.toDouble())
            val elevFactor = (90L - scene.lightElevationDeg.coerceIn(0L, 90L)) / 90f
            val lightX = (w * 0.5f + cos(angleRad).toFloat() * w * 0.42f * elevFactor).coerceIn(0f, w)
            val lightY = (h * 0.45f + sin(angleRad).toFloat() * h * 0.38f * elevFactor).coerceIn(0f, h)

            val keyAlpha = (scene.lightingKeyIntensity.coerceIn(0L, 100L) / 100f) * 0.52f
            if (keyAlpha > 0.02f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            kelvinComposeColor.copy(alpha = keyAlpha),
                            kelvinComposeColor.copy(alpha = keyAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(lightX, lightY),
                        radius = max(w, h) * 0.75f
                    )
                )
            }

            val shadowAlpha = ((100L - scene.lightingFillIntensity.coerceIn(0L, 100L)) / 100f) * 0.65f
            if (shadowAlpha > 0.03f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF03060C).copy(alpha = shadowAlpha * 0.5f),
                            Color(0xFF020408).copy(alpha = shadowAlpha)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = max(w, h) * 0.7f
                    )
                )
            }

            val rimAlpha = (scene.lightingRimIntensity.coerceIn(0L, 100L) / 100f) * 0.42f
            if (rimAlpha > 0.02f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            StudioCyan.copy(alpha = rimAlpha),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.35f
                    )
                )
            }

            val fogAlpha = (scene.atmosphereFog.coerceIn(0L, 100L) / 100f) * 0.38f
            if (fogAlpha > 0.02f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            kelvinComposeColor.copy(alpha = fogAlpha)
                        ),
                        startY = h * 0.42f,
                        endY = h
                    )
                )
            }

            if (showRuleOfThirdsGrid) {
                val guideColor = Color.White.copy(alpha = 0.22f)
                drawLine(guideColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1f)
                drawLine(guideColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                drawLine(guideColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1f)

                drawCircle(
                    color = CinemaGold,
                    radius = 10f,
                    center = Offset(lightX, lightY),
                    style = Stroke(width = 2.5f)
                )
            }
        }

        // Top HUD Telemetry Bar (Scene + Camera Angle + Lens + Lighting)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC090D16),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "مشهد ${sceneIndex + 1}/$totalScenes • ${shotAnglePreset.labelAr.substringBefore(" (")} • ${scene.lensFocalMm}mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                color = Color(0xCC090D16),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${scene.colorTempKelvin}K • Key ${scene.lightingKeyIntensity}% • ${CameraMovementPreset.fromCode(scene.cameraMovement).labelAr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (scene.narrationAr.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC090D16))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = scene.narrationAr,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun InteractiveLightingRigRadar(
    lightAngleDeg: Long,
    lightElevationDeg: Long,
    keyIntensity: Long,
    fillIntensity: Long,
    rimIntensity: Long,
    colorTempKelvin: Long,
    onLightPositionChanged: (angleDeg: Long, elevationDeg: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val kelvinColor = remember(colorTempKelvin) {
        Color(Mp4VideoExporter.kelvinToRgbColor(colorTempKelvin))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.radar_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${lightAngleDeg}° | ارتفاع ${lightElevationDeg}°",
                style = MaterialTheme.typography.bodySmall,
                color = CinemaGold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B111E))
                .border(1.5.dp, DividerColor, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val maxR = min(cx, cy) * 0.85f
                        val dist = hypot(dx, dy).coerceIn(0f, maxR)
                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).roundToLong()
                        if (deg < 0) deg += 360L
                        val elev = ((1f - (dist / maxR)) * 90f).roundToLong().coerceIn(0L, 90L)
                        onLightPositionChanged(deg, elev)
                    }
                }
                .testTag("lighting_rig_radar"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = min(cx, cy) * 0.85f

                for (ring in 1..3) {
                    drawCircle(
                        color = Color(0xFF28354E),
                        radius = maxR * (ring / 3f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2f)
                    )
                }

                drawLine(Color(0xFF28354E), Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 1f)
                drawLine(Color(0xFF28354E), Offset(cx, cy - maxR), Offset(cx, cy + maxR), strokeWidth = 1f)

                val angleRad = Math.toRadians(lightAngleDeg.toDouble())
                val distRatio = (90L - lightElevationDeg.coerceIn(0L, 90L)) / 90f
                val keyX = cx + cos(angleRad).toFloat() * maxR * distRatio
                val keyY = cy + sin(angleRad).toFloat() * maxR * distRatio

                val conePath = Path().apply {
                    moveTo(keyX, keyY)
                    val perpX = -sin(angleRad).toFloat() * 24f
                    val perpY = cos(angleRad).toFloat() * 24f
                    lineTo(cx + perpX, cy + perpY)
                    lineTo(cx - perpX, cy - perpY)
                    close()
                }
                drawPath(
                    path = conePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            kelvinColor.copy(alpha = (keyIntensity / 100f) * 0.7f),
                            kelvinColor.copy(alpha = 0.05f)
                        ),
                        start = Offset(keyX, keyY),
                        end = Offset(cx, cy)
                    )
                )

                drawCircle(
                    color = StudioCyan.copy(alpha = (rimIntensity / 100f).coerceIn(0.2f, 1f)),
                    radius = 8f,
                    center = Offset(cx, cy - maxR * 0.88f)
                )

                drawCircle(
                    color = Color.White.copy(alpha = (fillIntensity / 100f).coerceIn(0.2f, 1f)),
                    radius = 8f,
                    center = Offset(cx - cos(angleRad).toFloat() * maxR * 0.65f, cy + maxR * 0.55f)
                )

                drawCircle(
                    color = Color.White,
                    radius = 9f,
                    center = Offset(cx, cy)
                )

                drawCircle(
                    color = kelvinColor,
                    radius = 14f,
                    center = Offset(keyX, keyY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 14f,
                    center = Offset(keyX, keyY),
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}

private val DividerColor = Color(0xFF2A364F)
