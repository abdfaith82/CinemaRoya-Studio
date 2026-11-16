package com.cinemaroya.studio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinemaroya.studio.R
import com.cinemaroya.studio.model.VideoScene
import com.cinemaroya.studio.ui.theme.CinemaGold
import com.cinemaroya.studio.ui.theme.StudioCard
import com.cinemaroya.studio.ui.theme.StudioCyan
import com.cinemaroya.studio.ui.theme.StudioElevated
import com.cinemaroya.studio.ui.theme.StudioEmerald
import com.cinemaroya.studio.ui.theme.StudioObsidian
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class VideoPreviewSourceType {
    CLOUD_STREAM,
    LOCAL_MP4,
    AI_SCENE_SEQUENCE
}

fun resolvePlayableVideoUri(videoUrl: String, localFilePath: String): Uri? {
    val cleanUrl = videoUrl.trim()
    if (cleanUrl.startsWith("https://", ignoreCase = true) ||
        cleanUrl.startsWith("http://", ignoreCase = true) ||
        cleanUrl.startsWith("content://", ignoreCase = true)
    ) {
        return runCatching { Uri.parse(cleanUrl) }.getOrNull()
    }
    if (localFilePath.isNotBlank()) {
        val file = File(localFilePath)
        if (file.exists() && file.length() > 0L) {
            return Uri.fromFile(file)
        }
    }
    return null
}

fun detectVideoSourceType(videoUrl: String, localFilePath: String): VideoPreviewSourceType {
    val cleanUrl = videoUrl.trim()
    if (cleanUrl.startsWith("https://", ignoreCase = true) ||
        cleanUrl.startsWith("http://", ignoreCase = true) ||
        cleanUrl.startsWith("content://", ignoreCase = true)
    ) {
        return VideoPreviewSourceType.CLOUD_STREAM
    }
    if (localFilePath.isNotBlank()) {
        val file = File(localFilePath)
        if (file.exists() && file.length() > 0L) {
            return VideoPreviewSourceType.LOCAL_MP4
        }
    }
    return VideoPreviewSourceType.AI_SCENE_SEQUENCE
}

fun formatMediaTimeMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPreviewPlayer(
    title: String,
    aspectRatioCode: String,
    videoUri: Uri?,
    fallbackThumbnail: Bitmap? = null,
    fallbackDurationSeconds: Long = 10L,
    scenes: List<VideoScene> = emptyList(),
    sceneBitmaps: Map<String, Bitmap> = emptyMap(),
    autoPlay: Boolean = false,
    showFullscreenButton: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val stageAspectRatio = remember(aspectRatioCode) {
        when (aspectRatioCode) {
            "9:16" -> 9f / 16f
            "21:9" -> 21f / 9f
            "1:1" -> 1f
            else -> 16f / 9f
        }.coerceIn(0.65f, 2.35f)
    }

    var forceAiFallbackMode by remember(videoUri) { mutableStateOf(false) }
    val activeVideoUri = if (forceAiFallbackMode) null else videoUri

    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // Media3 ExoPlayer state
    var exoIsPlaying by remember(activeVideoUri) { mutableStateOf(false) }
    var exoIsBuffering by remember(activeVideoUri) { mutableStateOf(activeVideoUri != null) }
    var exoHasEnded by remember(activeVideoUri) { mutableStateOf(false) }
    var exoFirstFrameRendered by remember(activeVideoUri) { mutableStateOf(false) }
    var exoPositionMs by remember(activeVideoUri) { mutableLongStateOf(0L) }
    var exoDurationMs by remember(activeVideoUri) { mutableLongStateOf((fallbackDurationSeconds.coerceAtLeast(1L) * 1000L)) }
    var exoErrorMessage by remember(activeVideoUri) { mutableStateOf<String?>(null) }

    // Scrubber drag tracking so ExoPlayer polling doesn't overwrite user drag
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(context, activeVideoUri) {
        activeVideoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = autoPlay
                prepare()
            }
        }
    }

    // Apply volume / repeat / speed updates to ExoPlayer
    LaunchedEffect(exoPlayer, isMuted, isLooping, playbackSpeed) {
        exoPlayer?.let { player ->
            player.volume = if (isMuted) 0f else 1f
            player.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            player.setPlaybackSpeed(playbackSpeed)
        }
    }

    // Attach ExoPlayer listener & lifecycle observer + clean release
    DisposableEffect(exoPlayer, lifecycleOwner) {
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    exoIsPlaying = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    exoIsBuffering = playbackState == Player.STATE_BUFFERING
                    exoHasEnded = playbackState == Player.STATE_ENDED
                    if (playbackState == Player.STATE_READY) {
                        exoErrorMessage = null
                        val dur = exoPlayer.duration
                        if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                            exoDurationMs = dur
                        }
                    }
                }

                override fun onRenderedFirstFrame() {
                    exoFirstFrameRendered = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    exoIsBuffering = false
                    exoIsPlaying = false
                    exoErrorMessage = error.localizedMessage ?: "تعذر تحميل أو تشغيل ملف الفيديو."
                }
            }
            exoPlayer.addListener(listener)

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    exoPlayer.pause()
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    // Poll ExoPlayer current position smoothly
    LaunchedEffect(exoPlayer, exoIsPlaying) {
        if (exoPlayer != null) {
            while (exoIsPlaying) {
                if (!isScrubbing) {
                    exoPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                        exoDurationMs = dur
                    }
                }
                delay(100L)
            }
        }
    }

    // Virtual AI scene sequence / thumbnail player state (when no MP4 URI or fallback selected)
    val virtualTotalDurationMs = remember(scenes, fallbackDurationSeconds) {
        val scenesMs = scenes.sumOf { it.durationSeconds.coerceIn(3L, 60L) * 1000L }
        if (scenesMs > 0L) scenesMs else (fallbackDurationSeconds.coerceIn(3L, 3600L) * 1000L)
    }
    var virtualIsPlaying by remember(activeVideoUri) { mutableStateOf(activeVideoUri == null && autoPlay) }
    var virtualPositionMs by remember(activeVideoUri) { mutableLongStateOf(0L) }
    var virtualHasEnded by remember(activeVideoUri) { mutableStateOf(false) }

    LaunchedEffect(activeVideoUri, virtualIsPlaying, playbackSpeed, isLooping, virtualTotalDurationMs) {
        if (activeVideoUri == null && virtualIsPlaying) {
            val tickMs = 50L
            while (virtualIsPlaying) {
                delay(tickMs)
                val advance = (tickMs * playbackSpeed).toLong().coerceAtLeast(10L)
                val next = virtualPositionMs + advance
                if (next >= virtualTotalDurationMs) {
                    if (isLooping) {
                        virtualPositionMs = 0L
                        virtualHasEnded = false
                    } else {
                        virtualPositionMs = virtualTotalDurationMs
                        virtualIsPlaying = false
                        virtualHasEnded = true
                    }
                } else {
                    virtualPositionMs = next
                    virtualHasEnded = false
                }
            }
        }
    }

    val effectiveIsPlaying = if (activeVideoUri != null) exoIsPlaying else virtualIsPlaying
    val effectiveHasEnded = if (activeVideoUri != null) exoHasEnded else virtualHasEnded
    val effectiveDurationMs = if (activeVideoUri != null) exoDurationMs.coerceAtLeast(1000L) else virtualTotalDurationMs
    val effectivePositionMs = if (activeVideoUri != null) exoPositionMs.coerceIn(0L, effectiveDurationMs) else virtualPositionMs.coerceIn(0L, effectiveDurationMs)
    val effectiveFraction = if (isScrubbing) {
        scrubFraction
    } else {
        (effectivePositionMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun togglePlayPause() {
        if (activeVideoUri != null && exoPlayer != null) {
            if (exoHasEnded) {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            } else if (exoIsPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
        } else {
            if (virtualHasEnded || virtualPositionMs >= virtualTotalDurationMs) {
                virtualPositionMs = 0L
                virtualHasEnded = false
                virtualIsPlaying = true
            } else {
                virtualIsPlaying = !virtualIsPlaying
            }
        }
    }

    fun seekByDelta(deltaMs: Long) {
        if (activeVideoUri != null && exoPlayer != null) {
            val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, effectiveDurationMs)
            exoPlayer.seekTo(target)
            exoPositionMs = target
        } else {
            val target = (virtualPositionMs + deltaMs).coerceIn(0L, virtualTotalDurationMs)
            virtualPositionMs = target
            virtualHasEnded = target >= virtualTotalDurationMs
        }
    }

    fun seekToFraction(fraction: Float) {
        val targetMs = (fraction.coerceIn(0f, 1f) * effectiveDurationMs).toLong()
        if (activeVideoUri != null && exoPlayer != null) {
            exoPlayer.seekTo(targetMs)
            exoPositionMs = targetMs
        } else {
            virtualPositionMs = targetMs
            virtualHasEnded = targetMs >= virtualTotalDurationMs
        }
    }

    fun cyclePlaybackSpeed() {
        playbackSpeed = when (playbackSpeed) {
            0.5f -> 1f
            1f -> 1.5f
            1.5f -> 2f
            else -> 0.5f
        }
    }

    // Resolve current scene for multi-scene AI rendering
    val (activeSceneIndex, activeSceneProgress) = remember(scenes, effectivePositionMs) {
        if (scenes.isEmpty()) {
            0 to 0f
        } else {
            var remaining = effectivePositionMs
            var foundIdx = scenes.lastIndex
            var foundProgress = 1f
            for (idx in scenes.indices) {
                val durMs = scenes[idx].durationSeconds.coerceIn(3L, 60L) * 1000L
                if (remaining <= durMs || idx == scenes.lastIndex) {
                    foundIdx = idx
                    foundProgress = (remaining.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                    break
                }
                remaining -= durMs
            }
            foundIdx to foundProgress
        }
    }
    val activeScene = scenes.getOrNull(activeSceneIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StudioCard)
            .border(1.dp, StudioElevated, RoundedCornerShape(18.dp))
            .padding(10.dp)
            .testTag("video_preview_player"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Video Rendering Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(stageAspectRatio)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioObsidian)
                .border(1.dp, CinemaGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .clickable(enabled = exoErrorMessage == null) { togglePlayPause() }
        ) {
            if (activeVideoUri != null && exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setKeepContentOnPlayerReset(true)
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Show fallback thumbnail under player until first video frame arrives
                if (!exoFirstFrameRendered && fallbackThumbnail != null && !fallbackThumbnail.isRecycled) {
                    Image(
                        bitmap = fallbackThumbnail.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (exoIsBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(StudioObsidian.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CinemaGold)
                    }
                } else if (exoErrorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(StudioObsidian.copy(alpha = 0.88f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = exoErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        exoErrorMessage = null
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CinemaGold,
                                        contentColor = StudioObsidian
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.playback_retry_btn))
                                }
                                if (scenes.isNotEmpty() || fallbackThumbnail != null) {
                                    OutlinedButton(
                                        onClick = { forceAiFallbackMode = true },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.preview_source_ai_scenes), color = StudioCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeScene != null) {
                CinemaViewportStage(
                    scene = activeScene,
                    sceneIndex = activeSceneIndex,
                    totalScenes = scenes.size,
                    sceneBitmap = sceneBitmaps[activeScene.id] ?: fallbackThumbnail,
                    playbackProgress = activeSceneProgress,
                    aspectRatioCode = aspectRatioCode,
                    showRuleOfThirdsGrid = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (fallbackThumbnail != null && !fallbackThumbnail.isRecycled) {
                val kenBurnsScale = 1.0f + (effectiveFraction * 0.14f)
                Image(
                    bitmap = fallbackThumbnail.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = kenBurnsScale
                            scaleY = kenBurnsScale
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, StudioObsidian.copy(alpha = 0.85f))
                            )
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CinemaGold.copy(alpha = 0.22f), StudioObsidian)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = null,
                            tint = CinemaGold,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Center Play / Replay badge when paused & no error/buffering
            if (!effectiveIsPlaying && !exoIsBuffering && exoErrorMessage == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(StudioObsidian.copy(alpha = 0.78f))
                        .border(1.5.dp, CinemaGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (effectiveHasEnded) Icons.Default.Replay else Icons.Default.PlayArrow,
                        contentDescription = if (effectiveHasEnded) {
                            stringResource(R.string.playback_replay)
                        } else {
                            stringResource(R.string.playback_play)
                        },
                        tint = CinemaGold,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Scrubber Timeline & Time Display
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Slider(
                value = effectiveFraction,
                onValueChange = { newVal ->
                    isScrubbing = true
                    scrubFraction = newVal
                },
                onValueChangeFinished = {
                    seekToFraction(scrubFraction)
                    isScrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = CinemaGold,
                    activeTrackColor = CinemaGold,
                    inactiveTrackColor = StudioElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_preview_scrubber")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatMediaTimeMs(effectivePositionMs)} / ${formatMediaTimeMs(effectiveDurationMs)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CinemaGold
                )

                if (activeVideoUri == null && activeScene != null && scenes.size > 1) {
                    Text(
                        text = "المشهد ${activeSceneIndex + 1}/${scenes.size} • ${activeScene.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Playback Controls Transport Row (responsive FlowRow for compact phones and tablets)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { seekByDelta(-5000L) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_preview_seek_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay5,
                        contentDescription = stringResource(R.string.playback_seek_back_5s),
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CinemaGold)
                        .testTag("video_preview_play_pause")
                ) {
                    Icon(
                        imageVector = when {
                            effectiveIsPlaying -> Icons.Default.Pause
                            effectiveHasEnded -> Icons.Default.Replay
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = when {
                            effectiveIsPlaying -> stringResource(R.string.playback_pause)
                            effectiveHasEnded -> stringResource(R.string.playback_replay)
                            else -> stringResource(R.string.playback_play)
                        },
                        tint = StudioObsidian
                    )
                }

                IconButton(
                    onClick = { seekByDelta(5000L) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_preview_seek_forward")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward5,
                        contentDescription = stringResource(R.string.playback_seek_forward_5s),
                        tint = Color.White
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_preview_mute")
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (isMuted) {
                            stringResource(R.string.playback_unmute_toggle)
                        } else {
                            stringResource(R.string.playback_mute_toggle)
                        },
                        tint = if (isMuted) Color(0xFF94A3B8) else StudioCyan
                    )
                }

                IconButton(
                    onClick = { isLooping = !isLooping },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_preview_loop")
                ) {
                    Icon(
                        imageVector = if (isLooping) Icons.Default.RepeatOn else Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.playback_loop_toggle),
                        tint = if (isLooping) CinemaGold else Color(0xFF94A3B8)
                    )
                }

                Surface(
                    onClick = { cyclePlaybackSpeed() },
                    color = StudioElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("video_preview_speed")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        val speedLabel = if (playbackSpeed % 1f == 0f) {
                            "${playbackSpeed.toInt()}x"
                        } else {
                            "${playbackSpeed}x"
                        }
                        Text(
                            text = speedLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CinemaGold
                        )
                    }
                }

                if (showFullscreenButton && onFullscreenClick != null) {
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("video_preview_fullscreen")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.preview_fullscreen_btn),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPreviewModalDialog(
    title: String,
    aspectRatioCode: String,
    sourceType: VideoPreviewSourceType,
    videoUri: Uri?,
    fallbackThumbnail: Bitmap? = null,
    fallbackDurationSeconds: Long = 10L,
    scenes: List<VideoScene> = emptyList(),
    sceneBitmaps: Map<String, Bitmap> = emptyMap(),
    onDismiss: () -> Unit,
    onEditInStudio: (() -> Unit)? = null,
    onShareVideo: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = StudioObsidian,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 860.dp)
                .border(1.dp, StudioElevated, RoundedCornerShape(24.dp))
                .testTag("video_preview_modal_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title.ifBlank { stringResource(R.string.preview_modal_title) },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (sourceLabelRes, sourceColor) = when (sourceType) {
                                VideoPreviewSourceType.CLOUD_STREAM ->
                                    R.string.preview_source_cloud_stream to StudioCyan
                                VideoPreviewSourceType.LOCAL_MP4 ->
                                    R.string.preview_source_local_mp4 to StudioEmerald
                                VideoPreviewSourceType.AI_SCENE_SEQUENCE ->
                                    R.string.preview_source_ai_scenes to CinemaGold
                            }
                            Surface(
                                color = sourceColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(sourceLabelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = sourceColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = StudioElevated,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = aspectRatioCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_video_preview_modal")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق المعاينة",
                            tint = Color.White
                        )
                    }
                }

                VideoPreviewPlayer(
                    title = title,
                    aspectRatioCode = aspectRatioCode,
                    videoUri = videoUri,
                    fallbackThumbnail = fallbackThumbnail,
                    fallbackDurationSeconds = fallbackDurationSeconds,
                    scenes = scenes,
                    sceneBitmaps = sceneBitmaps,
                    autoPlay = true,
                    showFullscreenButton = false
                )

                if (onEditInStudio != null || onShareVideo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onEditInStudio != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onEditInStudio()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = CinemaGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.gallery_open_project),
                                    color = CinemaGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (onShareVideo != null) {
                            Button(
                                onClick = onShareVideo,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioEmerald,
                                    contentColor = StudioObsidian
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.gallery_share_video),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
