package com.memdroid.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class StorageOverview(
    val totalBytes: Long,
    val freeBytes: Long,
    val imagesBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
) {
    val usedBytes get() = totalBytes - freeBytes
    val usedFraction get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

data class AppStorage(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val lastUsed: Long?,
) {
    val totalBytes get() = appBytes + dataBytes
}

data class LargeFile(val file: File, val size: Long, val modified: Long)

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasAllFilesAccess() = Environment.isExternalStorageManager()

class StorageRepository(private val context: Context) {
    private val stats = context.getSystemService(StorageStatsManager::class.java)

    suspend fun overview(): StorageOverview = withContext(Dispatchers.IO) {
        val uuid = StorageManager.UUID_DEFAULT
        StorageOverview(
            totalBytes = stats.getTotalBytes(uuid),
            freeBytes = stats.getFreeBytes(uuid),
            imagesBytes = mediaSize(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            videoBytes = mediaSize(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            audioBytes = mediaSize(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        )
    }

    private fun mediaSize(uri: android.net.Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
            ?.use { c ->
                var sum = 0L
                while (c.moveToNext()) sum += c.getLong(0)
                sum
            } ?: 0L
    } catch (_: SecurityException) {
        0L
    }

    /** Per-app storage, largest first. Needs usage access. */
    suspend fun appStorage(): List<AppStorage> = withContext(Dispatchers.IO) {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val lastUsed = usage.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(365), now)
            .mapValues { it.value.lastTimeUsed }
        val user: UserHandle = Process.myUserHandle()

        AppLookup(context).installed().mapNotNull { app ->
            try {
                val s = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
                AppStorage(
                    app.packageName, app.label, app.isSystem,
                    s.appBytes, s.dataBytes, s.cacheBytes,
                    lastUsed[app.packageName]?.takeIf { it > 0 },
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.totalBytes }
    }

    /** Clears every app's cache through the package manager. Needs Shizuku. */
    suspend fun clearAllCaches(): Boolean = ShizukuShell.exec("pm trim-caches 9999G").ok

    /** Walks shared storage for files at or above [minBytes]. Needs all files access. */
    suspend fun findLargeFiles(minBytes: Long, onProgress: (Int) -> Unit): List<LargeFile> =
        withContext(Dispatchers.IO) {
            val root = Environment.getExternalStorageDirectory()
            val found = ArrayList<LargeFile>()
            var scanned = 0
            root.walkTopDown()
                .onEnter { dir -> dir.canRead() && !dir.isHidden }
                .filter { it.isFile }
                .forEach { f ->
                    scanned++
                    if (scanned % 500 == 0) onProgress(scanned)
                    val size = f.length()
                    if (size >= minBytes) found += LargeFile(f, size, f.lastModified())
                }
            onProgress(scanned)
            found.sortedByDescending { it.size }
        }

    /** Deletes files. Only called after the user confirms in a dialog. Returns how many were deleted. */
    suspend fun delete(files: List<File>): Int = withContext(Dispatchers.IO) {
        val deleted = files.filter { it.delete() }
        if (deleted.isNotEmpty()) {
            android.media.MediaScannerConnection.scanFile(
                context, deleted.map { it.absolutePath }.toTypedArray(), null, null
            )
        }
        deleted.size
    }
}
