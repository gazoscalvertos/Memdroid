package com.memdroid.app.data

import android.app.ActivityManager
import android.content.Context

data class SystemRam(val totalBytes: Long, val availableBytes: Long, val lowMemory: Boolean) {
    val usedBytes get() = totalBytes - availableBytes
    val usedFraction get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

data class RunningApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val memoryBytes: Long,
    val processCount: Int,
)

class RamRepository(private val context: Context) {
    private val lookup = AppLookup(context)

    fun systemRam(): SystemRam {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return SystemRam(info.totalMem, info.availMem, info.lowMemory)
    }

    /** Lists running apps by resident memory, grouping each app's processes together. Needs Shizuku. */
    suspend fun runningApps(): Result<List<RunningApp>> {
        val result = ShizukuShell.exec("ps -A -o RSS,NAME")
        if (!result.ok) return Result.failure(IllegalStateException(result.err.ifBlank { "ps failed" }))

        val apps = Parsers.psByPackage(result.out).mapNotNull { (pkg, usage) ->
            val meta = lookup.find(pkg) ?: return@mapNotNull null
            RunningApp(pkg, meta.label, meta.isSystem, usage.bytes, usage.processes)
        }.sortedByDescending { it.memoryBytes }
        return Result.success(apps)
    }

    suspend fun forceStop(pkg: String): Boolean {
        if (!isValidPackageName(pkg)) return false
        return ShizukuShell.exec("am force-stop $pkg").ok
    }
}
