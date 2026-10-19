package com.memdroid.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.memdroid.app.MainActivity
import com.memdroid.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Stops the apps on the auto-kill list when the screen turns off or RAM runs low.
 * Android only delivers screen-off events to a running app, so this runs as a
 * foreground service with a quiet ongoing notification while a rule is on.
 */
class AutoKillService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingScreenOff: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> if (Prefs.get(context).autoKillOnScreenOff.value) {
                    // A short grace period so a quick glance-and-lock doesn't kill anything.
                    pendingScreenOff = scope.launch {
                        delay(SCREEN_OFF_DELAY_MS)
                        runRules(context, context.getString(R.string.c_autokill_reason_screen_off))
                    }
                }
                Intent.ACTION_SCREEN_ON -> pendingScreenOff?.cancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ShizukuShell.init()
        startForeground(
            NOTIFICATION_ID, notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            while (isActive) {
                delay(LOW_RAM_CHECK_MS)
                val prefs = Prefs.get(this@AutoKillService)
                if (!prefs.autoKillOnLowRam.value) continue
                val ram = RamRepository(this@AutoKillService).systemRam()
                val availablePercent = 100 - (ram.usedFraction * 100).toInt()
                if (availablePercent < prefs.autoKillRamPercent.value) runRules(this@AutoKillService, getString(R.string.c_autokill_reason_low_ram))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.get(this).autoKillActive) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.c_autokill_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.c_autokill_notification_title))
            .setContentText(getString(R.string.c_autokill_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "auto_kill"
        private const val NOTIFICATION_ID = 10
        private const val SCREEN_OFF_DELAY_MS = 30_000L
        private const val LOW_RAM_CHECK_MS = 60_000L

        /** Starts or stops the service to match the current rules. */
        fun sync(context: Context) {
            val intent = Intent(context, AutoKillService::class.java)
            if (Prefs.get(context).autoKillActive) context.startForegroundService(intent)
            else context.stopService(intent)
        }

        /** Stops every auto-kill app that isn't whitelisted. Returns how many were stopped. */
        suspend fun runRules(context: Context, reason: String): Int {
            val prefs = Prefs.get(context)
            if (ShizukuShell.state.value != ShizukuState.READY) return 0
            val targets = prefs.autoKillApps.value - prefs.whitelist.value
            if (targets.isEmpty()) return 0
            val ram = RamRepository(context)
            val stopped = targets.count { ram.forceStop(it) }
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            prefs.setAutoKillLog(
                context.resources.getQuantityString(R.plurals.c_autokill_log, targets.size, time, reason, stopped, targets.size)
            )
            return stopped
        }
    }
}

/** Restarts auto-kill after a reboot if a rule is on. Shizuku must be started again before it can stop apps. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AutoKillService.sync(context)
    }
}
