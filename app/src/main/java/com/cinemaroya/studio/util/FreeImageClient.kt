package com.cinemaroya.studio.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/** Free, keyless image generation (Pollinations) used when Gemini image quota is exhausted. */
object FreeImageClient {
    suspend fun fetch(prompt: String, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        repeat(2) {
            try {
                val q = URLEncoder.encode(prompt.take(380), "UTF-8").replace("+", "%20")
                val url = URL(
                    "https://image.pollinations.ai/prompt/$q?width=$width&height=$height" +
                        "&nologo=true&seed=${Random.nextInt(1_000_000)}"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 70_000
                }
                try {
                    if (conn.responseCode in 200..299) {
                        val bmp = BitmapFactory.decodeStream(conn.inputStream)
                        if (bmp != null) return@withContext bmp
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        null
    }
}
