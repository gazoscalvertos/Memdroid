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

    /** Apps Memdroid stops automatically when an auto-kill rule fires. */
    private val _autoKillApps = MutableStateFlow(sp.getStringSet(KEY_AUTO_KILL_APPS, emptySet())!!.toSet())
    val autoKillApps: StateFlow<Set<String>> = _autoKillApps

    private val _autoKillOnScreenOff = MutableStateFlow(sp.getBoolean(KEY_AUTO_KILL_SCREEN_OFF, false))
    val autoKillOnScreenOff: StateFlow<Boolean> = _autoKillOnScreenOff

    private val _autoKillOnLowRam = MutableStateFlow(sp.getBoolean(KEY_AUTO_KILL_LOW_RAM, false))
    val autoKillOnLowRam: StateFlow<Boolean> = _autoKillOnLowRam

    /** Auto-kill runs when available RAM drops below this percentage. */
    private val _autoKillRamPercent = MutableStateFlow(sp.getInt(KEY_AUTO_KILL_RAM_PERCENT, 15))
    val autoKillRamPercent: StateFlow<Int> = _autoKillRamPercent

    private val _autoKillLog = MutableStateFlow(sp.getString(KEY_AUTO_KILL_LOG, null))
    val autoKillLog: StateFlow<String?> = _autoKillLog

    val autoKillActive get() = _autoKillOnScreenOff.value || _autoKillOnLowRam.value

    fun setAutoKill(pkg: String, enabled: Boolean) {
        val updated = if (enabled) _autoKillApps.value + pkg else _autoKillApps.value - pkg
        _autoKillApps.value = updated
        sp.edit().putStringSet(KEY_AUTO_KILL_APPS, updated).apply()
    }

    fun setAutoKillOnScreenOff(value: Boolean) {
        _autoKillOnScreenOff.value = value
        sp.edit().putBoolean(KEY_AUTO_KILL_SCREEN_OFF, value).apply()
    }

    fun setAutoKillOnLowRam(value: Boolean) {
        _autoKillOnLowRam.value = value
        sp.edit().putBoolean(KEY_AUTO_KILL_LOW_RAM, value).apply()
    }

    fun setAutoKillRamPercent(value: Int) {
        _autoKillRamPercent.value = value
        sp.edit().putInt(KEY_AUTO_KILL_RAM_PERCENT, value).apply()
    }

    fun setAutoKillLog(value: String) {
        _autoKillLog.value = value
        sp.edit().putString(KEY_AUTO_KILL_LOG, value).apply()
    }

    /** Apps the user chose to never see in the unused apps list. */
    private val _ignoredUnused = MutableStateFlow(sp.getStringSet(KEY_IGNORED_UNUSED, emptySet())!!.toSet())
    val ignoredUnused: StateFlow<Set<String>> = _ignoredUnused

    fun setIgnoredUnused(pkg: String, ignored: Boolean) {
        val updated = if (ignored) _ignoredUnused.value + pkg else _ignoredUnused.value - pkg
        _ignoredUnused.value = updated
        sp.edit().putStringSet(KEY_IGNORED_UNUSED, updated).apply()
    }

    /** Deleted files go to the recycle bin for 30 days instead of being removed straight away. */
    private val _recycleBin = MutableStateFlow(sp.getBoolean(KEY_RECYCLE_BIN, true))
    val recycleBinEnabled: StateFlow<Boolean> = _recycleBin

    fun setRecycleBinEnabled(value: Boolean) {
        _recycleBin.value = value
        sp.edit().putBoolean(KEY_RECYCLE_BIN, value).apply()
    }

    private val _drainAlerts = MutableStateFlow(sp.getBoolean(KEY_DRAIN_ALERTS, false))
    val drainAlertsEnabled: StateFlow<Boolean> = _drainAlerts

    /** Alert when an app uses at least this many mAh per hour while not on screen. */
    private val _drainThreshold = MutableStateFlow(sp.getInt(KEY_DRAIN_THRESHOLD, 100))
    val drainThresholdMah: StateFlow<Int> = _drainThreshold

    fun setDrainAlertsEnabled(value: Boolean) {
        _drainAlerts.value = value
        sp.edit().putBoolean(KEY_DRAIN_ALERTS, value).apply()
    }

    fun setDrainThresholdMah(value: Int) {
        _drainThreshold.value = value
        sp.edit().putInt(KEY_DRAIN_THRESHOLD, value).apply()
    }

    /** Day number an app was last reported for draining, so each app alerts at most once a day. */
    fun drainNotifiedDay(pkg: String) = sp.getLong("drain_day_$pkg", -1)

    fun setDrainNotifiedDay(pkg: String, day: Long) {
        sp.edit().putLong("drain_day_$pkg", day).apply()
    }

    private val _onboarded = MutableStateFlow(sp.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded

    fun setOnboarded(value: Boolean) {
        _onboarded.value = value
        sp.edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    var lastAppSnapshot: Long
        get() = sp.getLong(KEY_LAST_APP_SNAPSHOT, 0)
        set(value) = sp.edit().putLong(KEY_LAST_APP_SNAPSHOT, value).apply()

    /** Whether an alert is currently showing for [key], so each low state notifies only once. */
    fun alertActive(key: String) = sp.getBoolean("alert_active_$key", false)

    fun setAlertActive(key: String, active: Boolean) {
        sp.edit().putBoolean("alert_active_$key", active).apply()
    }

    /** How this supporter asked to appear on the heroes wall, shown until the next release lists them. */
    private val _myHero = MutableStateFlow(sp.getString(KEY_MY_HERO, null))
    val myHero: StateFlow<String?> = _myHero

    fun setMyHero(shownAs: String) {
        _myHero.value = shownAs
        sp.edit().putString(KEY_MY_HERO, shownAs).apply()
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
        private const val KEY_AUTO_KILL_APPS = "auto_kill_apps"
        private const val KEY_AUTO_KILL_SCREEN_OFF = "auto_kill_screen_off"
        private const val KEY_AUTO_KILL_LOW_RAM = "auto_kill_low_ram"
        private const val KEY_AUTO_KILL_RAM_PERCENT = "auto_kill_ram_percent"
        private const val KEY_AUTO_KILL_LOG = "auto_kill_log"
        private const val KEY_LAST_APP_SNAPSHOT = "last_app_snapshot"
        private const val KEY_IGNORED_UNUSED = "ignored_unused"
        private const val KEY_RECYCLE_BIN = "recycle_bin"
        private const val KEY_DRAIN_ALERTS = "drain_alerts"
        private const val KEY_DRAIN_THRESHOLD = "drain_threshold_mah"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_MY_HERO = "my_hero"

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
