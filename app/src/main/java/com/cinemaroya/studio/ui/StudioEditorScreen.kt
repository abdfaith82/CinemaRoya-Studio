package com.cinemaroya.studio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cinemaroya.studio.R
import com.cinemaroya.studio.model.CameraLensPreset
import com.cinemaroya.studio.model.CameraMovementPreset
import com.cinemaroya.studio.model.CameraShotAnglePreset
import com.cinemaroya.studio.model.CinematicTemplatePreset
import com.cinemaroya.studio.model.ColorGradePreset
import com.cinemaroya.studio.model.TransitionPreset
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.model.VideoScene
import com.cinemaroya.studio.ui.components.AppCheckDiagnosticsDialog
import com.cinemaroya.studio.ui.components.CinemaViewportStage
import com.cinemaroya.studio.ui.components.InteractiveLightingRigRadar
import com.cinemaroya.studio.ui.components.VideoPreviewModalDialog
import com.cinemaroya.studio.ui.components.VideoPreviewSourceType
import com.cinemaroya.studio.ui.components.formatMediaTimeMs
import com.cinemaroya.studio.ui.theme.CinemaGold
import com.cinemaroya.studio.ui.theme.StudioCard
import com.cinemaroya.studio.ui.theme.StudioCyan
import com.cinemaroya.studio.ui.theme.StudioElevated
import com.cinemaroya.studio.ui.theme.StudioEmerald
import com.cinemaroya.studio.ui.theme.StudioObsidian
import com.cinemaroya.studio.util.AppCheckDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioEditorScreen(
    project: VideoProject,
    scenesState: DataUiState<List<VideoScene>>,
    selectedSceneId: String?,
    sceneBitmaps: Map<String, Bitmap>,
    aiTaskState: AiTaskState,
    promptSequenceText: String?,
    exportState: ExportMp4State,
    onSelectScene: (String) -> Unit,
    onUpdateScene: (VideoScene) -> Unit,
    onMoveSceneOrder: (sceneId: String, directionDelta: Int) -> Unit,
    onDuplicateScene: (VideoScene) -> Unit,
    onApplyLightingPreset: (VideoScene, ColorGradePreset) -> Unit,
    onApplyCameraShotAnglePreset: (VideoScene, CameraShotAnglePreset) -> Unit,
    onApplyCinematicTemplateToScene: (VideoScene, CinematicTemplatePreset) -> Unit,
    onApplyCinematicTemplateToAllScenes: (CinematicTemplatePreset) -> Unit,
    onGenerateAiSceneFrame: (VideoScene, Boolean) -> Unit,
    onGeneratePromptSequence: () -> Unit,
    onDismissPromptSequence: () -> Unit,
    onApplyPhotoDirectlyToScene: (String, Bitmap) -> Unit,
    onEnhanceSceneWithAi: (VideoScene, String) -> Unit,
    onAddNewScene: () -> Unit,
    onDeleteScene: (String) -> Unit,
    onExportMp4: () -> Unit,
    onClearAiState: () -> Unit,
    onClearExportState: () -> Unit,
    onOpenGalleryAfterExport: () -> Unit,
    onBackToHub: () -> Unit,
    appCheckDiagnostics: AppCheckDiagnostics? = null,
    appCheckTestState: AppCheckTestUiState = AppCheckTestUiState.Idle,
    onRefreshAppCheckDiagnostics: () -> Unit = {},
    onSaveAndTestAppCheck: (modeOverride: String, customDebugToken: String, customSiteKey: String) -> Unit = { _, _, _ -> },
    onClearAppCheckTestState: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showAppCheckDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (drawerState.isOpen) {
            drawerScope.launch { drawerState.close() }
        } else {
            onBackToHub()
        }
    }

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isLooping by remember { mutableStateOf(false) }
    var isVoiceoverMuted by remember { mutableStateOf(false) }
    var showGridGuides by remember { mutableStateOf(true) }
    var showFullscreenPreview by remember { mutableStateOf(false) }
    var previewExportedFile by remember { mutableStateOf<File?>(null) }
    var activeTabIndex by rememberSaveable { mutableIntStateOf(0) }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.forLanguageTag("ar")
            }
        }
        ttsEngine = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }

    val scenes = (scenesState as? DataUiState.Success)?.data ?: emptyList()
    val currentSceneIndex = scenes.indexOfFirst { it.id == selectedSceneId }.let { if (it >= 0) it else 0 }
    val currentScene = scenes.getOrNull(currentSceneIndex)

    LaunchedEffect(isVoiceoverMuted, playbackSpeed) {
        ttsEngine?.setSpeechRate(playbackSpeed)
        if (isVoiceoverMuted) {
            ttsEngine?.stop()
        }
    }

    LaunchedEffect(isPlaying, currentScene?.id, scenes.size, playbackSpeed, isLooping) {
        if (isPlaying && currentScene != null) {
            if (!isVoiceoverMuted && currentScene.narrationAr.isNotBlank()) {
                ttsEngine?.setSpeechRate(playbackSpeed)
                ttsEngine?.speak(
                    currentScene.narrationAr,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "scene_${currentScene.id}"
                )
            }
            val durationMs = ((currentScene.durationSeconds.coerceIn(3L, 60L) * 1000L) / playbackSpeed).toLong().coerceAtLeast(500L)
            val stepMs = 50L
            val steps = (durationMs / stepMs).coerceAtLeast(1L)
            val startStep = (playbackProgress * steps).toLong().coerceIn(0L, steps)
            for (step in startStep..steps) {
                if (!isPlaying) break
                playbackProgress = (step.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                delay(stepMs)
            }
            if (isPlaying) {
                playbackProgress = 0f
                val nextIdx = currentSceneIndex + 1
                if (nextIdx < scenes.size) {
                    onSelectScene(scenes[nextIdx].id)
                } else if (isLooping && scenes.isNotEmpty()) {
                    onSelectScene(scenes[0].id)
                } else {
                    isPlaying = false
                }
            }
        } else {
            ttsEngine?.stop()
        }
    }

    fun shareMp4File(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "مشاركة الفيديو السينمائي")
            )
        } catch (_: Exception) {}
    }

    // Exported MP4 Media3 Preview Modal
    val exportedFileToPreview = previewExportedFile
    if (exportedFileToPreview != null) {
        val firstSceneBmp = currentScene?.let { sceneBitmaps[it.id] } ?: sceneBitmaps.values.firstOrNull()
        VideoPreviewModalDialog(
            title = project.title,
            aspectRatioCode = project.aspectRatio,
            sourceType = VideoPreviewSourceType.LOCAL_MP4,
            videoUri = Uri.fromFile(exportedFileToPreview),
            fallbackThumbnail = firstSceneBmp,
            fallbackDurationSeconds = scenes.sumOf { it.durationSeconds }.coerceAtLeast(3L),
            scenes = scenes,
            sceneBitmaps = sceneBitmaps,
            onDismiss = { previewExportedFile = null },
            onShareVideo = { shareMp4File(exportedFileToPreview) }
        )
    }

    // Fullscreen Interactive Multi-Scene Preview Modal
    if (showFullscreenPreview && scenes.isNotEmpty()) {
        val firstSceneBmp = currentScene?.let { sceneBitmaps[it.id] } ?: sceneBitmaps.values.firstOrNull()
        VideoPreviewModalDialog(
            title = project.title,
            aspectRatioCode = project.aspectRatio,
            sourceType = VideoPreviewSourceType.AI_SCENE_SEQUENCE,
            videoUri = null,
            fallbackThumbnail = firstSceneBmp,
            fallbackDurationSeconds = scenes.sumOf { it.durationSeconds }.coerceAtLeast(3L),
            scenes = scenes,
            sceneBitmaps = sceneBitmaps,
            onDismiss = { showFullscreenPreview = false }
        )
    }

    val scenePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && currentScene != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        onApplyPhotoDirectlyToScene(currentScene.id, bmp)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Prompt Sequence Dialog
    if (promptSequenceText != null) {
        AlertDialog(
            onDismissRequest = onDismissPromptSequence,
            containerColor = StudioCard,
            titleContentColor = Color.White,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    text = stringResource(R.string.generate_prompt_seq_btn),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .height(340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = promptSequenceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("CineRoya Prompt Sequence", promptSequenceText))
                        onDismissPromptSequence()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaGold, contentColor = StudioObsidian)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ التسلسل")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPromptSequence) {
                    Text("إغلاق", color = CinemaGold)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CinematicTemplatesDrawerSheet(
                currentScene = currentScene,
                onApplyToCurrentScene = { template ->
                    if (currentScene != null) {
                        onApplyCinematicTemplateToScene(currentScene, template)
                    }
                    drawerScope.launch { drawerState.close() }
                },
                onApplyToAllScenes = { template ->
                    onApplyCinematicTemplateToAllScenes(template)
                    drawerScope.launch { drawerState.close() }
                },
                onClose = {
                    drawerScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioObsidian)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Studio Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBackToHub,
                            modifier = Modifier.testTag("back_to_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "العودة للمشاريع",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val totalDuration = scenes.sumOf { it.durationSeconds }
                            Text(
                                text = "${scenes.size} مشاهد • المدة الكلية ${totalDuration}ث • ${project.aspectRatio}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaGold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { drawerScope.launch { drawerState.open() } },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("cinematic_templates_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = stringResource(R.string.cinematic_templates_btn),
                                tint = CinemaGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.cinematic_templates_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = CinemaGold
                            )
                        }

                        Button(
                            onClick = onExportMp4,
                            enabled = exportState !is ExportMp4State.Exporting && scenes.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioEmerald,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("export_mp4_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.export_mp4_btn),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Export Progress / Ready Banner
                when (exportState) {
                    is ExportMp4State.Exporting -> {
                        Surface(
                            color = StudioCard,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioEmerald.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .testTag("export_progress_indicator")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = exportState.stageMessageAr,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(exportState.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = StudioEmerald
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { exportState.progress },
                                    color = StudioEmerald,
                                    trackColor = StudioElevated,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                    is ExportMp4State.Completed -> {
                        Surface(
                            color = StudioEmerald.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StudioEmerald, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.export_success_title),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = StudioEmerald
                                        )
                                        Text(
                                            text = exportState.file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                    }
                                    IconButton(onClick = onClearExportState) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            isPlaying = false
                                            previewExportedFile = exportState.file
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StudioEmerald,
                                            contentColor = StudioObsidian
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("preview_exported_mp4_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.preview_mp4_btn),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { shareMp4File(exportState.file) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                                tint = StudioEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.share_video_btn), color = StudioEmerald, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        OutlinedButton(
                                            onClick = onOpenGalleryAfterExport,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("view_in_gallery_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = CinemaGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.gallery_view_in_gallery), color = CinemaGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                is ExportMp4State.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = exportState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearExportState) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                else -> {}
            }

            // AI Status Bar
            when (aiTaskState) {
                is AiTaskState.Running -> {
                    Surface(
                        color = StudioCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CinemaGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = CinemaGold,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = aiTaskState.statusMessageAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
                is AiTaskState.Failed -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = aiTaskState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onClearAiState) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (aiTaskState.errorMessage.contains("App Check", ignoreCase = true) ||
                                aiTaskState.errorMessage.contains("reCAPTCHA", ignoreCase = true) ||
                                aiTaskState.errorMessage.contains("403")
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onClearAppCheckTestState()
                                        onRefreshAppCheckDiagnostics()
                                        showAppCheckDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = CinemaGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تشخيص وإعداد App Check",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CinemaGold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    if (showAppCheckDialog) {
                        AppCheckDiagnosticsDialog(
                            diagnostics = appCheckDiagnostics,
                            testState = appCheckTestState,
                            onSaveAndTest = onSaveAndTestAppCheck,
                            onDismiss = { showAppCheckDialog = false }
                        )
                    }
                }
                is AiTaskState.Completed -> {
                    Surface(
                        color = StudioCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = aiTaskState.messageAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioCyan,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearAiState) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = StudioCyan)
                            }
                        }
                    }
                }
                else -> {}
            }

            if (currentScene != null) {
                // Live Cinema Viewport Stage
                CinemaViewportStage(
                    scene = currentScene,
                    sceneIndex = currentSceneIndex,
                    totalScenes = scenes.size,
                    sceneBitmap = sceneBitmaps[currentScene.id],
                    playbackProgress = playbackProgress,
                    aspectRatioCode = project.aspectRatio,
                    showRuleOfThirdsGrid = showGridGuides
                )

                // Transport & Playback Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioCard)
                        .border(1.dp, StudioElevated, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val sceneDurationMs = currentScene.durationSeconds.coerceIn(3L, 60L) * 1000L
                    val sceneElapsedMs = (playbackProgress * sceneDurationMs).toLong()
                    val totalProjectDurationMs = scenes.sumOf { it.durationSeconds.coerceIn(3L, 60L) * 1000L }
                    val previousScenesDurationMs = scenes.take(currentSceneIndex).sumOf { it.durationSeconds.coerceIn(3L, 60L) * 1000L }
                    val totalElapsedMs = previousScenesDurationMs + sceneElapsedMs

                    Slider(
                        value = playbackProgress,
                        onValueChange = {
                            isPlaying = false
                            playbackProgress = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_playback_scrubber"),
                        colors = SliderDefaults.colors(
                            thumbColor = CinemaGold,
                            activeTrackColor = CinemaGold,
                            inactiveTrackColor = StudioElevated
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMediaTimeMs(totalElapsedMs)} / ${formatMediaTimeMs(totalProjectDurationMs)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CinemaGold
                        )
                        Text(
                            text = "المشهد ${currentSceneIndex + 1}/${scenes.size} (${formatMediaTimeMs(sceneElapsedMs)} / ${formatMediaTimeMs(sceneDurationMs)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioCyan
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentSceneIndex > 0) {
                                        playbackProgress = 0f
                                        onSelectScene(scenes[currentSceneIndex - 1].id)
                                    }
                                },
                                enabled = currentSceneIndex > 0,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "المشهد السابق", tint = Color.White)
                            }

                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(CinemaGold)
                                    .testTag("play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل الفيلم",
                                    tint = StudioObsidian
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentSceneIndex < scenes.size - 1) {
                                        playbackProgress = 0f
                                        onSelectScene(scenes[currentSceneIndex + 1].id)
                                    }
                                },
                                enabled = currentSceneIndex < scenes.size - 1,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "المشهد التالي", tint = Color.White)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isVoiceoverMuted = !isVoiceoverMuted },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("studio_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isVoiceoverMuted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isVoiceoverMuted) {
                                        stringResource(R.string.playback_unmute_toggle)
                                    } else {
                                        stringResource(R.string.playback_mute_toggle)
                                    },
                                    tint = if (isVoiceoverMuted) MaterialTheme.colorScheme.onSurfaceVariant else StudioCyan
                                )
                            }

                            IconButton(
                                onClick = { isLooping = !isLooping },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("studio_loop_button")
                            ) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Default.RepeatOn else Icons.Default.Repeat,
                                    contentDescription = stringResource(R.string.playback_loop_toggle),
                                    tint = if (isLooping) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                onClick = {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.5f -> 1f
                                        1f -> 1.5f
                                        1.5f -> 2f
                                        else -> 0.5f
                                    }
                                },
                                color = StudioElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("studio_speed_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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

                            IconButton(
                                onClick = { showGridGuides = !showGridGuides },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "شبكة الإضاءة والأثلاث",
                                    tint = if (showGridGuides) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    isPlaying = false
                                    showFullscreenPreview = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("studio_fullscreen_preview_button")
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

                // Multi-Scene Timeline Strip with Reordering & Duplication Controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onAddNewScene,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("add_scene_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = CinemaGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_scene_btn), style = MaterialTheme.typography.labelMedium, color = CinemaGold)
                        }
                    }

                    // Active Scene Reorder Action Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioCard)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ترتيب المشهد الحالي (${currentSceneIndex + 1}/${scenes.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaGold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onMoveSceneOrder(currentScene.id, -1) },
                                enabled = currentSceneIndex > 0,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("move_scene_earlier_button")
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.move_scene_earlier), style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { onMoveSceneOrder(currentScene.id, 1) },
                                enabled = currentSceneIndex < scenes.size - 1,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("move_scene_later_button")
                            ) {
                                Text(stringResource(R.string.move_scene_later), style = MaterialTheme.typography.bodySmall)
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onDuplicateScene(currentScene) },
                                modifier = Modifier.testTag("duplicate_scene_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.duplicate_scene),
                                    tint = StudioCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(scenes, key = { _, s -> s.id }) { idx, itemScene ->
                            val isSelected = itemScene.id == currentScene.id
                            val hasAiFrame = sceneBitmaps.containsKey(itemScene.id)
                            Surface(
                                color = if (isSelected) StudioElevated else StudioCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .width(175.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) CinemaGold else StudioElevated,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        isPlaying = false
                                        playbackProgress = 0f
                                        onSelectScene(itemScene.id)
                                    }
                                    .padding(10.dp)
                                    .testTag("timeline_scene_$idx")
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "مشهد ${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) CinemaGold else StudioCyan
                                        )
                                        Text(
                                            text = "${itemScene.durationSeconds}ث • ${itemScene.lensFocalMm}mm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = itemScene.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (hasAiFrame) "✓ لقطة 8K جاهزة" else "${itemScene.colorTempKelvin}K • ${CameraShotAnglePreset.fromCode(itemScene.cameraShotAngle).labelAr.substringBefore(" (")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasAiFrame) StudioEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 4 Director Control Tabs
                val tabTitles = listOf(
                    stringResource(R.string.tab_ai_scene),
                    stringResource(R.string.tab_lighting_3d),
                    stringResource(R.string.tab_camera_angles),
                    stringResource(R.string.tab_arabic_script)
                )

                ScrollableTabRow(
                    selectedTabIndex = activeTabIndex,
                    containerColor = StudioCard,
                    contentColor = CinemaGold,
                    edgePadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    tabTitles.forEachIndexed { idx, title ->
                        Tab(
                            selected = activeTabIndex == idx,
                            onClick = { activeTabIndex = idx },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (activeTabIndex == idx) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("editor_tab_$idx")
                        )
                    }
                }

                // Tab Content Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCard),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (activeTabIndex) {
                            0 -> AiSceneSynthesisTab(
                                scene = currentScene,
                                isAiBusy = aiTaskState is AiTaskState.Running,
                                onUpdatePrompt = { newPrompt ->
                                    onUpdateScene(currentScene.copy(visualPrompt = newPrompt))
                                },
                                onGenerateFrame = { useRef ->
                                    onGenerateAiSceneFrame(currentScene, useRef)
                                },
                                onGeneratePromptSequence = onGeneratePromptSequence,
                                onPickPhotoForScene = {
                                    scenePhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onEnhanceWithAiDirector = { instruction ->
                                    onEnhanceSceneWithAi(currentScene, instruction)
                                },
                                onDeleteScene = {
                                    if (scenes.size > 1) {
                                        onDeleteScene(currentScene.id)
                                    }
                                },
                                canDelete = scenes.size > 1
                            )

                            1 -> AdvancedLightingStudioTab(
                                scene = currentScene,
                                onUpdateScene = onUpdateScene,
                                onSelectPreset = { preset ->
                                    onApplyLightingPreset(currentScene, preset)
                                }
                            )

                            2 -> CameraAnglesAndMotionTab(
                                scene = currentScene,
                                onUpdateScene = onUpdateScene,
                                onSelectShotAngle = { anglePreset ->
                                    onApplyCameraShotAnglePreset(currentScene, anglePreset)
                                }
                            )

                            3 -> ArabicScriptAndVoiceTab(
                                scene = currentScene,
                                onUpdateScene = onUpdateScene,
                                onSpeakPreview = { textAr ->
                                    ttsEngine?.speak(
                                        textAr,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "preview_${currentScene.id}"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CinematicTemplatesDrawerSheet(
    currentScene: VideoScene?,
    onApplyToCurrentScene: (CinematicTemplatePreset) -> Unit,
    onApplyToAllScenes: (CinematicTemplatePreset) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = StudioObsidian,
        drawerContentColor = Color.White,
        modifier = Modifier
            .widthIn(max = 350.dp)
            .testTag("cinematic_templates_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = CinemaGold
                    )
                    Text(
                        text = stringResource(R.string.cinematic_templates_drawer_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White
                    )
                }
            }

            Text(
                text = stringResource(R.string.cinematic_templates_drawer_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = StudioElevated)

            CinematicTemplatePreset.entries.forEach { template ->
                val isCurrentMatch = currentScene != null &&
                    currentScene.colorGradePreset == template.colorGrade.code &&
                    currentScene.cameraMovement == template.cameraMovement.code
                val accentColor = Color(template.accentHex)
                val secondaryColor = Color(template.secondaryHex)

                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrentMatch) 1.5.dp else 1.dp,
                            color = if (isCurrentMatch) accentColor else StudioElevated,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("cinematic_template_card_${template.code}")
                ) {
                    Column {
                        // Visual Color Swatch Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.85f), secondaryColor)
                                    )
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = template.titleEn,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${template.colorGrade.defaultKelvin}K • ${template.lensFocalMm}mm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = template.titleAr,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = template.descriptionAr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Lighting & Camera telemetry pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = StudioElevated,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "إضاءة ${template.colorGrade.keyLight}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CinemaGold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = StudioElevated,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = template.cameraMovement.labelAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onApplyToCurrentScene(template) },
                                    enabled = currentScene != null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = StudioObsidian
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apply_template_scene_${template.code}")
                                ) {
                                    Text(
                                        text = stringResource(R.string.apply_template_current_scene),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onApplyToAllScenes(template) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apply_template_all_${template.code}")
                                ) {
                                    Text(
                                        text = stringResource(R.string.apply_template_all_scenes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSceneSynthesisTab(
    scene: VideoScene,
    isAiBusy: Boolean,
    onUpdatePrompt: (String) -> Unit,
    onGenerateFrame: (Boolean) -> Unit,
    onGeneratePromptSequence: () -> Unit,
    onPickPhotoForScene: () -> Unit,
    onEnhanceWithAiDirector: (String) -> Unit,
    onDeleteScene: () -> Unit,
    canDelete: Boolean
) {
    var localPrompt by remember(scene.id, scene.visualPrompt) { mutableStateOf(scene.visualPrompt) }
    var directorInstruction by rememberSaveable(scene.id) { mutableStateOf("") }

    Text(
        text = stringResource(R.string.ai_frame_section_title),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White
    )

    OutlinedTextField(
        value = localPrompt,
        onValueChange = {
            localPrompt = it
            onUpdatePrompt(it)
        },
        label = { Text(stringResource(R.string.visual_prompt_label)) },
        minLines = 2,
        maxLines = 4,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CinemaGold,
            unfocusedBorderColor = StudioElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_visual_prompt_input")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onGenerateFrame(false) },
            enabled = !isAiBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = CinemaGold,
                contentColor = StudioObsidian
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("generate_scene_frame_button")
        ) {
            Icon(imageVector = Icons.Default.CameraEnhance, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.generate_8k_frame_btn),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }

        OutlinedButton(
            onClick = onPickPhotoForScene,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("scene_pick_photo_button")
        ) {
            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = StudioCyan)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.pick_device_photo_btn), color = Color.White, textAlign = TextAlign.Center)
        }
    }

    OutlinedButton(
        onClick = onGeneratePromptSequence,
        enabled = !isAiBusy,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("generate_prompt_sequence_button")
    ) {
        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = CinemaGold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.generate_prompt_seq_btn),
            color = CinemaGold,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(R.string.ai_director_label),
        style = MaterialTheme.typography.titleSmall,
        color = StudioCyan
    )

    OutlinedTextField(
        value = directorInstruction,
        onValueChange = { directorInstruction = it },
        label = { Text(stringResource(R.string.ai_director_hint)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_director_instruction_input")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { onEnhanceWithAiDirector(directorInstruction) },
            enabled = !isAiBusy && directorInstruction.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioCyan,
                contentColor = StudioObsidian
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("ai_enhance_scene_button")
        ) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.apply_ai_director_btn), style = MaterialTheme.typography.labelLarge)
        }

        if (canDelete) {
            IconButton(
                onClick = onDeleteScene,
                modifier = Modifier.testTag("delete_scene_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_scene),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AdvancedLightingStudioTab(
    scene: VideoScene,
    onUpdateScene: (VideoScene) -> Unit,
    onSelectPreset: (ColorGradePreset) -> Unit
) {
    Text(
        text = stringResource(R.string.lut_presets_label),
        style = MaterialTheme.typography.titleSmall,
        color = CinemaGold
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColorGradePreset.entries.forEach { preset ->
            FilterChip(
                selected = scene.colorGradePreset == preset.code,
                onClick = { onSelectPreset(preset) },
                label = { Text(preset.labelAr) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CinemaGold.copy(alpha = 0.22f),
                    selectedLabelColor = CinemaGold
                )
            )
        }
    }

    InteractiveLightingRigRadar(
        lightAngleDeg = scene.lightAngleDeg,
        lightElevationDeg = scene.lightElevationDeg,
        keyIntensity = scene.lightingKeyIntensity,
        fillIntensity = scene.lightingFillIntensity,
        rimIntensity = scene.lightingRimIntensity,
        colorTempKelvin = scene.colorTempKelvin,
        onLightPositionChanged = { deg, elev ->
            onUpdateScene(
                scene.copy(
                    lightAngleDeg = deg,
                    lightElevationDeg = elev
                )
            )
        }
    )

    LightingSliderRow(
        label = stringResource(R.string.slider_key_light),
        valueText = "${scene.lightingKeyIntensity}%",
        value = scene.lightingKeyIntensity.toFloat(),
        valueRange = 0f..100f,
        accentColor = CinemaGold,
        onValueChange = {
            onUpdateScene(scene.copy(lightingKeyIntensity = it.roundToLong().coerceIn(0L, 100L)))
        }
    )

    LightingSliderRow(
        label = stringResource(R.string.slider_fill_light),
        valueText = "${scene.lightingFillIntensity}%",
        value = scene.lightingFillIntensity.toFloat(),
        valueRange = 0f..100f,
        accentColor = StudioCyan,
        onValueChange = {
            onUpdateScene(scene.copy(lightingFillIntensity = it.roundToLong().coerceIn(0L, 100L)))
        }
    )

    LightingSliderRow(
        label = stringResource(R.string.slider_rim_light),
        valueText = "${scene.lightingRimIntensity}%",
        value = scene.lightingRimIntensity.toFloat(),
        valueRange = 0f..100f,
        accentColor = StudioCyan,
        onValueChange = {
            onUpdateScene(scene.copy(lightingRimIntensity = it.roundToLong().coerceIn(0L, 100L)))
        }
    )

    LightingSliderRow(
        label = stringResource(R.string.slider_kelvin),
        valueText = "${scene.colorTempKelvin}K",
        value = scene.colorTempKelvin.toFloat(),
        valueRange = 2000f..10000f,
        accentColor = CinemaGold,
        onValueChange = {
            onUpdateScene(scene.copy(colorTempKelvin = it.roundToLong().coerceIn(2000L, 10000L)))
        }
    )

    LightingSliderRow(
        label = stringResource(R.string.slider_fog),
        valueText = "${scene.atmosphereFog}%",
        value = scene.atmosphereFog.toFloat(),
        valueRange = 0f..100f,
        accentColor = StudioEmerald,
        onValueChange = {
            onUpdateScene(scene.copy(atmosphereFog = it.roundToLong().coerceIn(0L, 100L)))
        }
    )
}

@Composable
private fun CameraAnglesAndMotionTab(
    scene: VideoScene,
    onUpdateScene: (VideoScene) -> Unit,
    onSelectShotAngle: (CameraShotAnglePreset) -> Unit
) {
    // 1. Camera Shot Angle Presets
    Text(
        text = stringResource(R.string.camera_angle_presets_label),
        style = MaterialTheme.typography.titleSmall,
        color = CinemaGold
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CameraShotAnglePreset.entries.forEach { anglePreset ->
            val selected = scene.cameraShotAngle == anglePreset.code
            Surface(
                color = if (selected) CinemaGold.copy(alpha = 0.16f) else StudioElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (selected) CinemaGold else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onSelectShotAngle(anglePreset)
                    }
                    .padding(10.dp)
                    .testTag("camera_angle_${anglePreset.code}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anglePreset.labelAr,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) CinemaGold else Color.White
                        )
                        Text(
                            text = anglePreset.descAr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = null,
                        tint = if (selected) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 2. Camera Vertical Pitch / Tilt Slider (-45° to +45°)
    LightingSliderRow(
        label = stringResource(R.string.camera_pitch_slider_label),
        valueText = "${scene.cameraPitchDeg}°",
        value = scene.cameraPitchDeg.toFloat(),
        valueRange = -45f..45f,
        accentColor = StudioCyan,
        onValueChange = {
            onUpdateScene(scene.copy(cameraPitchDeg = it.roundToLong().coerceIn(-45L, 45L)))
        }
    )

    // 3. Cinema Lens Focal Length (16mm, 24mm, 35mm, 50mm, 85mm)
    Text(
        text = stringResource(R.string.camera_lens_label),
        style = MaterialTheme.typography.titleSmall,
        color = StudioCyan
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CameraLensPreset.entries.forEach { lens ->
            FilterChip(
                selected = scene.lensFocalMm == lens.focalMm,
                onClick = { onUpdateScene(scene.copy(lensFocalMm = lens.focalMm)) },
                label = { Text(lens.labelAr) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                    selectedLabelColor = StudioCyan
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // 4. Dynamic Camera Rig Movement
    Text(
        text = stringResource(R.string.camera_motion_label),
        style = MaterialTheme.typography.titleSmall,
        color = CinemaGold
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CameraMovementPreset.entries.forEach { preset ->
            FilterChip(
                selected = scene.cameraMovement == preset.code,
                onClick = { onUpdateScene(scene.copy(cameraMovement = preset.code)) },
                label = { Text(preset.labelAr) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CinemaGold.copy(alpha = 0.22f),
                    selectedLabelColor = CinemaGold
                )
            )
        }
    }

    // 5. Transition Preset
    Text(
        text = stringResource(R.string.scene_transition_label),
        style = MaterialTheme.typography.titleSmall,
        color = StudioCyan
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransitionPreset.entries.forEach { tr ->
            FilterChip(
                selected = scene.transitionType == tr.code,
                onClick = { onUpdateScene(scene.copy(transitionType = tr.code)) },
                label = { Text(tr.labelAr) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                    selectedLabelColor = StudioCyan
                )
            )
        }
    }

    LightingSliderRow(
        label = stringResource(R.string.scene_duration_label),
        valueText = "${scene.durationSeconds} ثانية",
        value = scene.durationSeconds.toFloat(),
        valueRange = 3f..60f,
        accentColor = CinemaGold,
        onValueChange = {
            onUpdateScene(scene.copy(durationSeconds = it.roundToLong().coerceIn(3L, 60L)))
        }
    )
}

@Composable
private fun ArabicScriptAndVoiceTab(
    scene: VideoScene,
    onUpdateScene: (VideoScene) -> Unit,
    onSpeakPreview: (String) -> Unit
) {
    var localTitle by remember(scene.id, scene.title) { mutableStateOf(scene.title) }
    var localNarration by remember(scene.id, scene.narrationAr) { mutableStateOf(scene.narrationAr) }

    OutlinedTextField(
        value = localTitle,
        onValueChange = {
            localTitle = it
            onUpdateScene(scene.copy(title = it))
        },
        label = { Text(stringResource(R.string.scene_title_label)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CinemaGold,
            unfocusedBorderColor = StudioElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_title_input")
    )

    OutlinedTextField(
        value = localNarration,
        onValueChange = {
            localNarration = it
            onUpdateScene(scene.copy(narrationAr = it))
        },
        label = { Text(stringResource(R.string.scene_narration_label)) },
        minLines = 3,
        maxLines = 6,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CinemaGold,
            unfocusedBorderColor = StudioElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_narration_input")
    )

    Button(
        onClick = { onSpeakPreview(localNarration) },
        enabled = localNarration.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudioCyan,
            contentColor = StudioObsidian
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("tts_preview_button")
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.tts_preview_btn),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LightingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
    }
}
