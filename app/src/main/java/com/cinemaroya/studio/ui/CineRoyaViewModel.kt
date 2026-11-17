package com.cinemaroya.studio.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cinemaroya.studio.data.ai.AiCinemaService
import com.cinemaroya.studio.data.repository.CineRoyaRepository
import com.cinemaroya.studio.model.CameraShotAnglePreset
import com.cinemaroya.studio.model.CinematicTemplatePreset
import com.cinemaroya.studio.model.ColorGradePreset
import com.cinemaroya.studio.model.GeneratedVideo
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.model.VideoScene
import com.cinemaroya.studio.util.AppCheckDiagnostics
import com.cinemaroya.studio.util.AppCheckTokenVerification
import com.cinemaroya.studio.util.Mp4VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cinemaroya.studio.background.BackgroundWork
import com.cinemaroya.studio.util.FreeImageClient
import com.cinemaroya.studio.util.NarrationAudio
import com.cinemaroya.studio.util.getAppCheckDiagnostics
import com.cinemaroya.studio.util.updateUserAppCheckSettings
import com.cinemaroya.studio.util.verifyAppCheckToken
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

private const val TAG = "CineRoyaVM"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Running(val statusMessageAr: String) : AiTaskState
    data class Completed(val messageAr: String) : AiTaskState
    data class Failed(val errorMessage: String) : AiTaskState
}

sealed interface ExportMp4State {
    data object Idle : ExportMp4State
    data class Exporting(
        val progress: Float,
        val stageMessageAr: String = "جاري تصيير المقاطع..."
    ) : ExportMp4State
    data class Completed(val file: File) : ExportMp4State
    data class Error(val message: String) : ExportMp4State
}

sealed interface AppCheckTestUiState {
    data object Idle : AppCheckTestUiState
    data object Testing : AppCheckTestUiState
    data class Success(val messageAr: String) : AppCheckTestUiState
    data class Failed(val messageAr: String) : AppCheckTestUiState
}

class CineRoyaViewModel(
    private val repository: CineRoyaRepository?,
    private val isGuestMode: Boolean = false,
    private val aiService: AiCinemaService = AiCinemaService(),
    private val appContext: Context? = null
) : ViewModel() {

    // Guest-mode local state (never touches Firestore when unauthenticated)
    private val _guestProjects = MutableStateFlow<List<VideoProject>>(createDefaultGuestProjects())
    private val _guestScenesByProject = MutableStateFlow<Map<String, List<VideoScene>>>(createDefaultGuestScenes())
    private val _guestGeneratedVideos = MutableStateFlow<List<GeneratedVideo>>(createDefaultGuestGeneratedVideos())

    val projectsState: StateFlow<DataUiState<List<VideoProject>>> = if (isGuestMode || repository == null) {
        _guestProjects
            .map<List<VideoProject>, DataUiState<List<VideoProject>>> { DataUiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Success(_guestProjects.value)
            )
    } else {
        repository.observeProjects()
            .map<List<VideoProject>, DataUiState<List<VideoProject>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing projects", rootError)
                emit(DataUiState.Error(rootError.message ?: "تعذر تحميل المشاريع السحابية"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )
    }

    val generatedVideosState: StateFlow<DataUiState<List<GeneratedVideo>>> = if (isGuestMode || repository == null) {
        _guestGeneratedVideos
            .map<List<GeneratedVideo>, DataUiState<List<GeneratedVideo>>> { DataUiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Success(_guestGeneratedVideos.value)
            )
    } else {
        repository.observeGeneratedVideos()
            .map<List<GeneratedVideo>, DataUiState<List<GeneratedVideo>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing generated videos", rootError)
                emit(DataUiState.Error(rootError.message ?: "تعذر تحميل معرض الفيديوهات"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )
    }

    private val _selectedProject = MutableStateFlow<VideoProject?>(null)
    val selectedProject: StateFlow<VideoProject?> = _selectedProject.asStateFlow()

    private val _scenesState = MutableStateFlow<DataUiState<List<VideoScene>>>(DataUiState.Loading)
    val scenesState: StateFlow<DataUiState<List<VideoScene>>> = _scenesState.asStateFlow()

    private val _selectedSceneId = MutableStateFlow<String?>(null)
    val selectedSceneId: StateFlow<String?> = _selectedSceneId.asStateFlow()

    private val _sceneBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val sceneBitmaps: StateFlow<Map<String, Bitmap>> = _sceneBitmaps.asStateFlow()

    // Stores per-scene camera shot angle, pitch, and lens across edits
    private val _sceneCameraExtras = MutableStateFlow<Map<String, Triple<String, Long, Long>>>(emptyMap())

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _aiTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiTaskState: StateFlow<AiTaskState> = _aiTaskState.asStateFlow()

    private val _promptSequenceText = MutableStateFlow<String?>(null)
    val promptSequenceText: StateFlow<String?> = _promptSequenceText.asStateFlow()

    private val _exportState = MutableStateFlow<ExportMp4State>(ExportMp4State.Idle)
    val exportState: StateFlow<ExportMp4State> = _exportState.asStateFlow()

    private val _appCheckDiagnostics = MutableStateFlow<AppCheckDiagnostics?>(
        appContext?.let { runCatching { getAppCheckDiagnostics(it) }.getOrNull() }
    )
    val appCheckDiagnostics: StateFlow<AppCheckDiagnostics?> = _appCheckDiagnostics.asStateFlow()

    private val _appCheckTestState = MutableStateFlow<AppCheckTestUiState>(AppCheckTestUiState.Idle)
    val appCheckTestState: StateFlow<AppCheckTestUiState> = _appCheckTestState.asStateFlow()

    private var scenesObserverJob: Job? = null

    fun setReferenceBitmap(bitmap: Bitmap?) {
        _referenceBitmap.value = bitmap
    }

    fun clearAiTaskState() {
        _aiTaskState.value = AiTaskState.Idle
    }

    fun dismissPromptSequenceDialog() {
        _promptSequenceText.value = null
    }

    fun clearExportState() {
        _exportState.value = ExportMp4State.Idle
    }

    fun clearAppCheckTestState() {
        _appCheckTestState.value = AppCheckTestUiState.Idle
    }

    fun refreshAppCheckDiagnostics(context: Context? = appContext) {
        val ctx = context ?: return
        _appCheckDiagnostics.value = runCatching { getAppCheckDiagnostics(ctx) }.getOrNull()
    }

    fun saveAndTestAppCheckSettings(
        context: Context,
        modeOverride: String,
        customDebugToken: String,
        customSiteKey: String
    ) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            _appCheckTestState.value = AppCheckTestUiState.Testing
            updateUserAppCheckSettings(
                context = appCtx,
                modeOverride = modeOverride,
                customDebugToken = customDebugToken,
                customSiteKey = customSiteKey
            )
            refreshAppCheckDiagnostics(appCtx)

            when (val verification = verifyAppCheckToken(appCtx, forceRefresh = true)) {
                is AppCheckTokenVerification.Valid -> {
                    _appCheckTestState.value = AppCheckTestUiState.Success(
                        "تم التحقق من رمز App Check بنجاح (${verification.tokenPreview}). يمكنك الآن توليد الفيديو بالذكاء الاصطناعي."
                    )
                }
                is AppCheckTokenVerification.Failed -> {
                    _appCheckTestState.value = AppCheckTestUiState.Failed(verification.userMessageAr)
                }
            }
            refreshAppCheckDiagnostics(appCtx)
        }
    }

    fun openProject(project: VideoProject) {
        _selectedProject.value = project
        _scenesState.value = DataUiState.Loading
        scenesObserverJob?.cancel()

        if (isGuestMode || repository == null) {
            scenesObserverJob = viewModelScope.launch {
                _guestScenesByProject.collect { allMap ->
                    val list = (allMap[project.id] ?: emptyList())
                        .sortedBy { it.sceneIndex }
                        .map { applyCameraExtras(it) }
                    _scenesState.value = DataUiState.Success(list)
                    val currentId = _selectedSceneId.value
                    if (currentId == null || list.none { it.id == currentId }) {
                        _selectedSceneId.value = list.firstOrNull()?.id
                    }
                }
            }
        } else {
            scenesObserverJob = viewModelScope.launch {
                repository.observeScenes(project.id)
                    .map<List<VideoScene>, DataUiState<List<VideoScene>>> { list ->
                        DataUiState.Success(list.map { applyCameraExtras(it) })
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing scenes", rootError)
                        emit(DataUiState.Error(rootError.message ?: "تعذر تحميل مشاهد المشروع"))
                    }
                    .collect { state ->
                        _scenesState.value = state
                        if (state is DataUiState.Success) {
                            val currentId = _selectedSceneId.value
                            if (currentId == null || state.data.none { it.id == currentId }) {
                                _selectedSceneId.value = state.data.firstOrNull()?.id
                            }
                        }
                    }
            }
        }
    }

    fun closeProject() {
        scenesObserverJob?.cancel()
        _selectedProject.value = null
        _selectedSceneId.value = null
    }

    fun selectScene(sceneId: String) {
        _selectedSceneId.value = sceneId
    }

    private fun isFirebaseInitialized(): Boolean =
        runCatching { FirebaseApp.getInstance() }.isSuccess

    private suspend fun ensureAppCheckReadyOrFail(): Boolean {
        val ctx = appContext ?: return true
        refreshAppCheckDiagnostics(ctx)
        return when (val verification = verifyAppCheckToken(ctx, forceRefresh = false)) {
            is AppCheckTokenVerification.Valid -> true
            is AppCheckTokenVerification.Failed -> {
                _aiTaskState.value = AiTaskState.Failed(verification.userMessageAr)
                false
            }
        }
    }

    private fun formatAiError(error: Exception, fallbackAr: String): String {
        val chainText = generateSequence<Throwable>(error) { it.cause }
            .mapNotNull { it.localizedMessage?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" | ")
        val raw = chainText.ifBlank { error.localizedMessage.orEmpty() }
        val diag = appContext?.let { runCatching { getAppCheckDiagnostics(it) }.getOrNull() }

        return when {
            raw.contains("FirebaseApp is not initialized", ignoreCase = true) || !isFirebaseInitialized() ->
                "ملف google-services.json غير مدمج في هذه النسخة، لذا خدمات Gemini AI متوقفة مؤقتاً. أضف الملف في مجلد app أو عبر سر GOOGLE_SERVICES_JSON في GitHub ثم أعد البناء، أو أنشئ مشروعاً يدوياً أدناه."
            raw.contains("App Check", ignoreCase = true) ||
                raw.contains("App attestation", ignoreCase = true) ||
                raw.contains("403") -> {
                buildString {
                    append("فشل التحقق الأمني للتطبيق (Firebase App Check): $raw\n")
                    if (diag != null) {
                        append("• المزوّد النشط: ${diag.activeModeLabelAr} | المشروع: ${diag.projectId}\n")
                        if (diag.hasProjectMismatchWarning) {
                            append("• السبب المرجح: ملف google-services.json لا يطابق مفتاح reCAPTCHA المدمج.\n")
                        }
                        append("• اضغط على زر «تشخيص وإعداد App Check» أدناه لفحص البصمة (SHA-1) أو تفعيل رمز Debug Token.")
                    } else {
                        append("افتح نافذة «تشخيص وإعداد App Check» للتحقق من المفتاح أو رمز التصحيح.")
                    }
                }
            }
            else -> raw.ifBlank { fallbackAr }
        }
    }

    fun createManualProjectFromIdea(
        ideaPrompt: String,
        genre: String,
        aspectRatio: String,
        visualStyle: String,
        requestedScenes: Int
    ) {
        val cleanText = ideaPrompt.trim().ifBlank { "مشروع سينمائي جديد" }
        val count = requestedScenes.coerceIn(3, 8)
        val generatedTitle = cleanText.lineSequence().firstOrNull()?.take(48)?.trim().orEmpty()
            .ifBlank { "مشروع $genre" }

        viewModelScope.launch {
            val baseSceneTitles = List(count) { idx ->
                when (idx) {
                    0 -> "المشهد الافتتاحي"
                    count - 1 -> "المشهد الختامي"
                    else -> "المشهد التتابعي ${idx + 1}"
                }
            }
            val defaultScenes = baseSceneTitles.mapIndexed { idx, heading ->
                VideoScene(
                    sceneIndex = idx.toLong(),
                    title = "$heading • $generatedTitle",
                    narrationAr = if (idx == 0) cleanText else "استكمال سرد المشهد ${idx + 1} من القصة السينمائية.",
                    visualPrompt = "$visualStyle cinematic shot for $genre, 8K photorealistic lighting",
                    durationSeconds = 10L,
                    cameraMovement = if (idx % 2 == 0) "DOLLY_FORWARD" else "PAN_RIGHT",
                    transitionType = if (idx == count - 1) "DIP_TO_BLACK" else "CROSSFADE",
                    colorGradePreset = "CINEMATIC_GOLD",
                    cameraShotAngle = if (idx == 0) "AERIAL_DRONE" else "EYE_LEVEL",
                    cameraPitchDeg = if (idx == 0) 35L else 0L,
                    lensFocalMm = if (idx == 0) 24L else 35L
                )
            }
            val totalDuration = defaultScenes.sumOf { it.durationSeconds }

            if (isGuestMode || repository == null) {
                val projectId = "guest_manual_${UUID.randomUUID().toString().take(8)}"
                val newProject = VideoProject(
                    id = projectId,
                    userId = "guest",
                    title = generatedTitle,
                    synopsis = cleanText,
                    genre = genre,
                    aspectRatio = aspectRatio,
                    visualStyle = visualStyle,
                    targetDurationSeconds = totalDuration,
                    sceneCount = defaultScenes.size.toLong(),
                    globalLightingPreset = "CINEMATIC_GOLD",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                val createdScenes = defaultScenes.mapIndexed { idx, s ->
                    val sceneId = "guest_scene_${UUID.randomUUID().toString().take(8)}"
                    _sceneCameraExtras.value = _sceneCameraExtras.value + (
                        sceneId to Triple(s.cameraShotAngle, s.cameraPitchDeg, s.lensFocalMm)
                    )
                    s.copy(
                        id = sceneId,
                        userId = "guest",
                        projectId = projectId,
                        sceneIndex = idx.toLong(),
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                }
                _guestProjects.value = listOf(newProject) + _guestProjects.value
                _guestScenesByProject.value = _guestScenesByProject.value + (projectId to createdScenes)
                openProject(newProject)
                _aiTaskState.value = AiTaskState.Completed("تم إنشاء المشروع اليدوي بنجاح! يمكنك التعديل وإضافة الصور وتصدير MP4.")
            } else {
                try {
                    val projectId = repository.createProject(
                        title = generatedTitle,
                        synopsis = cleanText,
                        genre = genre,
                        aspectRatio = aspectRatio,
                        visualStyle = visualStyle,
                        targetDurationSeconds = totalDuration,
                        sceneCount = defaultScenes.size.toLong(),
                        globalLightingPreset = "CINEMATIC_GOLD"
                    ).getOrThrow()
                    for ((idx, scene) in defaultScenes.withIndex()) {
                        val sceneId = repository.createScene(
                            projectId = projectId,
                            scene = scene.copy(sceneIndex = idx.toLong())
                        ).getOrThrow()
                        _sceneCameraExtras.value = _sceneCameraExtras.value + (
                            sceneId to Triple(scene.cameraShotAngle, scene.cameraPitchDeg, scene.lensFocalMm)
                        )
                    }
                    val newProject = VideoProject(
                        id = projectId,
                        title = generatedTitle,
                        synopsis = cleanText,
                        genre = genre,
                        aspectRatio = aspectRatio,
                        visualStyle = visualStyle,
                        targetDurationSeconds = totalDuration,
                        sceneCount = defaultScenes.size.toLong(),
                        globalLightingPreset = "CINEMATIC_GOLD"
                    )
                    openProject(newProject)
                    _aiTaskState.value = AiTaskState.Completed("تم إنشاء المشروع اليدوي بنجاح!")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _aiTaskState.value = AiTaskState.Failed(e.localizedMessage ?: "تعذر إنشاء المشروع.")
                }
            }
        }
    }

    fun generateFullAiProjectFromPrompt(
        ideaPrompt: String,
        genre: String,
        aspectRatio: String,
        visualStyle: String,
        requestedScenes: Int
    ) {
        if (ideaPrompt.isBlank()) {
            _aiTaskState.value = AiTaskState.Failed("يرجى كتابة فكرة أو نص الفيديو أولاً.")
            return
        }
        if (!isFirebaseInitialized()) {
            _aiTaskState.value = AiTaskState.Failed(
                formatAiError(IllegalStateException("FirebaseApp is not initialized"), "")
            )
            return
        }

        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Running("جاري فحص التحقق الأمني App Check وتهيئة Gemini AI...")
            if (!ensureAppCheckReadyOrFail()) return@launch
            _aiTaskState.value = AiTaskState.Running("جاري تأليف السيناريو وتقسيم المشاهد وهندسة الإضاءة وزوايا الكاميرا عبر Gemini AI...")
            try {
                val storyboard = withTimeout(95_000L) {
                    aiService.generateLongVideoStoryboard(
                        ideaPrompt = ideaPrompt,
                        genre = genre,
                        visualStyle = visualStyle,
                        requestedSceneCount = requestedScenes.coerceIn(3, 8),
                        referenceBitmap = _referenceBitmap.value,
                        onStatusUpdate = { statusAr ->
                            _aiTaskState.value = AiTaskState.Running(statusAr)
                        }
                    )
                }

                val totalDuration = storyboard.scenes.sumOf { it.durationSeconds }.coerceAtLeast(15L)

                if (isGuestMode || repository == null) {
                    val projectId = "guest_proj_${UUID.randomUUID().toString().take(8)}"
                    val newProject = VideoProject(
                        id = projectId,
                        userId = "guest",
                        title = storyboard.title,
                        synopsis = storyboard.synopsis.ifBlank { ideaPrompt },
                        genre = genre,
                        aspectRatio = aspectRatio,
                        visualStyle = visualStyle,
                        targetDurationSeconds = totalDuration,
                        sceneCount = storyboard.scenes.size.toLong(),
                        globalLightingPreset = storyboard.globalLightingPreset,
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                    val createdScenes = storyboard.scenes.mapIndexed { idx, s ->
                        val sceneId = "guest_scene_${UUID.randomUUID().toString().take(8)}"
                        _sceneCameraExtras.value = _sceneCameraExtras.value + (
                            sceneId to Triple(s.cameraShotAngle, s.cameraPitchDeg, s.lensFocalMm)
                        )
                        s.copy(
                            id = sceneId,
                            userId = "guest",
                            projectId = projectId,
                            sceneIndex = idx.toLong(),
                            createdAt = Timestamp.now(),
                            updatedAt = Timestamp.now()
                        )
                    }
                    _guestProjects.value = listOf(newProject) + _guestProjects.value
                    _guestScenesByProject.value = _guestScenesByProject.value + (projectId to createdScenes)
                    openProject(newProject)
                } else {
                    val createProjResult = repository.createProject(
                        title = storyboard.title,
                        synopsis = storyboard.synopsis.ifBlank { ideaPrompt },
                        genre = genre,
                        aspectRatio = aspectRatio,
                        visualStyle = visualStyle,
                        targetDurationSeconds = totalDuration,
                        sceneCount = storyboard.scenes.size.toLong(),
                        globalLightingPreset = storyboard.globalLightingPreset
                    )

                    val projectId = createProjResult.getOrThrow()
                    for ((idx, scene) in storyboard.scenes.withIndex()) {
                        val sceneId = repository.createScene(
                            projectId = projectId,
                            scene = scene.copy(sceneIndex = idx.toLong())
                        ).getOrThrow()
                        _sceneCameraExtras.value = _sceneCameraExtras.value + (
                            sceneId to Triple(scene.cameraShotAngle, scene.cameraPitchDeg, scene.lensFocalMm)
                        )
                    }

                    val newProject = VideoProject(
                        id = projectId,
                        title = storyboard.title,
                        synopsis = storyboard.synopsis.ifBlank { ideaPrompt },
                        genre = genre,
                        aspectRatio = aspectRatio,
                        visualStyle = visualStyle,
                        targetDurationSeconds = totalDuration,
                        sceneCount = storyboard.scenes.size.toLong(),
                        globalLightingPreset = storyboard.globalLightingPreset
                    )
                    openProject(newProject)
                }

                _aiTaskState.value = AiTaskState.Completed("تم توليد المشروع ومشاهده السينمائية بنجاح!")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Storyboard generation timed out", e)
                _aiTaskState.value = AiTaskState.Failed("انتهت مهلة الاتصال بالذكاء الاصطناعي. يرجى التحقق من اتصال الإنترنت والمحاولة مجدداً.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate storyboard", e)
                _aiTaskState.value = AiTaskState.Failed(formatAiError(e, "فشل توليد السيناريو بالذكاء الاصطناعي."))
            }
        }
    }

    fun generateAiFrameForScene(scene: VideoScene, useReferencePhoto: Boolean = false) {
        if (!isFirebaseInitialized()) {
            _aiTaskState.value = AiTaskState.Failed(
                formatAiError(IllegalStateException("FirebaseApp is not initialized"), "")
            )
            return
        }
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Running("جاري فحص App Check وتوليد اللقطة الواقعية 8K للمشهد (${scene.title})...")
            if (!ensureAppCheckReadyOrFail()) return@launch
            _aiTaskState.value = AiTaskState.Running("جاري توليد اللقطة الواقعية 8K وتطبيق زاوية الكاميرا والإضاءة للمشهد (${scene.title})... قد يستغرق 30-60 ثانية")
            try {
                val refBmp = if (useReferencePhoto) _referenceBitmap.value else null
                val bitmap = withTimeout(75_000L) {
                    aiService.generatePhotorealisticSceneFrame(
                        scene = scene,
                        referenceBitmap = refBmp
                    )
                }
                if (bitmap != null) {
                    _sceneBitmaps.value = _sceneBitmaps.value + (scene.id to bitmap)
                    _aiTaskState.value = AiTaskState.Completed("تم توليد اللقطة الواقعية للمشهد بنجاح!")
                } else {
                    _aiTaskState.value = AiTaskState.Failed("لم يرجع نموذج الصور لقطة لهذا المشهد.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Scene frame generation timed out", e)
                _aiTaskState.value = AiTaskState.Failed("انتهت مهلة توليد الصورة بعد 75 ثانية. يرجى المحاولة مجدداً.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scene frame generation failed", e)
                _aiTaskState.value = AiTaskState.Failed(formatAiError(e, "تعذر توليد لقطة المشهد."))
            }
        }
    }

    fun generateProjectPromptSequence() {
        val project = _selectedProject.value ?: return
        val scenes = (_scenesState.value as? DataUiState.Success)?.data ?: emptyList()
        if (scenes.isEmpty()) return
        if (!isFirebaseInitialized()) {
            _aiTaskState.value = AiTaskState.Failed(
                formatAiError(IllegalStateException("FirebaseApp is not initialized"), "")
            )
            return
        }

        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Running("جاري فحص App Check وتوليد تسلسل موجهات الفيديو...")
            if (!ensureAppCheckReadyOrFail()) return@launch
            _aiTaskState.value = AiTaskState.Running("جاري توليد وثيقة تسلسل موجهات الفيديو (Prompt Sequence) عبر Gemini AI...")
            try {
                val doc = withTimeout(60_000L) {
                    aiService.generateVideoPromptSequenceDocument(project, scenes)
                }
                _promptSequenceText.value = doc
                _aiTaskState.value = AiTaskState.Completed("تم توليد تسلسل موجهات الفيديو بنجاح!")
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Failed("انتهت مهلة توليد تسلسل الموجهات بعد 60 ثانية.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate prompt sequence", e)
                _aiTaskState.value = AiTaskState.Failed(formatAiError(e, "تعذر توليد تسلسل الموجهات."))
            }
        }
    }

    fun applyReferenceImageDirectlyToScene(sceneId: String, bitmap: Bitmap) {
        _sceneBitmaps.value = _sceneBitmaps.value + (sceneId to bitmap)
        _referenceBitmap.value = bitmap
    }

    fun enhanceSceneWithAiDirector(scene: VideoScene, instructionAr: String) {
        if (instructionAr.isBlank()) {
            _aiTaskState.value = AiTaskState.Failed("يرجى كتابة توجيه التعديل أولاً.")
            return
        }
        if (!isFirebaseInitialized()) {
            _aiTaskState.value = AiTaskState.Failed(
                formatAiError(IllegalStateException("FirebaseApp is not initialized"), "")
            )
            return
        }
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Running("جاري فحص App Check وتحسين المشهد...")
            if (!ensureAppCheckReadyOrFail()) return@launch
            _aiTaskState.value = AiTaskState.Running("جاري تحسين المشهد وضبط زاوية الكاميرا والإضاءة بالذكاء الاصطناعي...")
            try {
                val updatedScene = withTimeout(60_000L) {
                    aiService.enhanceSceneAndLighting(scene, instructionAr)
                }
                updateSceneRealtime(updatedScene)
                _aiTaskState.value = AiTaskState.Completed("تم تحديث المشهد وزاوية الكاميرا والإضاءة بنجاح!")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Enhance scene timed out", e)
                _aiTaskState.value = AiTaskState.Failed("انتهت مهلة التعديل الذكي بعد 60 ثانية.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enhance scene", e)
                _aiTaskState.value = AiTaskState.Failed(formatAiError(e, "تعذر تحسين المشهد."))
            }
        }
    }

    fun updateSceneRealtime(updatedScene: VideoScene) {
        val project = _selectedProject.value ?: return
        _sceneCameraExtras.value = _sceneCameraExtras.value + (
            updatedScene.id to Triple(
                updatedScene.cameraShotAngle,
                updatedScene.cameraPitchDeg,
                updatedScene.lensFocalMm
            )
        )

        if (isGuestMode || repository == null) {
            val currentList = _guestScenesByProject.value[project.id] ?: emptyList()
            val newList = currentList.map { if (it.id == updatedScene.id) updatedScene else it }
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to newList)
            recalculateGuestProjectTotals(project.id, newList)
        } else {
            viewModelScope.launch {
                repository.updateScene(project.id, updatedScene)
                recalculateCloudProjectTotals(project)
            }
        }
    }

    fun moveSceneOrder(sceneId: String, directionDelta: Int) {
        val project = _selectedProject.value ?: return
        val currentScenes = (_scenesState.value as? DataUiState.Success)?.data ?: return
        val currentIndex = currentScenes.indexOfFirst { it.id == sceneId }
        if (currentIndex < 0) return
        val targetIndex = currentIndex + directionDelta
        if (targetIndex !in currentScenes.indices) return

        val mutable = currentScenes.toMutableList()
        val item = mutable.removeAt(currentIndex)
        mutable.add(targetIndex, item)
        val reindexed = mutable.mapIndexed { idx, scene ->
            scene.copy(sceneIndex = idx.toLong())
        }

        if (isGuestMode || repository == null) {
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to reindexed)
        } else {
            // Update local UI state immediately for snappy UX and persist to Firestore
            _scenesState.value = DataUiState.Success(reindexed)
            viewModelScope.launch {
                for (scene in reindexed) {
                    repository.updateScene(project.id, scene)
                }
            }
        }
    }

    fun duplicateSceneInTimeline(scene: VideoScene) {
        val project = _selectedProject.value ?: return
        val currentScenes = (_scenesState.value as? DataUiState.Success)?.data ?: emptyList()
        val nextIndex = currentScenes.size.toLong()

        if (isGuestMode || repository == null) {
            val newId = "guest_scene_${UUID.randomUUID().toString().take(8)}"
            _sceneCameraExtras.value = _sceneCameraExtras.value + (
                newId to Triple(scene.cameraShotAngle, scene.cameraPitchDeg, scene.lensFocalMm)
            )
            val duplicated = scene.copy(
                id = newId,
                sceneIndex = nextIndex,
                title = "${scene.title} (نسخة)"
            )
            val updatedList = currentScenes + duplicated
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to updatedList)
            _selectedSceneId.value = newId
            recalculateGuestProjectTotals(project.id, updatedList)
        } else {
            viewModelScope.launch {
                val duplicated = scene.copy(
                    id = "",
                    sceneIndex = nextIndex,
                    title = "${scene.title} (نسخة)"
                )
                val result = repository.createScene(project.id, duplicated)
                result.onSuccess { id ->
                    _sceneCameraExtras.value = _sceneCameraExtras.value + (
                        id to Triple(scene.cameraShotAngle, scene.cameraPitchDeg, scene.lensFocalMm)
                    )
                    _selectedSceneId.value = id
                    recalculateCloudProjectTotals(project)
                }
            }
        }
    }

    fun applyLightingPresetToScene(scene: VideoScene, preset: ColorGradePreset) {
        val updated = scene.copy(
            colorGradePreset = preset.code,
            colorTempKelvin = preset.defaultKelvin,
            lightingKeyIntensity = preset.keyLight,
            lightingFillIntensity = preset.fillLight,
            lightingRimIntensity = preset.rimLight,
            atmosphereFog = preset.fog
        )
        updateSceneRealtime(updated)
    }

    fun applyCameraShotAnglePreset(scene: VideoScene, preset: CameraShotAnglePreset) {
        val updated = scene.copy(
            cameraShotAngle = preset.code,
            cameraPitchDeg = preset.defaultPitchDeg
        )
        updateSceneRealtime(updated)
    }

    fun applyCinematicTemplateToScene(scene: VideoScene, template: CinematicTemplatePreset) {
        val updated = scene.copy(
            colorGradePreset = template.colorGrade.code,
            colorTempKelvin = template.colorGrade.defaultKelvin,
            lightingKeyIntensity = template.colorGrade.keyLight,
            lightingFillIntensity = template.colorGrade.fillLight,
            lightingRimIntensity = template.colorGrade.rimLight,
            atmosphereFog = template.colorGrade.fog,
            lightAngleDeg = template.lightAngleDeg,
            lightElevationDeg = template.lightElevationDeg,
            cameraMovement = template.cameraMovement.code,
            cameraShotAngle = template.cameraShotAngle.code,
            cameraPitchDeg = template.cameraShotAngle.defaultPitchDeg,
            lensFocalMm = template.lensFocalMm
        )
        updateSceneRealtime(updated)
        _aiTaskState.value = AiTaskState.Completed("تم تطبيق قالب «${template.titleAr}» على المشهد الحالي!")
    }

    fun applyCinematicTemplateToAllScenes(template: CinematicTemplatePreset) {
        val project = _selectedProject.value ?: return
        val currentScenes = (_scenesState.value as? DataUiState.Success)?.data ?: return
        val updatedScenes = currentScenes.map { scene ->
            val updated = scene.copy(
                colorGradePreset = template.colorGrade.code,
                colorTempKelvin = template.colorGrade.defaultKelvin,
                lightingKeyIntensity = template.colorGrade.keyLight,
                lightingFillIntensity = template.colorGrade.fillLight,
                lightingRimIntensity = template.colorGrade.rimLight,
                atmosphereFog = template.colorGrade.fog,
                lightAngleDeg = template.lightAngleDeg,
                lightElevationDeg = template.lightElevationDeg,
                cameraMovement = template.cameraMovement.code,
                cameraShotAngle = template.cameraShotAngle.code,
                cameraPitchDeg = template.cameraShotAngle.defaultPitchDeg,
                lensFocalMm = template.lensFocalMm
            )
            _sceneCameraExtras.value = _sceneCameraExtras.value + (
                updated.id to Triple(updated.cameraShotAngle, updated.cameraPitchDeg, updated.lensFocalMm)
            )
            updated
        }

        if (isGuestMode || repository == null) {
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to updatedScenes)
            _guestProjects.value = _guestProjects.value.map {
                if (it.id == project.id) it.copy(globalLightingPreset = template.colorGrade.code) else it
            }
            _selectedProject.value = project.copy(globalLightingPreset = template.colorGrade.code)
        } else {
            _scenesState.value = DataUiState.Success(updatedScenes)
            viewModelScope.launch {
                for (scene in updatedScenes) {
                    repository.updateScene(project.id, scene)
                }
                repository.updateProjectMetadata(
                    projectId = project.id,
                    title = project.title,
                    synopsis = project.synopsis,
                    targetDurationSeconds = updatedScenes.sumOf { it.durationSeconds }.coerceAtLeast(5L),
                    sceneCount = updatedScenes.size.toLong(),
                    globalLightingPreset = template.colorGrade.code
                )
            }
        }
        _aiTaskState.value = AiTaskState.Completed("تم تطبيق قالب «${template.titleAr}» على جميع مشاهد الفيلم!")
    }

    fun addNewSceneToTimeline() {
        val project = _selectedProject.value ?: return
        val currentScenes = (_scenesState.value as? DataUiState.Success)?.data ?: emptyList()
        val nextIndex = currentScenes.size.toLong()

        if (isGuestMode || repository == null) {
            val newId = "guest_scene_${UUID.randomUUID().toString().take(8)}"
            val newScene = VideoScene(
                id = newId,
                userId = "guest",
                projectId = project.id,
                sceneIndex = nextIndex,
                title = "المشهد ${nextIndex + 1}",
                narrationAr = "يستمر السرد السينمائي في هذا المشهد الجديد بإضاءة واقعية وزاوية كاميرا احترافية.",
                visualPrompt = "Wide cinematic 8K shot with dramatic volumetric studio lighting and realistic textures",
                durationSeconds = 10L,
                cameraMovement = "DOLLY_FORWARD",
                transitionType = "CROSSFADE"
            )
            val updatedList = currentScenes + newScene
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to updatedList)
            _selectedSceneId.value = newId
            recalculateGuestProjectTotals(project.id, updatedList)
        } else {
            viewModelScope.launch {
                val newScene = VideoScene(
                    sceneIndex = nextIndex,
                    title = "المشهد ${nextIndex + 1}",
                    narrationAr = "يستمر السرد السينمائي في هذا المشهد الجديد بإضاءة واقعية.",
                    visualPrompt = "Wide cinematic 8K shot with dramatic volumetric studio lighting and realistic textures",
                    durationSeconds = 10L,
                    cameraMovement = "DOLLY_FORWARD",
                    transitionType = "CROSSFADE"
                )
                val result = repository.createScene(project.id, newScene)
                result.onSuccess { id ->
                    _selectedSceneId.value = id
                    recalculateCloudProjectTotals(project)
                }
            }
        }
    }

    fun deleteSceneFromTimeline(sceneId: String) {
        val project = _selectedProject.value ?: return
        if (isGuestMode || repository == null) {
            val currentList = _guestScenesByProject.value[project.id] ?: return
            val updatedList = currentList.filterNot { it.id == sceneId }.mapIndexed { idx, s ->
                s.copy(sceneIndex = idx.toLong())
            }
            _guestScenesByProject.value = _guestScenesByProject.value + (project.id to updatedList)
            recalculateGuestProjectTotals(project.id, updatedList)
        } else {
            viewModelScope.launch {
                repository.deleteScene(project.id, sceneId)
                recalculateCloudProjectTotals(project)
            }
        }
    }

    fun deleteProject(projectId: String) {
        if (_selectedProject.value?.id == projectId) {
            closeProject()
        }
        if (isGuestMode || repository == null) {
            _guestProjects.value = _guestProjects.value.filterNot { it.id == projectId }
            _guestScenesByProject.value = _guestScenesByProject.value - projectId
        } else {
            viewModelScope.launch {
                repository.deleteProject(projectId)
            }
        }
    }

    fun deleteGeneratedVideo(videoId: String) {
        if (isGuestMode || repository == null) {
            _guestGeneratedVideos.value = _guestGeneratedVideos.value.filterNot { it.id == videoId }
        } else {
            viewModelScope.launch {
                repository.deleteGeneratedVideo(videoId)
            }
        }
    }

    fun openProjectFromGallery(video: GeneratedVideo): Boolean {
        val existingProject = (_projectsStateOrNull() ?: emptyList()).find { it.id == video.projectId }
        return if (existingProject != null) {
            openProject(existingProject)
            true
        } else {
            false
        }
    }

    suspend fun getScenesForPreview(projectId: String): List<VideoScene> {
        if (projectId.isBlank()) return emptyList()
        val currentOpenProject = _selectedProject.value
        if (currentOpenProject?.id == projectId) {
            val loaded = (_scenesState.value as? DataUiState.Success)?.data
            if (!loaded.isNullOrEmpty()) return loaded
        }
        return if (isGuestMode || repository == null) {
            (_guestScenesByProject.value[projectId] ?: emptyList())
                .sortedBy { it.sceneIndex }
                .map { applyCameraExtras(it) }
        } else {
            repository.getScenesForProject(projectId).map { applyCameraExtras(it) }
        }
    }

    private fun _projectsStateOrNull(): List<VideoProject>? =
        (projectsState.value as? DataUiState.Success)?.data

    fun exportProjectVideoMp4(context: Context) {
        val project = _selectedProject.value ?: return
        val scenes = (_scenesState.value as? DataUiState.Success)?.data ?: emptyList()
        if (scenes.isEmpty()) {
            _exportState.value = ExportMp4State.Error("المشروع لا يحتوي على مشاهد لتصديرها.")
            return
        }

        viewModelScope.launch { BackgroundWork.keepAlive("تصدير الفيديو") {
            _exportState.value = ExportMp4State.Exporting(
                progress = 0.06f,
                stageMessageAr = "تهيئة المشاهد ومصفوفة الإضاءة 3D..."
            )
            delay(220L)
            _exportState.value = ExportMp4State.Exporting(
                progress = 0.14f,
                stageMessageAr = "دمج وتصيير المقاطع السينمائية (${scenes.size} مشاهد)..."
            )

            // Free image engine fills any scene that has no generated frame yet.
            val (imgW, imgH) = when (project.aspectRatio) {
                "9:16" -> 576 to 1024
                "1:1" -> 768 to 768
                "21:9" -> 1024 to 440
                else -> 1024 to 576
            }
            for ((i, s) in scenes.withIndex()) {
                if (_sceneBitmaps.value[s.id] == null) {
                    _exportState.value = ExportMp4State.Exporting(
                        progress = 0.06f + 0.08f * (i / scenes.size.toFloat()),
                        stageMessageAr = "توليد صورة المشهد ${i + 1} من ${scenes.size}..."
                    )
                    FreeImageClient.fetch("${s.visualPrompt}, cinematic photorealistic 8k film still", imgW, imgH)
                        ?.let { _sceneBitmaps.value = _sceneBitmaps.value + (s.id to it) }
                }
            }
            _exportState.value = ExportMp4State.Exporting(
                progress = 0.14f,
                stageMessageAr = "توليد التعليق الصوتي بالصوت العربي..."
            )
            val narration = runCatching { NarrationAudio.build(context.applicationContext, scenes) }.getOrNull()

            val result = Mp4VideoExporter.exportProjectToMp4(
                context = context.applicationContext,
                project = project,
                scenes = scenes,
                sceneBitmaps = _sceneBitmaps.value,
                sceneSeconds = narration?.sceneSeconds,
                onProgress = { p ->
                    val scaled = 0.15f + (p * 0.75f)
                    _exportState.value = ExportMp4State.Exporting(
                        progress = scaled.coerceIn(0.15f, 0.90f),
                        stageMessageAr = "دمج وتصيير المقاطع السينمائية (H.264 MP4)..."
                    )
                }
            )
            val finalResult = result.map { videoFile ->
                if (narration == null) {
                    videoFile
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val merged = File(videoFile.parentFile, "cineroya_av_${System.currentTimeMillis()}.mp4")
                            NarrationAudio.mux(videoFile, narration.audioFile, merged)
                            videoFile.delete()
                            merged
                        }.getOrDefault(videoFile)
                    }
                }
            }
            finalResult.fold(
                onSuccess = { file ->
                    _exportState.value = ExportMp4State.Exporting(
                        progress = 0.94f,
                        stageMessageAr = "معالجة الإطارات النهائية وحفظ الصورة المصغرة في المعرض..."
                    )
                    val thumbnailB64 = Mp4VideoExporter.generateThumbnailBase64(
                        aspectRatio = project.aspectRatio,
                        scenes = scenes,
                        sceneBitmaps = _sceneBitmaps.value
                    )
                    val totalDuration = scenes.sumOf { it.durationSeconds }.coerceAtLeast(3L)
                    val primaryPreset = scenes.firstOrNull()?.colorGradePreset ?: project.globalLightingPreset

                    if (isGuestMode || repository == null) {
                        val entry = GeneratedVideo(
                            id = "guest_vid_${UUID.randomUUID().toString().take(8)}",
                            userId = "guest",
                            projectId = project.id,
                            title = project.title,
                            genre = project.genre,
                            aspectRatio = project.aspectRatio,
                            durationSeconds = totalDuration,
                            sceneCount = scenes.size.toLong(),
                            colorGradePreset = primaryPreset,
                            thumbnailBase64 = thumbnailB64,
                            localFilePath = file.absolutePath,
                            createdAt = Timestamp.now(),
                            updatedAt = Timestamp.now()
                        )
                        _guestGeneratedVideos.value = listOf(entry) + _guestGeneratedVideos.value
                    } else {
                        repository.saveGeneratedVideo(
                            projectId = project.id,
                            title = project.title,
                            genre = project.genre,
                            aspectRatio = project.aspectRatio,
                            durationSeconds = totalDuration,
                            sceneCount = scenes.size.toLong(),
                            colorGradePreset = primaryPreset,
                            thumbnailBase64 = thumbnailB64,
                            localFilePath = file.absolutePath
                        )
                    }
                    delay(180L)
                    _exportState.value = ExportMp4State.Completed(file)
                },
                onFailure = { err ->
                    Log.e(TAG, "MP4 Export failed", err)
                    _exportState.value = ExportMp4State.Error(err.localizedMessage ?: "فشل تصدير ملف الفيديو MP4")
                }
            )
        } }
    }

    private fun applyCameraExtras(scene: VideoScene): VideoScene {
        val extras = _sceneCameraExtras.value[scene.id] ?: return scene
        return scene.copy(
            cameraShotAngle = extras.first,
            cameraPitchDeg = extras.second,
            lensFocalMm = extras.third
        )
    }

    private fun recalculateGuestProjectTotals(projectId: String, scenes: List<VideoScene>) {
        val totalSec = scenes.sumOf { it.durationSeconds }.coerceAtLeast(5L)
        _guestProjects.value = _guestProjects.value.map { p ->
            if (p.id == projectId) {
                val updated = p.copy(
                    targetDurationSeconds = totalSec,
                    sceneCount = scenes.size.toLong(),
                    updatedAt = Timestamp.now()
                )
                if (_selectedProject.value?.id == projectId) {
                    _selectedProject.value = updated
                }
                updated
            } else {
                p
            }
        }
    }

    private suspend fun recalculateCloudProjectTotals(project: VideoProject) {
        val repo = repository ?: return
        val scenes = (_scenesState.value as? DataUiState.Success)?.data ?: return
        val totalSec = scenes.sumOf { it.durationSeconds }.coerceAtLeast(5L)
        repo.updateProjectMetadata(
            projectId = project.id,
            title = project.title,
            synopsis = project.synopsis,
            targetDurationSeconds = totalSec,
            sceneCount = scenes.size.toLong(),
            globalLightingPreset = project.globalLightingPreset
        )
    }

    companion object {
        private const val DEFAULT_GUEST_PROJECT_ID = "guest_alula_doc"

        private fun createDefaultGuestGeneratedVideos(): List<GeneratedVideo> {
            val defaultScenes = createDefaultGuestScenes()[DEFAULT_GUEST_PROJECT_ID].orEmpty()
            val thumb = try {
                Mp4VideoExporter.generateThumbnailBase64("16:9", defaultScenes, emptyMap())
            } catch (_: Exception) {
                ""
            }
            return listOf(
                GeneratedVideo(
                    id = "guest_vid_sample_1",
                    userId = "guest",
                    projectId = DEFAULT_GUEST_PROJECT_ID,
                    title = "وثائقي: أسرار العلا والحضارة النبطية (نسخة المخرج)",
                    genre = "وثائقي سينمائي",
                    aspectRatio = "16:9",
                    durationSeconds = 48L,
                    sceneCount = 4L,
                    colorGradePreset = "CINEMATIC_GOLD",
                    thumbnailBase64 = thumb,
                    localFilePath = "",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )
        }

        private fun createDefaultGuestProjects(): List<VideoProject> = listOf(
            VideoProject(
                id = DEFAULT_GUEST_PROJECT_ID,
                userId = "guest",
                title = "وثائقي: أسرار العلا والحضارة النبطية",
                synopsis = "رحلة بصرية سينمائية طويلة تستكشف الجبال الصخرية والواجهات المنحوتة في العلا من شروق الشمس الذهبي حتى سماء الليل المرصعة بالنجوم.",
                genre = "وثائقي سينمائي",
                aspectRatio = "16:9",
                visualStyle = "Photorealistic 8K IMAX",
                targetDurationSeconds = 48L,
                sceneCount = 4L,
                globalLightingPreset = "CINEMATIC_GOLD",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        )

        private fun createDefaultGuestScenes(): Map<String, List<VideoScene>> = mapOf(
            DEFAULT_GUEST_PROJECT_ID to listOf(
                VideoScene(
                    id = "guest_scene_1",
                    userId = "guest",
                    projectId = DEFAULT_GUEST_PROJECT_ID,
                    sceneIndex = 0L,
                    title = "فجر الصحراء الذهبي فوق جبال العلا",
                    narrationAr = "مع أول خيوط الفجر الذهبية، تتجلى عظمة الجبال المنحوتة في قلب الجزيرة العربية.",
                    visualPrompt = "Sweeping aerial drone shot of AlUla sandstone canyons at golden sunrise, volumetric sunbeams, 8K ARRI Alexa 65",
                    durationSeconds = 12L,
                    cameraMovement = "DOLLY_FORWARD",
                    transitionType = "CROSSFADE",
                    lightingKeyIntensity = 90L,
                    lightingFillIntensity = 48L,
                    lightingRimIntensity = 75L,
                    colorTempKelvin = 3500L,
                    lightAngleDeg = 35L,
                    lightElevationDeg = 22L,
                    atmosphereFog = 35L,
                    colorGradePreset = "CINEMATIC_GOLD",
                    cameraShotAngle = "AERIAL_DRONE",
                    cameraPitchDeg = 30L,
                    lensFocalMm = 24L
                ),
                VideoScene(
                    id = "guest_scene_2",
                    userId = "guest",
                    projectId = DEFAULT_GUEST_PROJECT_ID,
                    sceneIndex = 1L,
                    title = "الواجهات النبطية الشامخة",
                    narrationAr = "هنا نحت الأنباط تاريخهم في الصخر بدقة هندسية تدهش العالم عبر آلاف السنين.",
                    visualPrompt = "Dramatic low-angle heroic shot of Hegra Nabataean tomb facade carved in red sandstone, sharp sunlight shadows, 35mm anamorphic",
                    durationSeconds = 12L,
                    cameraMovement = "ORBIT_3D",
                    transitionType = "CROSSFADE",
                    lightingKeyIntensity = 92L,
                    lightingFillIntensity = 40L,
                    lightingRimIntensity = 80L,
                    colorTempKelvin = 4200L,
                    lightAngleDeg = 60L,
                    lightElevationDeg = 40L,
                    atmosphereFog = 20L,
                    colorGradePreset = "DESERT_WARM",
                    cameraShotAngle = "LOW_ANGLE",
                    cameraPitchDeg = -22L,
                    lensFocalMm = 35L
                ),
                VideoScene(
                    id = "guest_scene_3",
                    userId = "guest",
                    projectId = DEFAULT_GUEST_PROJECT_ID,
                    sceneIndex = 2L,
                    title = "واحة النخيل والظلال الهادئة",
                    narrationAr = "وبين أحضان الصخور، تنبض الواحة بالحياة تحت ظلال النخيل الوارفة وممرات المياه العذبة.",
                    visualPrompt = "Eye-level tracking shot through lush Arabian date palm oasis in AlUla, dappled sunlight rays filtering through leaves, 50mm prime lens",
                    durationSeconds = 12L,
                    cameraMovement = "PAN_RIGHT",
                    transitionType = "DIP_TO_BLACK",
                    lightingKeyIntensity = 80L,
                    lightingFillIntensity = 65L,
                    lightingRimIntensity = 60L,
                    colorTempKelvin = 5200L,
                    lightAngleDeg = 110L,
                    lightElevationDeg = 55L,
                    atmosphereFog = 25L,
                    colorGradePreset = "NATURAL_8K",
                    cameraShotAngle = "EYE_LEVEL",
                    cameraPitchDeg = 0L,
                    lensFocalMm = 50L
                ),
                VideoScene(
                    id = "guest_scene_4",
                    userId = "guest",
                    projectId = DEFAULT_GUEST_PROJECT_ID,
                    sceneIndex = 3L,
                    title = "سماء الليل المرصعة بالنجوم",
                    narrationAr = "وعندما يسدل الليل ستاره، تتحول الصحراء إلى مرصد كوني يروي حكايات القوافل والنجوم.",
                    visualPrompt = "Wide low-angle night shot of Elephant Rock in AlUla illuminated by warm torchlight under a glowing Milky Way galaxy sky, 16mm ultra-wide lens",
                    durationSeconds = 12L,
                    cameraMovement = "ZOOM_IN",
                    transitionType = "CROSSFADE",
                    lightingKeyIntensity = 75L,
                    lightingFillIntensity = 25L,
                    lightingRimIntensity = 90L,
                    colorTempKelvin = 6800L,
                    lightAngleDeg = 200L,
                    lightElevationDeg = 18L,
                    atmosphereFog = 45L,
                    colorGradePreset = "TEAL_ORANGE",
                    cameraShotAngle = "LOW_ANGLE",
                    cameraPitchDeg = -18L,
                    lensFocalMm = 16L
                )
            )
        )

        fun provideFactory(
            repository: CineRoyaRepository?,
            isGuestMode: Boolean = false,
            appContext: Context? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CineRoyaViewModel(
                    repository = repository,
                    isGuestMode = isGuestMode,
                    appContext = appContext
                )
            }
        }
    }
}
