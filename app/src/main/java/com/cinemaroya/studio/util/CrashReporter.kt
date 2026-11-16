package com.cinemaroya.studio.util

import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Stores the stack trace of an uncaught exception in app-private storage so the next launch
 * can show it on screen (selectable/copyable) without needing adb.
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(appContext.filesDir, FILE_NAME)
                    .writeText("Thread: ${thread.name}\n${throwable.stackTraceToString()}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readPending(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}

@Composable
fun CrashReportDialog(report: String, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("حدث انهيار في التشغيل السابق") },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = report, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("متابعة") }
        }
    )
}
