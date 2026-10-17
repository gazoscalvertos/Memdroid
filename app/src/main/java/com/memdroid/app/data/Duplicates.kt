package com.memdroid.app.data

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class DuplicateGroup(val size: Long, val files: List<File>) {
    /** Space freed by keeping one copy. */
    val wastedBytes get() = size * (files.size - 1)
}

/** Finds files with identical contents on shared storage. Needs all files access. */
class DuplicateFinder {
    private val root = Environment.getExternalStorageDirectory()
    private val androidDir = File(root, "Android")

    suspend fun find(minBytes: Long = 100_000, onProgress: (String) -> Unit): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            val bySize = HashMap<Long, MutableList<File>>()
            var scanned = 0
            root.walkTopDown()
                .onEnter { it != androidDir && it.canRead() && !it.isHidden }
                .filter { it.isFile }
                .forEach { f ->
                    scanned++
                    if (scanned % 500 == 0) onProgress("Scanned $scanned files…")
                    val size = f.length()
                    if (size >= minBytes) bySize.getOrPut(size) { mutableListOf() } += f
                }

            val candidates = bySize.filterValues { it.size > 1 }
            var checked = 0
            val groups = ArrayList<DuplicateGroup>()
            candidates.forEach { (size, files) ->
                ensureActive()
                checked++
                if (checked % 20 == 0) onProgress("Comparing files… $checked of ${candidates.size}")
                // A cheap sample of the start and end first, then the full contents.
                files.groupBy { sampleHash(it) }.values.filter { it.size > 1 }.forEach { sampled ->
                    sampled.groupBy { fullHash(it) }.values.filter { it.size > 1 }.forEach {
                        groups += DuplicateGroup(size, it.sortedBy { f -> f.lastModified() })
                    }
                }
            }
            groups.sortedByDescending { it.wastedBytes }
        }

    private fun sampleHash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(64 * 1024)
            val n = raf.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (raf.length() > buf.size * 2) {
                raf.seek(raf.length() - buf.size)
                val m = raf.read(buf)
                if (m > 0) md.update(buf, 0, m)
            }
        }
        md.digest().toHex()
    } catch (_: Exception) {
        "error:${f.absolutePath}"
    }

    private fun fullHash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().buffered().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().toHex()
    } catch (_: Exception) {
        "error:${f.absolutePath}"
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
