package com.memdroid.app.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class BatteryStatus(
    val level: Int,
    val charging: Boolean,
    val source: String?,
    val temperatureC: Float,
    val health: String,
)

data class AppBattery(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val mah: Double,
    val share: Float,
    val foregroundMs: Long?,
)

class BatteryRepository(private val context: Context) {
    private val lookup = AppLookup(context)

    fun status(): BatteryStatus? {
        val intent = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return BatteryStatus(
            level = if (scale > 0) level * 100 / scale else level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            source = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> null
            },
            temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                else -> "Unknown"
            },
        )
    }

    /**
     * Battery used by each app since the last full charge, from the system's battery stats.
     * Android only exposes this to the shell, so it needs Shizuku.
     */
    suspend fun appUsage(): Result<List<AppBattery>> = withContext(Dispatchers.IO) {
        try {
            readAppUsage()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readAppUsage(): Result<List<AppBattery>> {
        val byPackage = usageByPackage() ?: return Result.failure(IllegalStateException("No battery stats found"))
        val foreground = foregroundTimes()
        val total = byPackage.values.sumOf { it.second }.coerceAtLeast(0.001)
        return Result.success(
            byPackage.values.map { (meta, mah) ->
                AppBattery(meta.packageName, meta.label, meta.isSystem, mah, (mah / total).toFloat(), foreground?.get(meta.packageName))
            }.sortedByDescending { it.mah }
        )
    }

    /**
     * mAh used per package since the last full charge, for this profile only. Returns null if the
     * stats couldn't be read. Needs Shizuku.
     */
    suspend fun usageByPackage(): Map<String, Pair<AppMeta, Double>>? {
        val checkin = ShizukuShell.exec("dumpsys batterystats --checkin")
        var byUid = if (checkin.ok) Parsers.batteryCheckin(checkin.out) else emptyMap()
        if (byUid.isEmpty()) {
            val text = ShizukuShell.exec("dumpsys batterystats")
            if (text.ok) byUid = Parsers.batteryText(text.out)
        }
        if (byUid.isEmpty()) return null

        val pm = context.packageManager
        val myUser = Parsers.userIdOf(Process.myUid())
        // Several uids (shared system uids, SDK sandboxes) can resolve to the same package, so
        // sum per package. Uids from other profiles (Work, Private Space) can't be looked up
        // from this profile, so they're skipped.
        val byPackage = LinkedHashMap<String, Pair<AppMeta, Double>>()
        byUid.filterValues { it > 0 }.forEach { (uid, mah) ->
            if (Parsers.userIdOf(uid) != myUser) return@forEach
            val packages = try {
                pm.getPackagesForUid(uid).orEmpty()
            } catch (_: SecurityException) {
                return@forEach
            }
            val meta = packages.firstNotNullOfOrNull { lookup.find(it) } ?: return@forEach
            val previous = byPackage[meta.packageName]?.second ?: 0.0
            byPackage[meta.packageName] = meta to (previous + mah)
        }
        return byPackage
    }

    /** Screen time per app over the last day, if usage access is allowed. */
    private fun foregroundTimes(): Map<String, Long>? {
        if (!hasUsageAccess(context)) return null
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return usage.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(1), now)
            .mapValues { it.value.totalTimeInForeground }
    }
}

fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
