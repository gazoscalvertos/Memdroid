package com.memdroid.app.data

import android.os.Process

/**
 * Freezing and background limits for other apps, through Shizuku. Both are fully reversible:
 * a frozen app is disabled (hidden, can't run) until unfrozen, and a background-limited app
 * can still be opened normally but Android won't let it run or restart in the background.
 */
object AppControl {
    private val user get() = Parsers.userIdOf(Process.myUid())

    suspend fun frozen(): Set<String> =
        Parsers.packageList(ShizukuShell.exec("pm list packages -d --user $user").out)

    suspend fun setFrozen(pkg: String, frozen: Boolean): Boolean {
        if (!isValidPackageName(pkg)) return false
        val command = if (frozen) "pm disable-user --user $user $pkg" else "pm enable --user $user $pkg"
        return ShizukuShell.exec(command).ok
    }

    suspend fun backgroundRestricted(packages: Collection<String>): Set<String> {
        val valid = packages.filter(::isValidPackageName)
        if (valid.isEmpty()) return emptySet()
        val loop = "for p in ${valid.joinToString(" ")}; do " +
            "echo \"\$p \$(cmd appops get --user $user \$p RUN_ANY_IN_BACKGROUND 2>/dev/null)\"; done"
        return Parsers.backgroundRestricted(ShizukuShell.exec(loop).out)
    }

    suspend fun setBackgroundRestricted(pkg: String, restricted: Boolean): Boolean {
        if (!isValidPackageName(pkg)) return false
        val mode = if (restricted) "ignore" else "default"
        val result = ShizukuShell.exec(
            "cmd appops set --user $user $pkg RUN_ANY_IN_BACKGROUND $mode && " +
                "cmd appops set --user $user $pkg RUN_IN_BACKGROUND $mode"
        )
        return result.ok
    }
}
