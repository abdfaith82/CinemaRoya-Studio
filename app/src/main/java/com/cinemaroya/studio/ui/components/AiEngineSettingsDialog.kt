package com.cinemaroya.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cinemaroya.studio.data.ai.AiEngine
import com.cinemaroya.studio.data.ai.AiEngineSettings

@Composable
fun AiEngineSettingsDialog(onDismiss: () -> Unit) {
    var engine by remember { mutableStateOf(AiEngineSettings.engine) }
    var autoFallback by remember { mutableStateOf(AiEngineSettings.autoFallback) }
    val keys = remember {
        mutableStateMapOf<AiEngine, String>().also { map ->
            AiEngine.entries.filter { it.isExternal }.forEach { map[it] = AiEngineSettings.apiKey(it) }
        }
    }
    val models = remember {
        mutableStateMapOf<AiEngine, String>().also { map ->
            AiEngine.entries.filter { it.isExternal }.forEach { map[it] = AiEngineSettings.model(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محركات الذكاء الاصطناعي") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "اختر المحرك الأساسي لكتابة السيناريو والمشاهد. المفاتيح مجانية ومحفوظة على جهازك فقط.",
                    style = MaterialTheme.typography.bodySmall
                )
                AiEngine.entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { engine = item },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = engine == item, onClick = { engine = item })
                        Text(text = item.label, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (item.isExternal) {
                        OutlinedTextField(
                            value = keys[item].orEmpty(),
                            onValueChange = { keys[item] = it },
                            label = { Text("مفتاح API  (${item.keyHint})") },
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
                        Spacer(modifier = Modifier.height(4.dp))
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AiEngineSettings.engine = engine
                AiEngineSettings.autoFallback = autoFallback
                AiEngine.entries.filter { it.isExternal }.forEach {
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
