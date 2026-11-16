package com.cinemaroya.studio.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinemaroya.studio.R
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.ui.components.AppCheckDiagnosticsDialog
import com.cinemaroya.studio.ui.theme.CinemaGold
import com.cinemaroya.studio.ui.theme.StudioCard
import com.cinemaroya.studio.ui.theme.StudioCyan
import com.cinemaroya.studio.ui.theme.StudioElevated
import com.cinemaroya.studio.ui.theme.StudioObsidian
import com.cinemaroya.studio.util.AppCheckDiagnostics
import kotlin.math.roundToInt

private data class StoryTemplate(
    val titleAr: String,
    val genreAr: String,
    val promptAr: String,
    val aspectRatio: String
)

private val QuickTemplates = listOf(
    StoryTemplate(
        titleAr = "وثائقي: أسرار العلا والحضارة النبطية",
        genreAr = "وثائقي سينمائي",
        promptAr = "فيلم وثائقي سينمائي طويل يستكشف الجبال الصخرية والواجهات المنحوتة في العلا عند الغروب وتحت سماء الصحراء المرصعة بالنجوم، مع إضاءة ذهبية درامية وحركة كاميرا بانورامية.",
        aspectRatio = "16:9"
    ),
    StoryTemplate(
        titleAr = "خيال علمي: مدينة نيوم عام 2095",
        genreAr = "خيال علمي",
        promptAr = "رحلة بصرية مستقبلية داخل مدينة خطية ذكية معلقة وسط الجبال والبحر الأحمر، مركبات طائرة، حدائق معلقة، وإضاءة نيون سيان وذهبي بتقنية تتبع الأشعة الواقعية 8K.",
        aspectRatio = "21:9"
    ),
    StoryTemplate(
        titleAr = "إعلان فاخر: عطر العود الملكي",
        genreAr = "إعلان تجاري",
        promptAr = "إعلان سينمائي فاخر لزجاجة عطر عود عربي ملكي محاطة بدخان البخور الذهبي وقطرات الماء المتلألئة على رخام أسود مع إضاءة استوديو ثلاثية احترافية.",
        aspectRatio = "9:16"
    ),
    StoryTemplate(
        titleAr = "تاريخي ملحمي: بيت الحكمة في بغداد",
        genreAr = "تاريخي ملحمي",
        promptAr = "مشاهد ملحمية داخل قاعات بيت الحكمة التاريخي حيث العلماء والفلكيون يدرسون الإسطرلاب والمخطوطات تحت أشعة الشمس المخترقة للنوافذ المقوسة المزخرفة.",
        aspectRatio = "16:9"
    )
)

@Composable
fun ProjectHubScreen(
    isGuestMode: Boolean,
    projectsState: DataUiState<List<VideoProject>>,
    aiTaskState: AiTaskState,
    referenceBitmap: android.graphics.Bitmap?,
    appCheckDiagnostics: AppCheckDiagnostics? = null,
    appCheckTestState: AppCheckTestUiState = AppCheckTestUiState.Idle,
    onSetReferenceBitmap: (android.graphics.Bitmap?) -> Unit,
    onGenerateProject: (ideaPrompt: String, genre: String, aspectRatio: String, visualStyle: String, sceneCount: Int) -> Unit,
    onCreateManualProject: (ideaPrompt: String, genre: String, aspectRatio: String, visualStyle: String, sceneCount: Int) -> Unit,
    onOpenProject: (VideoProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    onClearAiState: () -> Unit,
    onRefreshAppCheckDiagnostics: () -> Unit = {},
    onSaveAndTestAppCheck: (modeOverride: String, customDebugToken: String, customSiteKey: String) -> Unit = { _, _, _ -> },
    onClearAppCheckTestState: () -> Unit = {},
    onSignOutOrSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAppCheckDialog by rememberSaveable { mutableStateOf(false) }
    var promptText by rememberSaveable {
        mutableStateOf("فيلم وثائقي سينمائي طويل يستكشف الجبال الصخرية والواجهات المنحوتة في العلا عند الغروب وتحت سماء الصحراء المرصعة بالنجوم، مع إضاءة ذهبية درامية.")
    }
    var selectedGenre by rememberSaveable { mutableStateOf("وثائقي سينمائي") }
    var selectedAspectRatio by rememberSaveable { mutableStateOf("16:9") }
    var selectedVisualStyle by rememberSaveable { mutableStateOf("Photorealistic 8K IMAX") }
    var requestedScenes by rememberSaveable { mutableIntStateOf(5) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        onSetReferenceBitmap(bmp)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val genres = listOf("وثائقي سينمائي", "خيال علمي", "تاريخي ملحمي", "إعلان تجاري", "دراما واقعية")
    val aspectRatios = listOf("16:9", "9:16", "21:9", "1:1")
    val styles = listOf("Photorealistic 8K IMAX", "Unreal Engine 5 PBR", "ARRI Alexa Anamorphic", "National Geographic HDR")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Studio Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.studio_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            if (isGuestMode) {
                                Surface(
                                    color = StudioCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.guest_mode_badge),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.studio_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onClearAppCheckTestState()
                                onRefreshAppCheckDiagnostics()
                                showAppCheckDialog = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StudioCard)
                                .testTag("open_appcheck_diagnostics_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "تشخيص وإعداد App Check",
                                tint = StudioCyan
                            )
                        }

                        if (isGuestMode) {
                            OutlinedButton(
                                onClick = onSignOutOrSwitchAccount,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = stringResource(R.string.link_google_account),
                                    tint = CinemaGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.link_google_account),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CinemaGold
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onSignOutOrSwitchAccount,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(StudioCard)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(R.string.sign_out),
                                    tint = CinemaGold
                                )
                            }
                        }
                    }
                }
            }

            // Hero Studio Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 155.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, CinemaGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1794558142941),
                        contentDescription = stringResource(R.string.studio_title),
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        StudioObsidian.copy(alpha = 0.92f),
                                        StudioObsidian.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            color = CinemaGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.hero_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = CinemaGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.hero_headline),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.hero_subheadline),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            // Quick Ready Templates
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.quick_templates_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaGold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickTemplates.forEach { tpl ->
                            Surface(
                                color = StudioCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .width(235.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, StudioElevated, RoundedCornerShape(14.dp))
                                    .clickable {
                                        promptText = tpl.promptAr
                                        selectedGenre = tpl.genreAr
                                        selectedAspectRatio = tpl.aspectRatio
                                    }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = tpl.titleAr,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tpl.promptAr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Long-Video Generator Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCard),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CinemaGold.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CinemaGold
                            )
                            Text(
                                text = stringResource(R.string.new_project_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            label = { Text(stringResource(R.string.story_prompt_label)) },
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CinemaGold,
                                unfocusedBorderColor = StudioElevated,
                                focusedLabelColor = CinemaGold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story_prompt_input")
                        )

                        // Reference Image Attachment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("pick_reference_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.attach_ref_image),
                                    tint = StudioCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (referenceBitmap == null) stringResource(R.string.attach_ref_image) else stringResource(R.string.change_ref_image),
                                    color = Color.White
                                )
                            }

                            if (referenceBitmap != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        bitmap = referenceBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.attach_ref_image),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, StudioCyan, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(onClick = { onSetReferenceBitmap(null) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إزالة الصورة",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.genre_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genres.forEach { genre ->
                                FilterChip(
                                    selected = selectedGenre == genre,
                                    onClick = { selectedGenre = genre },
                                    label = { Text(genre) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CinemaGold.copy(alpha = 0.22f),
                                        selectedLabelColor = CinemaGold
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.aspect_ratio_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                aspectRatios.forEach { ratio ->
                                    FilterChip(
                                        selected = selectedAspectRatio == ratio,
                                        onClick = { selectedAspectRatio = ratio },
                                        label = { Text(ratio) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = StudioCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = StudioCyan
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            styles.forEach { style ->
                                FilterChip(
                                    selected = selectedVisualStyle == style,
                                    onClick = { selectedVisualStyle = style },
                                    label = { Text(style) }
                                )
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.scene_count_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$requestedScenes مشاهد (~${requestedScenes * 12} ثانية)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CinemaGold
                                )
                            }
                            Slider(
                                value = requestedScenes.toFloat(),
                                onValueChange = { requestedScenes = it.roundToInt().coerceIn(3, 8) },
                                valueRange = 3f..8f,
                                steps = 4,
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaGold,
                                    activeTrackColor = CinemaGold
                                )
                            )
                        }

                        when (aiTaskState) {
                            is AiTaskState.Running -> {
                                Surface(
                                    color = StudioElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = CinemaGold,
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = aiTaskState.statusMessageAr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            is AiTaskState.Failed -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp),
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
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "إغلاق",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                                                    .testTag("error_open_appcheck_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = CinemaGold,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "تشخيص وإعداد App Check ونسخ البصمة / الرمز",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CinemaGold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }

                        Button(
                            onClick = {
                                onGenerateProject(
                                    promptText,
                                    selectedGenre,
                                    selectedAspectRatio,
                                    selectedVisualStyle,
                                    requestedScenes
                                )
                            },
                            enabled = aiTaskState !is AiTaskState.Running,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinemaGold,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .testTag("generate_long_video_button")
                        ) {
                            Icon(imageVector = Icons.Default.MovieFilter, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.generate_long_video_btn),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onCreateManualProject(
                                    promptText,
                                    selectedGenre,
                                    selectedAspectRatio,
                                    selectedVisualStyle,
                                    requestedScenes
                                )
                            },
                            enabled = aiTaskState !is AiTaskState.Running,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("create_manual_project_button")
                        ) {
                            Text(
                                text = "إنشاء مشروع تحرير يدوي بالخط الزمني (بدون AI)",
                                style = MaterialTheme.typography.labelLarge,
                                color = StudioCyan,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Projects List Header
            item {
                Text(
                    text = if (isGuestMode) stringResource(R.string.saved_projects_title_guest) else stringResource(R.string.saved_projects_title_cloud),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            when (projectsState) {
                is DataUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CinemaGold)
                        }
                    }
                }
                is DataUiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = projectsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is DataUiState.Success -> {
                    if (projectsState.data.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = StudioCard),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MovieFilter,
                                        contentDescription = null,
                                        tint = CinemaGold,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.empty_projects_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.empty_projects_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(projectsState.data, key = { it.id }) { project ->
                            ProjectItemCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onDelete = { onDeleteProject(project.id) }
                            )
                        }
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
}

@Composable
private fun ProjectItemCard(
    project: VideoProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = CinemaGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${project.genre} • ${project.visualStyle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioCyan
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف المشروع",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (project.synopsis.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = StudioElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = CinemaGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${project.targetDurationSeconds} ثانية • ${project.sceneCount} مشاهد",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    color = StudioElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${project.aspectRatio} • ${project.globalLightingPreset}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
