package com.memdroid.app.data

/**
 * Pure parsers for shell output, kept free of Android classes so they run as plain unit tests.
 * Android's dumpsys formats change between versions; the tests pin the formats we understand.
 */
object Parsers {
    /** Android gives each user (main profile, work profile, Private Space) its own uid range. */
    const val PER_USER_RANGE = 100_000

    fun userIdOf(uid: Int) = uid / PER_USER_RANGE

    /** Battery checkin rows look like `9,<uid>,l,pwi,uid,<mAh>,...`; `l` is "since last charge". */
    fun batteryCheckin(output: String): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        output.lineSequence().forEach { line ->
            val f = line.split(',')
            if (f.size > 5 && f[2] == "l" && f[3] == "pwi" && f[4] == "uid") {
                val uid = f[1].toIntOrNull() ?: return@forEach
                val mah = f[5].toDoubleOrNull() ?: return@forEach
                out[uid] = (out[uid] ?: 0.0) + mah
            }
        }
        return out
    }

    /** The human-readable "Estimated power use (mAh)" section: `UID u0a123: 45.6 ...`. */
    fun batteryText(output: String): Map<Int, Double> {
        val start = output.indexOf("Estimated power use")
        if (start < 0) return emptyMap()
        val row = Regex("""^\s+UID (?:u(\d+)a(\d+)|(\d+)):\s+([\d.]+)""")
        val out = HashMap<Int, Double>()
        output.substring(start).lineSequence().drop(1)
            .takeWhile { it.isBlank() || it.startsWith(" ") }
            .forEach { line ->
                val m = row.find(line) ?: return@forEach
                val (user, appId, plain, mah) = m.destructured
                val uid = if (plain.isNotEmpty()) plain.toInt()
                else user.toInt() * PER_USER_RANGE + 10_000 + appId.toInt()
                out[uid] = (out[uid] ?: 0.0) + mah.toDouble()
            }
        return out
    }

    data class ProcessUsage(val bytes: Long, val processes: Int)

    /** `ps -A -o RSS,NAME` output grouped by package (process names like `com.app:service` fold into `com.app`). */
    fun psByPackage(output: String): Map<String, ProcessUsage> {
        val out = HashMap<String, ProcessUsage>()
        output.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@forEach
            val rssKb = parts[0].toLongOrNull() ?: return@forEach
            val pkg = parts[1].substringBefore(':')
            if (!isValidPackageName(pkg)) return@forEach
            val prev = out[pkg] ?: ProcessUsage(0, 0)
            out[pkg] = ProcessUsage(prev.bytes + rssKb * 1024, prev.processes + 1)
        }
        return out
    }
}
