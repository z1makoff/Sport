package com.z1makoff.sport.service

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.z1makoff.sport.MainActivity
import com.z1makoff.sport.R
import com.z1makoff.sport.core.formatTime

/**
 * Keeps the app alive while a workout runs with the screen off or another app open,
 * and shows the running time in the notification. The time itself lives in the
 * run screen; this service only mirrors it.
 */
class TimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val elapsed = intent?.getLongExtra(EXTRA_ELAPSED, 0L) ?: 0L
        val running = intent?.getBooleanExtra(EXTRA_RUNNING, true) ?: true
        val notification = build(title, elapsed, running)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    private fun build(title: String, elapsedMs: Long, running: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (running) {
            b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - elapsedMs)
                .setContentText(getString(R.string.notif_running))
        } else {
            b.setUsesChronometer(false).setShowWhen(false)
                .setContentText(getString(R.string.notif_paused, formatTime(elapsedMs)))
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ELAPSED = "elapsed"
        private const val EXTRA_RUNNING = "running"

        fun show(context: Context, title: String, elapsedMs: Long, running: Boolean) {
            val i = Intent(context, TimerService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ELAPSED, elapsedMs)
                .putExtra(EXTRA_RUNNING, running)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, TimerService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
