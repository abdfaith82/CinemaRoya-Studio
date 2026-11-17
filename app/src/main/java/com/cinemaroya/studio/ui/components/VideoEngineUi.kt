package com.cinemaroya.studio.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cinemaroya.studio.data.video.ClipUi
import com.cinemaroya.studio.data.video.SpaceCandidate
import com.cinemaroya.studio.data.video.SpaceInspector
import com.cinemaroya.studio.data.video.SpaceSearch
import com.cinemaroya.studio.data.video.VideoClipManager
import com.cinemaroya.studio.data.video.VideoEngineStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SceneClipSection(sceneId: String, prompt: String) {
    val context = LocalContext.current
    val clips by VideoClipManager.clips.collectAsState()
    val ui = clips[sceneId] ?: ClipUi()
    val path = ui.path ?: VideoClipManager.existingClip(sceneId)
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "مقطع فيديو للمشهد (محرك مجاني)",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { VideoClipManager.generate(sceneId, prompt) },
                enabled = !ui.running
            ) { Text(if (ui.running) "جارٍ التوليد…" else "توليد مقطع") }
            if (path != null) {
                OutlinedButton(onClick = { openClip(context, path) }) { Text("تشغيل") }
            }
            TextButton(onClick = { showSettings = true }) { Text("إعدادات") }
        }
        if (ui.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (ui.status.isNotBlank()) {
            Text(ui.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (showSettings) VideoEngineDialog(onDismiss = { showSettings = false })
}

private fun openClip(context: Context, path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "تشغيل المقطع"))
    }
}

@Composable
fun VideoEngineDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val initial = remember { VideoEngineStore.load() }
    var url by remember { mutableStateOf(initial.spaceUrl) }
    var api by remember { mutableStateOf(initial.apiName) }
    var token by remember { mutableStateOf(initial.hfToken) }
    var template by remember { mutableStateOf(initial.dataTemplate) }
    var info by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<SpaceCandidate>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محرك الفيديو المجاني") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "يعتمد على Spaces مجانية في Hugging Face. قد تتوقف أو تنفد حصة GPU، فاستعمل البحث لإيجاد بديل حيّ.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("رابط الـ Space أو owner/name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = api, onValueChange = { api = it },
                    label = { Text("api_name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("توكن Hugging Face (اختياري)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = template, onValueChange = { template = it },
                    label = { Text("قالب البيانات (JSON)") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                info = "جاري قراءة واجهة الـ Space…"
                                try {
                                    val eps = SpaceInspector().inspect(url, token)
                                    val ep = eps.firstOrNull { it.returnsVideo } ?: eps.firstOrNull()
                                        ?: error("لا توجد دوال في هذا الـ Space")
                                    url = SpaceInspector.normalizeUrl(url)
                                    api = ep.apiName
                                    template = SpaceInspector.buildTemplate(ep)
                                    info = "تم ضبط الدالة ${ep.apiName}" +
                                        if (ep.returnsVideo) "" else " (تحذير: لا تُرجع فيديو)"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    info = e.message ?: "فشل الاكتشاف"
                                }
                                busy = false
                            }
                        }
                    ) { Text("اكتشاف تلقائي") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                info = "جاري البحث عن Spaces حيّة…"
                                try {
                                    candidates = SpaceSearch().search("text to video", token) { info = it }
                                    info = if (candidates.isEmpty()) "لم أجد Space صالحاً الآن" else "اختر أحد النتائج"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    info = e.message ?: "فشل البحث"
                                }
                                busy = false
                            }
                        }
                    ) { Text("بحث عن Spaces") }
                }
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.labelSmall)
                candidates.forEach { c ->
                    TextButton(onClick = {
                        url = c.url
                        api = c.ep.apiName
                        template = SpaceInspector.buildTemplate(c.ep)
                        info = "تم اختيار ${c.id}"
                    }) { Text("${c.id}   ♥ ${c.likes}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                VideoEngineStore.save(
                    VideoEngineStore.load().copy(
                        spaceUrl = SpaceInspector.normalizeUrl(url).ifBlank { url },
                        apiName = api,
                        dataTemplate = template,
                        hfToken = token
                    )
                )
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
