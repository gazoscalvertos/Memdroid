package com.memdroid.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

data class AppGrowth(val packageName: String, val label: String, val before: Long, val now: Long) {
    val change get() = now - before
}

/** Daily per-app storage sizes, kept for 60 days, so Memdroid can show which apps are growing. */
object AppHistoryStore {
    private val KEEP_MS = TimeUnit.DAYS.toMillis(60)
    private val INTERVAL_MS = TimeUnit.HOURS.toMillis(20)

    private fun file(context: Context) = File(context.filesDir, "app_history.csv")

    /** Records every app's size if usage access is allowed and the last record is old enough. */
    suspend fun recordIfDue(context: Context) {
        val prefs = Prefs.get(context)
        val now = System.currentTimeMillis()
        if (now - prefs.lastAppSnapshot < INTERVAL_MS || !hasUsageAccess(context)) return
        val apps = StorageRepository(context).appStorage()
        append(context, now, apps)
        prefs.lastAppSnapshot = now
    }

    @Synchronized
    private fun append(context: Context, time: Long, apps: List<AppStorage>) {
        val f = file(context)
        val cutoff = time - KEEP_MS
        val kept = if (f.exists()) f.readLines().filter { (it.substringBefore(',').toLongOrNull() ?: 0) >= cutoff } else emptyList()
        val added = apps.map { "$time,${it.packageName},${it.totalBytes}" }
        f.writeText((kept + added).joinToString("\n", postfix = "\n"))
    }

    /** Each app's size at the start of the window compared with its latest size, biggest growth first. */
    @Synchronized
    fun growth(context: Context, days: Int): List<AppGrowth> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val rows = f.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 3) return@mapNotNull null
            val t = p[0].toLongOrNull() ?: return@mapNotNull null
            val size = p[2].toLongOrNull() ?: return@mapNotNull null
            Triple(t, p[1], size)
        }.filter { it.first >= since }
        val times = rows.map { it.first }.distinct().sorted()
        if (times.size < 2) return emptyList()
        val first = rows.filter { it.first == times.first() }.associate { it.second to it.third }
        val last = rows.filter { it.first == times.last() }.associate { it.second to it.third }
        val lookup = AppLookup(context)
        return last.mapNotNull { (pkg, now) ->
            val before = first[pkg] ?: return@mapNotNull null
            val label = lookup.find(pkg)?.label ?: return@mapNotNull null
            AppGrowth(pkg, label, before, now)
        }.sortedByDescending { it.change }
    }

    /** How many daily records exist, so the screen can explain when there isn't enough data yet. */
    @Synchronized
    fun recordCount(context: Context): Int {
        val f = file(context)
        if (!f.exists()) return 0
        return f.readLines().map { it.substringBefore(',') }.distinct().size
    }
}
