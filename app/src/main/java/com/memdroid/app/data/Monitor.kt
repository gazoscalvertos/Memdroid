package com.memdroid.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memdroid.app.MainActivity
import com.memdroid.app.R
import com.memdroid.app.widget.MemdroidWidget
import java.util.concurrent.TimeUnit

/** Runs every 30 minutes: records history, checks alert thresholds and refreshes the widget. */
class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val snapshot = HistoryStore.capture(applicationContext)
        HistoryStore.append(applicationContext, snapshot)
        Alerts.check(applicationContext, snapshot)
        AppHistoryStore.recordIfDue(applicationContext)
        MemdroidWidget.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("monitor", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object Alerts {
    private const val CHANNEL = "alerts"
    private const val STORAGE = "storage"
    private const val RAM = "ram"

    fun check(context: Context, snapshot: Snapshot) {
        val prefs = Prefs.get(context)
        if (!prefs.alertsEnabled.value) return

        val lowStorage = snapshot.storageFree < prefs.storageAlertGb.value * 1_000_000_000L
        update(context, prefs, STORAGE, lowStorage, 1,
            "Storage is running low",
            "Only ${formatBytes(snapshot.storageFree)} free. Open Memdroid to free up space.")

        val lowRam = snapshot.ramAvailablePercent < prefs.ramAlertPercent.value
        update(context, prefs, RAM, lowRam, 2,
            "Memory is running low",
            "Only ${snapshot.ramAvailablePercent}% of RAM is available. Open Memdroid to stop apps.")
    }

    /** Notifies once when a value first goes low, and again only after it has recovered. */
    private fun update(context: Context, prefs: Prefs, key: String, low: Boolean, id: Int, title: String, text: String) {
        val wasLow = prefs.alertActive(key)
        if (low && !wasLow) notify(context, id, title, text)
        if (low != wasLow) prefs.setAlertActive(key, low)
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Low storage and memory", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked.
        }
    }
}
