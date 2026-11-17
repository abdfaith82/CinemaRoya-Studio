package com.cinemaroya.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cinemaroya.studio.data.repository.CineRoyaRepository
import com.cinemaroya.studio.ui.AuthScreen
import com.cinemaroya.studio.ui.CineRoyaViewModel
import com.cinemaroya.studio.ui.ProjectHubScreen
import com.cinemaroya.studio.ui.ProjectsGalleryScreen
import com.cinemaroya.studio.ui.StudioEditorScreen
import com.cinemaroya.studio.ui.signOutUser
import com.cinemaroya.studio.ui.theme.CinemaGold
import com.cinemaroya.studio.ui.theme.MyApplicationTheme
import com.cinemaroya.studio.ui.theme.StudioCard
import com.cinemaroya.studio.ui.theme.StudioElevated
import com.cinemaroya.studio.ui.theme.StudioObsidian
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.cinemaroya.studio.background.BackgroundWork
import com.cinemaroya.studio.data.ai.AiEngineSettings
import com.cinemaroya.studio.data.video.VideoClipManager
import com.cinemaroya.studio.data.video.VideoEngineStore
import com.cinemaroya.studio.util.CrashReportDialog
import com.cinemaroya.studio.util.CrashReporter
import com.cinemaroya.studio.util.configureAppCheck
import com.cinemaroya.studio.util.resolveFirestoreDatabaseId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashReporter.install(this)
        AiEngineSettings.init(this)
        BackgroundWork.init(this)
        VideoEngineStore.init(this)
        VideoClipManager.init(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val pendingCrashReport = CrashReporter.readPending(this)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme {
                    var crashReport by remember { mutableStateOf(pendingCrashReport) }
                    CineRoyaAppNavigation()
                    crashReport?.let { report ->
                        CrashReportDialog(
                            report = report,
                            onContinue = {
                                CrashReporter.clear(this@MainActivity)
                                crashReport = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CineRoyaAppNavigation() {
    val context = LocalContext.current
    val isFirebaseInitialized = remember {
        runCatching { com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
    var currentUser by remember {
        mutableStateOf(if (isFirebaseInitialized) Firebase.auth.currentUser else null)
    }
    var isGuestSession by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(isFirebaseInitialized) {
        if (!isFirebaseInitialized) {
            onDispose { }
        } else {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
                if (auth.currentUser != null) {
                    isGuestSession = false
                }
            }
            Firebase.auth.addAuthStateListener(listener)
            onDispose {
                Firebase.auth.removeAuthStateListener(listener)
            }
        }
    }

    val user = currentUser
    if (user == null && !isGuestSession) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = if (isFirebaseInitialized) Firebase.auth.currentUser else null
                isGuestSession = false
            },
            onContinueAsGuest = {
                isGuestSession = true
            }
        )
    } else {
        val sessionKey = user?.uid ?: "guest_local_session"
        val isGuest = user == null
        key(sessionKey) {
            StudioSessionContainer(
                sessionKey = sessionKey,
                isGuestMode = isGuest,
                onExitGuestMode = {
                    isGuestSession = false
                }
            )
        }
    }
}

@Composable
private fun StudioSessionContainer(
    sessionKey: String,
    isGuestMode: Boolean,
    onExitGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: CineRoyaViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                if (isGuestMode) {
                    CineRoyaViewModel(
                        repository = null,
                        isGuestMode = true,
                        appContext = app
                    )
                } else {
                    val databaseId = resolveFirestoreDatabaseId(app)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    CineRoyaViewModel(
                        repository = CineRoyaRepository(db),
                        isGuestMode = false,
                        appContext = app
                    )
                }
            }
        }
    )

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val generatedVideosState by viewModel.generatedVideosState.collectAsStateWithLifecycle()
    val selectedProject by viewModel.selectedProject.collectAsStateWithLifecycle()
    val scenesState by viewModel.scenesState.collectAsStateWithLifecycle()
    val selectedSceneId by viewModel.selectedSceneId.collectAsStateWithLifecycle()
    val sceneBitmaps by viewModel.sceneBitmaps.collectAsStateWithLifecycle()
    val referenceBitmap by viewModel.referenceBitmap.collectAsStateWithLifecycle()
    val aiTaskState by viewModel.aiTaskState.collectAsStateWithLifecycle()
    val promptSequenceText by viewModel.promptSequenceText.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val appCheckDiagnostics by viewModel.appCheckDiagnostics.collectAsStateWithLifecycle()
    val appCheckTestState by viewModel.appCheckTestState.collectAsStateWithLifecycle()

    // 0 = Studio Hub / Create, 1 = Projects Gallery (Generated Videos)
    var rootTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val activeProject = selectedProject
    if (activeProject == null) {
        Scaffold(
            containerColor = StudioObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar(
                    containerColor = StudioCard,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = rootTabIndex == 0,
                        onClick = { rootTabIndex = 0 },
                        icon = {
                            Icon(
                                imageVector = if (rootTabIndex == 0) Icons.Filled.MovieFilter else Icons.Outlined.MovieFilter,
                                contentDescription = stringResource(R.string.nav_studio_hub)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_studio_hub)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = StudioObsidian,
                            selectedTextColor = CinemaGold,
                            indicatorColor = CinemaGold,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.testTag("nav_tab_hub")
                    )
                    NavigationBarItem(
                        selected = rootTabIndex == 1,
                        onClick = { rootTabIndex = 1 },
                        icon = {
                            Icon(
                                imageVector = if (rootTabIndex == 1) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                contentDescription = stringResource(R.string.nav_projects_gallery)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_projects_gallery)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = StudioObsidian,
                            selectedTextColor = CinemaGold,
                            indicatorColor = CinemaGold,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.testTag("nav_tab_gallery")
                    )
                }
            }
        ) { innerPadding ->
            if (rootTabIndex == 0) {
                ProjectHubScreen(
                    isGuestMode = isGuestMode,
                    projectsState = projectsState,
                    aiTaskState = aiTaskState,
                    referenceBitmap = referenceBitmap,
                    appCheckDiagnostics = appCheckDiagnostics,
                    appCheckTestState = appCheckTestState,
                    onSetReferenceBitmap = viewModel::setReferenceBitmap,
                    onGenerateProject = viewModel::generateFullAiProjectFromPrompt,
                    onCreateManualProject = viewModel::createManualProjectFromIdea,
                    onOpenProject = viewModel::openProject,
                    onDeleteProject = viewModel::deleteProject,
                    onClearAiState = viewModel::clearAiTaskState,
                    onRefreshAppCheckDiagnostics = { viewModel.refreshAppCheckDiagnostics(context) },
                    onSaveAndTestAppCheck = { mode, debugTok, siteKey ->
                        viewModel.saveAndTestAppCheckSettings(context, mode, debugTok, siteKey)
                    },
                    onClearAppCheckTestState = viewModel::clearAppCheckTestState,
                    onSignOutOrSwitchAccount = {
                        if (isGuestMode) {
                            onExitGuestMode()
                        } else {
                            signOutUser(credentialManager = credentialManager, scope = scope)
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                ProjectsGalleryScreen(
                    isGuestMode = isGuestMode,
                    generatedVideosState = generatedVideosState,
                    onOpenVideoProject = { video ->
                        viewModel.openProjectFromGallery(video)
                    },
                    onDeleteVideo = viewModel::deleteGeneratedVideo,
                    onBackToHub = { rootTabIndex = 0 },
                    sceneBitmaps = sceneBitmaps,
                    onLoadScenesForPreview = viewModel::getScenesForPreview,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    } else {
        StudioEditorScreen(
            project = activeProject,
            scenesState = scenesState,
            selectedSceneId = selectedSceneId,
            sceneBitmaps = sceneBitmaps,
            aiTaskState = aiTaskState,
            promptSequenceText = promptSequenceText,
            exportState = exportState,
            onSelectScene = viewModel::selectScene,
            onUpdateScene = viewModel::updateSceneRealtime,
            onMoveSceneOrder = viewModel::moveSceneOrder,
            onDuplicateScene = viewModel::duplicateSceneInTimeline,
            onApplyLightingPreset = viewModel::applyLightingPresetToScene,
            onApplyCameraShotAnglePreset = viewModel::applyCameraShotAnglePreset,
            onApplyCinematicTemplateToScene = viewModel::applyCinematicTemplateToScene,
            onApplyCinematicTemplateToAllScenes = viewModel::applyCinematicTemplateToAllScenes,
            onGenerateAiSceneFrame = viewModel::generateAiFrameForScene,
            onGeneratePromptSequence = viewModel::generateProjectPromptSequence,
            onDismissPromptSequence = viewModel::dismissPromptSequenceDialog,
            onApplyPhotoDirectlyToScene = viewModel::applyReferenceImageDirectlyToScene,
            onEnhanceSceneWithAi = viewModel::enhanceSceneWithAiDirector,
            onAddNewScene = viewModel::addNewSceneToTimeline,
            onDeleteScene = viewModel::deleteSceneFromTimeline,
            onExportMp4 = { viewModel.exportProjectVideoMp4(context) },
            onClearAiState = viewModel::clearAiTaskState,
            onClearExportState = viewModel::clearExportState,
            onOpenGalleryAfterExport = {
                viewModel.closeProject()
                rootTabIndex = 1
            },
            onBackToHub = viewModel::closeProject,
            appCheckDiagnostics = appCheckDiagnostics,
            appCheckTestState = appCheckTestState,
            onRefreshAppCheckDiagnostics = { viewModel.refreshAppCheckDiagnostics(context) },
            onSaveAndTestAppCheck = { mode, debugTok, siteKey ->
                viewModel.saveAndTestAppCheckSettings(context, mode, debugTok, siteKey)
            },
            onClearAppCheckTestState = viewModel::clearAppCheckTestState
        )
    }
}
