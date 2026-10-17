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

    private val _alertsEnabled = MutableStateFlow(sp.getBoolean(KEY_ALERTS, false))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled

    /** Alert when free storage drops below this many GB. */
    private val _storageAlertGb = MutableStateFlow(sp.getInt(KEY_STORAGE_GB, 10))
    val storageAlertGb: StateFlow<Int> = _storageAlertGb

    /** Alert when available RAM drops below this percentage. */
    private val _ramAlertPercent = MutableStateFlow(sp.getInt(KEY_RAM_PERCENT, 10))
    val ramAlertPercent: StateFlow<Int> = _ramAlertPercent

    fun setAlertsEnabled(value: Boolean) {
        _alertsEnabled.value = value
        sp.edit().putBoolean(KEY_ALERTS, value).apply()
    }

    fun setStorageAlertGb(value: Int) {
        _storageAlertGb.value = value
        sp.edit().putInt(KEY_STORAGE_GB, value).apply()
    }

    fun setRamAlertPercent(value: Int) {
        _ramAlertPercent.value = value
        sp.edit().putInt(KEY_RAM_PERCENT, value).apply()
    }

    /** Whether an alert is currently showing for [key], so each low state notifies only once. */
    fun alertActive(key: String) = sp.getBoolean("alert_active_$key", false)

    fun setAlertActive(key: String, active: Boolean) {
        sp.edit().putBoolean("alert_active_$key", active).apply()
    }

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
        private const val KEY_ALERTS = "alerts_enabled"
        private const val KEY_STORAGE_GB = "storage_alert_gb"
        private const val KEY_RAM_PERCENT = "ram_alert_percent"

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
