package com.memdroid.app.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class BinItem(
    val originalPath: String,
    val binPath: String,
    val size: Long,
    val deletedAt: Long,
    val isDirectory: Boolean,
) {
    val name get() = File(originalPath).name
    val expiresAt get() = deletedAt + RecycleBin.KEEP_MS
}

/**
 * Deleted files are moved into a hidden folder on the same storage (a rename, so it's instant)
 * and removed for good after 30 days. A `.nomedia` file keeps them out of the gallery.
 */
object RecycleBin {
    val KEEP_MS = TimeUnit.DAYS.toMillis(30)
    val dir: File get() = File(Environment.getExternalStorageDirectory(), ".MemdroidBin")

    private fun index(context: Context) = File(context.filesDir, "recycle_bin.json")

    @Synchronized
    fun items(context: Context): List<BinItem> {
        val f = index(context)
        if (!f.exists()) return emptyList()
        return try {
            val json = JSONArray(f.readText())
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                BinItem(o.getString("original"), o.getString("bin"), o.getLong("size"), o.getLong("deletedAt"), o.optBoolean("dir"))
            }.filter { File(it.binPath).exists() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(context: Context, items: List<BinItem>) {
        val json = JSONArray()
        items.forEach {
            json.put(JSONObject().put("original", it.originalPath).put("bin", it.binPath).put("size", it.size)
                .put("deletedAt", it.deletedAt).put("dir", it.isDirectory))
        }
        index(context).writeText(json.toString())
    }

    /** Moves a file or folder into the bin. Returns false if it couldn't be moved (nothing is deleted then). */
    @Synchronized
    fun moveToBin(context: Context, file: File): Boolean {
        val bin = dir
        if (!bin.exists() && !bin.mkdirs()) return false
        File(bin, ".nomedia").let { if (!it.exists()) it.createNewFile() }
        val size = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
        val now = System.currentTimeMillis()
        val target = File(bin, "${now}_${file.name}")
        if (!file.renameTo(target)) return false
        save(context, items(context) + BinItem(file.absolutePath, target.absolutePath, size, now, target.isDirectory))
        return true
    }

    /** Puts an item back where it came from, or next to it with "(restored)" if that name is taken. */
    @Synchronized
    fun restore(context: Context, item: BinItem): Boolean {
        val original = File(item.originalPath)
        var dest = original
        if (dest.exists()) {
            val base = original.nameWithoutExtension
            val ext = original.extension.let { if (it.isEmpty()) "" else ".$it" }
            dest = File(original.parentFile, "$base (restored)$ext")
        }
        dest.parentFile?.mkdirs()
        if (!File(item.binPath).renameTo(dest)) return false
        save(context, items(context).filter { it.binPath != item.binPath })
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        return true
    }

    @Synchronized
    fun deleteForever(context: Context, targets: List<BinItem>): Int {
        val paths = targets.map { it.binPath }.toSet()
        val deleted = targets.count { File(it.binPath).deleteRecursively() }
        save(context, items(context).filter { it.binPath !in paths })
        return deleted
    }

    /** Removes items older than 30 days. Called by the background job. */
    fun purgeExpired(context: Context) {
        val now = System.currentTimeMillis()
        val expired = items(context).filter { it.expiresAt < now }
        if (expired.isNotEmpty()) deleteForever(context, expired)
    }

    fun totalBytes(context: Context) = items(context).sumOf { it.size }
}
