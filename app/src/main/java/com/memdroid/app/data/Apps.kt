package com.memdroid.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class AppMeta(val packageName: String, val label: String, val isSystem: Boolean, val uid: Int)

private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

fun isValidPackageName(pkg: String) = PACKAGE_NAME.matches(pkg)

fun ApplicationInfo.isSystemApp() =
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

class AppLookup(context: Context) {
    private val pm = context.packageManager
    private val cache = HashMap<String, AppMeta?>()

    fun find(pkg: String): AppMeta? = cache.getOrPut(pkg) {
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            AppMeta(pkg, info.loadLabel(pm).toString(), info.isSystemApp(), info.uid)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun installed(): List<AppMeta> =
        pm.getInstalledApplications(0).map {
            AppMeta(it.packageName, it.loadLabel(pm).toString(), it.isSystemApp(), it.uid)
        }
}
