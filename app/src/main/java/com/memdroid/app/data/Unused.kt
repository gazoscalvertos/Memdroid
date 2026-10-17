package com.memdroid.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class UnusedApp(val app: AppStorage, val installedAt: Long)

/** User apps not opened in [days] days, ignoring apps installed more recently than that. Needs usage access. */
suspend fun findUnusedApps(context: Context, apps: List<AppStorage>, days: Int): List<UnusedApp> =
    withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val pm = context.packageManager
        val skip = setOf(context.packageName, ShizukuShell.SHIZUKU_PACKAGE)
        apps.filter { !it.isSystem && it.packageName !in skip && (it.lastUsed ?: 0) < cutoff }
            .mapNotNull { app ->
                val installed = try {
                    pm.getPackageInfo(app.packageName, 0).firstInstallTime
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                if (installed > cutoff) null else UnusedApp(app, installed)
            }
            .sortedByDescending { it.app.totalBytes }
    }
