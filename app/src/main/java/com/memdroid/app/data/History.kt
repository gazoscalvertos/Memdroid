package com.memdroid.app.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import java.io.File
import java.util.concurrent.TimeUnit

data class Snapshot(
    val time: Long,
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val batteryLevel: Int,
) {
    val ramAvailablePercent get() = if (ramTotal == 0L) 0 else ((ramTotal - ramUsed) * 100 / ramTotal).toInt()
    val storageFree get() = storageTotal - storageUsed

    fun toCsv() = "$time,$ramUsed,$ramTotal,$storageUsed,$storageTotal,$batteryLevel"

    companion object {
        fun fromCsv(line: String): Snapshot? {
            val f = line.split(',')
            if (f.size != 6) return null
            return try {
                Snapshot(f[0].toLong(), f[1].toLong(), f[2].toLong(), f[3].toLong(), f[4].toLong(), f[5].toInt())
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}

/** RAM, storage and battery readings over time, kept for 30 days in a small CSV file. */
object HistoryStore {
    private val KEEP_MS = TimeUnit.DAYS.toMillis(30)

    private fun file(context: Context) = File(context.filesDir, "history.csv")

    fun capture(context: Context): Snapshot {
        val ram = RamRepository(context).systemRam()
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
        val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
        val battery = BatteryRepository(context).status()?.level ?: -1
        return Snapshot(System.currentTimeMillis(), ram.usedBytes, ram.totalBytes, total - free, total, battery)
    }

    @Synchronized
    fun append(context: Context, snapshot: Snapshot) {
        val f = file(context)
        f.appendText(snapshot.toCsv() + "\n")
        // Prune once the file holds well over 30 days of half-hourly readings.
        if (f.length() > 200_000) {
            val cutoff = System.currentTimeMillis() - KEEP_MS
            val kept = load(context, 0).filter { it.time >= cutoff }
            f.writeText(kept.joinToString("") { it.toCsv() + "\n" })
        }
    }

    @Synchronized
    fun load(context: Context, sinceMs: Long): List<Snapshot> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull(Snapshot::fromCsv).filter { it.time >= sinceMs }
    }
}
