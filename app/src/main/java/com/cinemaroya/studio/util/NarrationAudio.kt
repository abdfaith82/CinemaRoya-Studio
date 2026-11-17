package com.cinemaroya.studio.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cinemaroya.studio.model.VideoScene
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Builds an AAC narration track from each scene's Arabic text using the device text-to-speech voice. */
object NarrationAudio {

    data class Result(val audioFile: File, val sceneSeconds: List<Int>)

    suspend fun build(context: Context, scenes: List<VideoScene>): Result? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val tts = initTts(app) ?: return@withContext null
        try {
            val dir = File(app.cacheDir, "narration").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val pcms = ArrayList<ShortArray>()
            var sampleRate = 0
            scenes.forEachIndexed { i, scene ->
                ensureActive()
                val text = scene.narrationAr.trim()
                var pcm = ShortArray(0)
                if (text.isNotEmpty()) {
                    val wav = File(dir, "s$i.wav")
                    if (synth(tts, text, wav, "u$i")) {
                        val decoded = readWav(wav)
                        if (decoded != null) {
                            if (sampleRate == 0) sampleRate = decoded.first
                            if (decoded.first == sampleRate) pcm = decoded.second
                        }
                    }
                }
                pcms += pcm
            }
            if (sampleRate == 0 || pcms.all { it.isEmpty() }) return@withContext null

            val sceneSeconds = pcms.mapIndexed { i, pcm ->
                val narrationSec = if (pcm.isEmpty()) 0 else ceil(pcm.size / sampleRate.toDouble()).toInt() + 1
                max(scenes[i].durationSeconds.coerceIn(3L, 8L).toInt(), narrationSec).coerceAtMost(30)
            }
            val timeline = ShortArray(sceneSeconds.sum() * sampleRate)
            var start = 0
            pcms.forEachIndexed { i, pcm ->
                val n = min(pcm.size, timeline.size - start)
                if (n > 0) pcm.copyInto(timeline, start, 0, n)
                start += sceneSeconds[i] * sampleRate
            }
            val out = File(dir, "narration.m4a")
            encodeAac(timeline, sampleRate, out)
            Result(out, sceneSeconds)
        } finally {
            runCatching { tts.shutdown() }
        }
    }

    private suspend fun initTts(ctx: Context): TextToSpeech? = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Boolean>()
        val engine = TextToSpeech(ctx) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        val ok = withTimeoutOrNull(10_000) { ready.await() } ?: false
        if (!ok) {
            engine.shutdown()
            return@withContext null
        }
        val r = engine.setLanguage(Locale("ar"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.shutdown()
            return@withContext null
        }
        engine
    }

    private suspend fun synth(tts: TextToSpeech, text: String, out: File, id: String): Boolean {
        val done = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.complete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.complete(false)
            }
        })
        val r = tts.synthesizeToFile(text, Bundle(), out, id)
        if (r != TextToSpeech.SUCCESS) return false
        return withTimeoutOrNull(60_000) { done.await() } ?: false
    }

    private fun readWav(f: File): Pair<Int, ShortArray>? {
        val b = f.readBytes()
        if (b.size < 44) return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 0
        var ch = 1
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") {
                ch = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataStart = pos + 8
                dataLen = if (len <= 0 || dataStart + len > b.size) b.size - dataStart else len
                break
            }
            if (len < 0) break
            pos += 8 + len + (len and 1)
        }
        if (dataStart < 0 || rate <= 0 || bits != 16 || ch < 1) return null
        val frames = dataLen / (2 * ch)
        val out = ShortArray(frames)
        for (i in 0 until frames) out[i] = bb.getShort(dataStart + i * 2 * ch)
        return rate to out
    }

    private fun encodeAac(pcm: ShortArray, sampleRate: Int, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var inPos = 0
        var inputDone = false
        try {
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val n = min(buf.capacity() / 2, pcm.size - inPos)
                        val ptsUs = inPos * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (i in 0 until n) buf.putShort(pcm[inPos + i])
                            codec.queueInputBuffer(idx, 0, n * 2, ptsUs, 0)
                            inPos += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /** Merges the silent H.264 video with the AAC narration into one MP4. */
    fun mux(video: File, audio: File, out: File) {
        val vEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val aEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        var muxer: MediaMuxer? = null
        var started = false
        try {
            val vi = firstTrack(vEx, "video/")
            val ai = firstTrack(aEx, "audio/")
            require(vi >= 0 && ai >= 0) { "تعذر دمج الصوت مع الفيديو" }
            vEx.selectTrack(vi)
            aEx.selectTrack(ai)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vt = muxer.addTrack(vEx.getTrackFormat(vi))
            val at = muxer.addTrack(aEx.getTrackFormat(ai))
            muxer.start()
            started = true
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            while (!(vDone && aDone)) {
                val vTime = if (vDone) Long.MAX_VALUE else vEx.sampleTime
                val aTime = if (aDone) Long.MAX_VALUE else aEx.sampleTime
                if (!vDone && vTime < 0) { vDone = true; continue }
                if (!aDone && aTime < 0) { aDone = true; continue }
                val fromVideo = vTime <= aTime
                val ex = if (fromVideo) vEx else aEx
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    if (fromVideo) vDone = true else aDone = true
                    continue
                }
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_SYNC_FRAME
                } else 0
                info.set(0, size, ex.sampleTime, flags)
                muxer.writeSampleData(if (fromVideo) vt else at, buf, info)
                ex.advance()
            }
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { vEx.release() }
            runCatching { aEx.release() }
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}
