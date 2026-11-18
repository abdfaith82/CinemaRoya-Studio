package com.cinemaroya.studio.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cinemaroya.studio.data.ai.AiEngine
import com.cinemaroya.studio.data.ai.AiEngineSettings
import com.cinemaroya.studio.data.ai.EngineRouter
import com.cinemaroya.studio.data.ai.RouteStrategy
import com.cinemaroya.studio.data.video.KeyImporter
import com.cinemaroya.studio.data.video.VideoEngineStore

@Composable
fun AiEngineSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pasteInfo by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf(AiEngineSettings.engine) }
    var strategy by remember { mutableStateOf(AiEngineSettings.strategy) }
    var autoFallback by remember { mutableStateOf(AiEngineSettings.autoFallback) }
    var customUrl by remember { mutableStateOf(AiEngineSettings.customBaseUrl) }
    var expanded by remember { mutableStateOf<AiEngine?>(null) }
    val external = remember { AiEngine.entries.filter { it.isExternal } }
    val keys = remember {
        mutableStateMapOf<AiEngine, String>().also { m -> external.forEach { m[it] = AiEngineSettings.apiKey(it) } }
    }
    val models = remember {
        mutableStateMapOf<AiEngine, String>().also { m -> external.forEach { m[it] = AiEngineSettings.model(it) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محركات الذكاء الاصطناعي") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "المفاتيح مجانية ومشفّرة على جهازك. اختر المحرك الأساسي وطريقة التوجيه بين المحركات.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    val found = KeyImporter.importFrom(text)
                    found.forEach { (kind, key) ->
                        if (kind == "hf") {
                            keys[AiEngine.HUGGINGFACE] = key
                            VideoEngineStore.saveToken(key)
                        } else {
                            keys[AiEngine.fromCode(kind)] = key
                        }
                    }
                    pasteInfo = if (found.isEmpty()) "لم أجد مفاتيح معروفة في الحافظة"
                    else "تم التعرف على ${found.size} مفتاح، اضغط حفظ"
                }) { Text("لصق كل المفاتيح من الحافظة") }
                if (pasteInfo.isNotBlank()) Text(pasteInfo, style = MaterialTheme.typography.labelSmall)

                Text("طريقة التوجيه", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteStrategy.entries.forEach { item ->
                        if (strategy == item) {
                            Button(onClick = { strategy = item }) { Text(item.label) }
                        } else {
                            OutlinedButton(onClick = { strategy = item }) { Text(item.label) }
                        }
                    }
                }

                AiEngine.entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { engine = item },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = engine == item, onClick = { engine = item })
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.isExternal) {
                            val hasKey = if (item.custom) customUrl.isNotBlank() else !keys[item].isNullOrBlank()
                            TextButton(onClick = { expanded = if (expanded == item) null else item }) {
                                Text(if (hasKey) "✓ تعديل" else "إضافة مفتاح")
                            }
                        }
                    }
                    val note = EngineRouter.note(item)
                    if (note.isNotBlank()) {
                        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (item.isExternal && expanded == item) {
                        if (item.custom) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text("عنوان الخادم (مثل http://192.168.1.5:11434/v1)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = keys[item].orEmpty(),
                            onValueChange = { keys[item] = it },
                            label = { Text(if (item.custom) "مفتاح API (اختياري)" else "مفتاح API  (${item.keyHint})") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = models[item].orEmpty(),
                            onValueChange = { models[item] = it },
                            label = { Text("اسم النموذج") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "التحويل التلقائي لمحرك آخر عند نفاد الحصة أو الخطأ",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = autoFallback, onCheckedChange = { autoFallback = it })
                }
                TextButton(onClick = { EngineRouter.resetAll() }) { Text("إعادة ضبط حالة المحركات (الراحة والتعطيل)") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AiEngineSettings.engine = engine
                AiEngineSettings.strategy = strategy
                AiEngineSettings.autoFallback = autoFallback
                AiEngineSettings.customBaseUrl = customUrl
                external.forEach {
                    AiEngineSettings.setApiKey(it, keys[it].orEmpty())
                    AiEngineSettings.setModel(it, models[it].orEmpty())
                }
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
