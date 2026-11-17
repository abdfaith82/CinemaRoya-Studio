package com.cinemaroya.studio.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cinemaroya.studio.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the app process alive (foreground service + wake lock) while AI / video jobs run,
 * so generation continues when the user leaves the app or turns the screen off.
 */
object BackgroundWork {
    private var appContext: Context? = null
    private val active = AtomicInteger(0)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun <T> keepAlive(label: String, block: suspend () -> T): T {
        begin(label)
        try {
            return block()
        } finally {
            end()
        }
    }

    private fun begin(label: String) {
        val ctx = appContext ?: return
        if (active.incrementAndGet() == 1) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, KeepAliveService::class.java).putExtra(KeepAliveService.EXTRA_LABEL, label)
                )
            }
        }
    }

    private fun end() {
        val ctx = appContext ?: return
        if (active.decrementAndGet() <= 0) {
            active.set(0)
            runCatching { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) }
        }
    }
}

class KeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "جاري العمل"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "المعالجة في الخلفية", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CinemaRoya Studio")
            .setContentText("$label… يمكنك مغادرة التطبيق")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CinemaRoya:keepalive").apply {
                acquire(30 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        private const val CHANNEL_ID = "background_work"
        private const val NOTIFICATION_ID = 4101
    }
}
