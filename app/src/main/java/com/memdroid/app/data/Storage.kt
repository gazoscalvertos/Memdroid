package com.memdroid.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class StorageCategory(val label: String) {
    APPS("Apps"),
    APP_DATA("App data"),
    APP_CACHE("App cache"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    OTHER_FILES("Other files"),
    SYSTEM("System"),
}

data class StorageBreakdown(
    val totalBytes: Long,
    val freeBytes: Long,
    /** Ordered largest first. */
    val categories: List<Pair<StorageCategory, Long>>,
    /** False when a missing permission means some space is lumped into System. */
    val complete: Boolean,
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

data class FolderEntry(val file: File, val size: Long, val isDirectory: Boolean, val fileCount: Int)

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

    /**
     * Splits used space into categories. App sizes need usage access; without it only media
     * totals are known and the rest is reported as System.
     */
    suspend fun breakdown(apps: List<AppStorage>): StorageBreakdown = withContext(Dispatchers.IO) {
        val uuid = StorageManager.UUID_DEFAULT
        val total = stats.getTotalBytes(uuid)
        val free = stats.getFreeBytes(uuid)
        val used = total - free

        val external = try {
            stats.queryExternalStatsForUser(uuid, Process.myUserHandle())
        } catch (_: Exception) {
            null
        }
        val images = external?.imageBytes ?: mediaSize(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videos = external?.videoBytes ?: mediaSize(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val audio = external?.audioBytes ?: mediaSize(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        val categories = mutableListOf(
            StorageCategory.IMAGES to images,
            StorageCategory.VIDEOS to videos,
            StorageCategory.AUDIO to audio,
        )
        if (apps.isNotEmpty()) {
            val cache = apps.sumOf { it.cacheBytes }
            categories += StorageCategory.APPS to apps.sumOf { it.appBytes }
            categories += StorageCategory.APP_DATA to (apps.sumOf { it.dataBytes } - cache).coerceAtLeast(0)
            categories += StorageCategory.APP_CACHE to cache
        }
        if (external != null) {
            // App-specific external folders are already counted in each app's data.
            val other = external.totalBytes - images - videos - audio - external.appBytes
            categories += StorageCategory.OTHER_FILES to other.coerceAtLeast(0)
        }
        val accounted = categories.sumOf { it.second }
        categories += StorageCategory.SYSTEM to (used - accounted).coerceAtLeast(0)

        StorageBreakdown(
            total, free,
            categories.filter { it.second > 0 }.sortedByDescending { it.second },
            complete = apps.isNotEmpty() && external != null,
        )
    }

    private fun mediaSize(uri: Uri): Long = try {
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
        val user = Process.myUserHandle()

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

    suspend fun freeBytes(): Long = withContext(Dispatchers.IO) {
        stats.getFreeBytes(StorageManager.UUID_DEFAULT)
    }

    /** Lists a folder's contents with each entry's total size, largest first. Needs all files access. */
    suspend fun listFolder(dir: File): List<FolderEntry> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().map { f ->
            if (f.isDirectory) {
                var size = 0L
                var count = 0
                f.walkTopDown().filter { it.isFile }.forEach { size += it.length(); count++ }
                FolderEntry(f, size, true, count)
            } else {
                FolderEntry(f, f.length(), false, 1)
            }
        }.sortedByDescending { it.size }
    }

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

    /**
     * Deletes files or folders. Only called after the user confirms in a dialog.
     * Returns how many were deleted.
     */
    suspend fun delete(files: List<File>): Int = withContext(Dispatchers.IO) {
        val deleted = files.filter { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        if (deleted.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, deleted.map { it.absolutePath }.toTypedArray(), null, null)
        }
        deleted.size
    }
}
