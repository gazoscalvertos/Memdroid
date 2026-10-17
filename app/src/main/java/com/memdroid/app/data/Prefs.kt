package com.memdroid.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("memdroid", Context.MODE_PRIVATE)
    private val ownPackage = context.packageName

    /** Apps that can never be killed, because killing them breaks the phone or Memdroid itself. */
    val locked: Set<String> = setOf(
        ownPackage,
        ShizukuShell.SHIZUKU_PACKAGE,
        "android",
        "com.android.systemui",
        "com.android.phone",
    )

    private val _whitelist = MutableStateFlow(
        locked + (sp.getStringSet(KEY_WHITELIST, null) ?: DEFAULT_WHITELIST)
    )
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _includeSystemInKillAll = MutableStateFlow(sp.getBoolean(KEY_INCLUDE_SYSTEM, false))
    val includeSystemInKillAll: StateFlow<Boolean> = _includeSystemInKillAll

    fun setWhitelisted(pkg: String, whitelisted: Boolean) {
        if (pkg in locked) return
        val updated = if (whitelisted) _whitelist.value + pkg else _whitelist.value - pkg
        _whitelist.value = updated
        sp.edit().putStringSet(KEY_WHITELIST, updated - locked).apply()
    }

    fun setIncludeSystemInKillAll(value: Boolean) {
        _includeSystemInKillAll.value = value
        sp.edit().putBoolean(KEY_INCLUDE_SYSTEM, value).apply()
    }

    companion object {
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_INCLUDE_SYSTEM = "include_system_in_kill_all"

        private val DEFAULT_WHITELIST = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod.latin",
            "com.google.android.gms",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.bluetooth",
        )

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
