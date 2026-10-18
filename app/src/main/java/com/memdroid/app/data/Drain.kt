package com.memdroid.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Compares each app's battery use between background checks and alerts when an app that wasn't
 * opened in that time used more than the chosen mAh per hour. Needs Shizuku and usage access.
 */
object DrainMonitor {
    private fun file(context: Context) = File(context.filesDir, "drain_baseline.json")

    suspend fun check(context: Context) {
        val prefs = Prefs.get(context)
        if (!prefs.alertsEnabled.value || !prefs.drainAlertsEnabled.value) return
        if (ShizukuShell.state.value != ShizukuState.READY || !hasUsageAccess(context)) return

        val usage = BatteryRepository(context).usageByPackage() ?: return
        val now = System.currentTimeMillis()
        val current = usage.mapValues { it.value.second }
        val previous = load(context)
        save(context, now, current)
        if (previous == null) return

        val (since, before) = previous
        val hours = (now - since) / 3_600_000.0
        // Too short to judge, or too old to be a fair comparison.
        if (hours < 0.25 || hours > 3) return
        // Battery stats reset when the phone is charged; start again from the new baseline.
        if (current.any { (pkg, mah) -> mah < (before[pkg] ?: 0.0) }) return

        val opened = openedApps(context, since, now)
        val today = TimeUnit.MILLISECONDS.toDays(now)
        val threshold = prefs.drainThresholdMah.value
        usage.forEach { (pkg, entry) ->
            if (pkg == context.packageName || pkg in opened) return@forEach
            val perHour = (entry.second - (before[pkg] ?: 0.0)) / hours
            if (perHour >= threshold && prefs.drainNotifiedDay(pkg) != today) {
                prefs.setDrainNotifiedDay(pkg, today)
                Alerts.notify(
                    context, 1000 + (pkg.hashCode() and 0xfff),
                    "${entry.first.label} is draining your battery",
                    "It used about ${perHour.toInt()} mAh per hour in the background. Open Memdroid to stop it.",
                )
            }
        }
    }

    /** Apps that came to the foreground at any point in the window. */
    private fun openedApps(context: Context, start: Long, end: Long): Set<String> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val opened = HashSet<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) opened += event.packageName
        }
        return opened
    }

    private fun load(context: Context): Pair<Long, Map<String, Double>>? = try {
        val o = JSONObject(file(context).readText())
        val mah = o.getJSONObject("mah")
        o.getLong("time") to mah.keys().asSequence().associateWith { mah.getDouble(it) }
    } catch (_: Exception) {
        null
    }

    private fun save(context: Context, time: Long, mah: Map<String, Double>) {
        val m = JSONObject()
        mah.forEach { (k, v) -> m.put(k, v) }
        file(context).writeText(JSONObject().put("time", time).put("mah", m).toString())
    }
}
