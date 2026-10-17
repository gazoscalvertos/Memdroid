package com.memdroid.app.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
        var byUid = parseCheckin(ShizukuShell.exec("dumpsys batterystats --checkin"))
        if (byUid.isEmpty()) byUid = parseText(ShizukuShell.exec("dumpsys batterystats"))
        if (byUid.isEmpty()) {
            return Result.failure(IllegalStateException("No battery stats found"))
        }

        val foreground = foregroundTimes()
        val pm = context.packageManager
        // Several uids (shared system uids, SDK sandboxes) can resolve to the same package, so
        // sum per package; duplicate entries would also break the list's unique keys.
        val byPackage = LinkedHashMap<String, Pair<AppMeta, Double>>()
        byUid.filterValues { it > 0 }.forEach { (uid, mah) ->
            val meta = pm.getPackagesForUid(uid).orEmpty().firstNotNullOfOrNull { lookup.find(it) } ?: return@forEach
            val previous = byPackage[meta.packageName]?.second ?: 0.0
            byPackage[meta.packageName] = meta to (previous + mah)
        }
        val total = byPackage.values.sumOf { it.second }.coerceAtLeast(0.001)
        return Result.success(
            byPackage.values.map { (meta, mah) ->
                AppBattery(meta.packageName, meta.label, meta.isSystem, mah, (mah / total).toFloat(), foreground?.get(meta.packageName))
            }.sortedByDescending { it.mah }
        )
    }

    /** Checkin rows look like `9,<uid>,l,pwi,uid,<mAh>,...`; `l` is "since last charge". */
    private fun parseCheckin(result: ShellResult): Map<Int, Double> {
        if (!result.ok) return emptyMap()
        val out = HashMap<Int, Double>()
        result.out.lineSequence().forEach { line ->
            val f = line.split(',')
            if (f.size > 5 && f[2] == "l" && f[3] == "pwi" && f[4] == "uid") {
                val uid = f[1].toIntOrNull() ?: return@forEach
                val mah = f[5].toDoubleOrNull() ?: return@forEach
                out[uid] = (out[uid] ?: 0.0) + mah
            }
        }
        return out
    }

    /** Fallback for the human-readable "Estimated power use (mAh)" section: `UID u0a123: 45.6 ...`. */
    private fun parseText(result: ShellResult): Map<Int, Double> {
        if (!result.ok) return emptyMap()
        val start = result.out.indexOf("Estimated power use")
        if (start < 0) return emptyMap()
        val row = Regex("""^\s+UID (?:u(\d+)a(\d+)|(\d+)):\s+([\d.]+)""")
        val out = HashMap<Int, Double>()
        result.out.substring(start).lineSequence().drop(1)
            .takeWhile { it.isBlank() || it.startsWith(" ") }
            .forEach { line ->
                val m = row.find(line) ?: return@forEach
                val (user, appId, plain, mah) = m.destructured
                val uid = if (plain.isNotEmpty()) plain.toInt()
                else user.toInt() * 100_000 + 10_000 + appId.toInt()
                out[uid] = (out[uid] ?: 0.0) + mah.toDouble()
            }
        return out
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
