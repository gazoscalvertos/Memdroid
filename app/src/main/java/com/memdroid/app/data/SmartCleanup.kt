package com.memdroid.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class CleanupItem(val kind: Kind, val count: Int, val bytes: Long) {
    enum class Kind { CACHES, OLD_DOWNLOADS, OLD_SCREENSHOTS, UNUSED_APPS, RECYCLE_BIN }
}

/**
 * Everything Memdroid can suggest freeing in one go. Nothing here deletes; each item opens a
 * review screen or asks for confirmation.
 */
object SmartCleanup {
    const val OLD_DOWNLOAD_DAYS = 90
    const val OLD_SCREENSHOT_DAYS = 90
    const val UNUSED_DAYS = 60

    suspend fun summarize(context: Context, apps: List<AppStorage>): List<CleanupItem> = withContext(Dispatchers.IO) {
        val items = ArrayList<CleanupItem>()
        val now = System.currentTimeMillis()
        if (apps.isNotEmpty()) {
            val withCache = apps.filter { it.cacheBytes > 0 }
            items += CleanupItem(CleanupItem.Kind.CACHES, withCache.size, withCache.sumOf { it.cacheBytes })
            val ignored = Prefs.get(context).ignoredUnused.value
            val unused = findUnusedApps(context, apps, UNUSED_DAYS).filter { it.app.packageName !in ignored }
            items += CleanupItem(CleanupItem.Kind.UNUSED_APPS, unused.size, unused.sumOf { it.app.totalBytes })
        }
        if (hasAllFilesAccess()) {
            val repo = StorageRepository(context)
            val downloads = repo.findOldFiles(OldFileSource.DOWNLOADS.dirs, now - TimeUnit.DAYS.toMillis(OLD_DOWNLOAD_DAYS.toLong()))
            items += CleanupItem(CleanupItem.Kind.OLD_DOWNLOADS, downloads.size, downloads.sumOf { it.size })
            val shots = repo.findOldFiles(OldFileSource.SCREENSHOTS.dirs, now - TimeUnit.DAYS.toMillis(OLD_SCREENSHOT_DAYS.toLong()))
            items += CleanupItem(CleanupItem.Kind.OLD_SCREENSHOTS, shots.size, shots.sumOf { it.size })
            val bin = RecycleBin.items(context)
            items += CleanupItem(CleanupItem.Kind.RECYCLE_BIN, bin.size, bin.sumOf { it.size })
        }
        items.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
    }
}
