package com.memdroid.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memdroid.app.R
import com.memdroid.app.data.AppLookup
import com.memdroid.app.data.Community
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.allFilesAccessIntent
import com.memdroid.app.ui.usageAccessIntent

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val lookup = remember { AppLookup(context) }
    val shizuku by ShizukuShell.state.collectAsState()
    val whitelist by prefs.whitelist.collectAsState()
    val includeSystem by prefs.includeSystemInKillAll.collectAsState()
    val alertsEnabled by prefs.alertsEnabled.collectAsState()
    val storageAlertGb by prefs.storageAlertGb.collectAsState()
    val ramAlertPercent by prefs.ramAlertPercent.collectAsState()
    val drainAlerts by prefs.drainAlertsEnabled.collectAsState()
    val drainThreshold by prefs.drainThresholdMah.collectAsState()
    val recycleBin by prefs.recycleBinEnabled.collectAsState()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.setAlertsEnabled(granted)
    }

    var usageAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var allFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) {
        usageAccess = hasUsageAccess(context)
        allFiles = hasAllFilesAccess()
        onPauseOrDispose { }
    }

    LazyColumn(modifier.fillMaxSize()) {
        item { SectionTitle(stringResource(R.string.c_settings_section_access)) }
        item {
            StatusRow(
                stringResource(R.string.c_settings_shizuku),
                when (shizuku) {
                    ShizukuState.READY -> stringResource(R.string.c_settings_shizuku_ready)
                    ShizukuState.NO_PERMISSION -> stringResource(R.string.c_settings_shizuku_no_permission)
                    ShizukuState.NOT_RUNNING -> stringResource(R.string.c_settings_shizuku_not_running)
                },
                action = if (shizuku == ShizukuState.NO_PERMISSION) stringResource(R.string.c_settings_grant) else null,
                onAction = ShizukuShell::requestPermission,
            )
        }
        item {
            if (usageAccess) {
                StatusRow(stringResource(R.string.c_settings_usage_access), stringResource(R.string.c_settings_allowed), action = null, onAction = {})
            } else {
                PermissionCard(
                    stringResource(R.string.c_settings_usage_access),
                    stringResource(R.string.c_settings_usage_access_body),
                    onOpenSettings = { context.startActivity(usageAccessIntent()) },
                )
            }
        }
        item {
            if (allFiles) {
                StatusRow(stringResource(R.string.c_settings_all_files_access), stringResource(R.string.c_settings_allowed), action = null, onAction = {})
            } else {
                PermissionCard(
                    stringResource(R.string.c_settings_all_files_access),
                    stringResource(R.string.c_settings_all_files_access_body),
                    onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
                )
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_alerts)) }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.c_settings_alerts_title))
                    Text(
                        stringResource(R.string.c_settings_alerts_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = alertsEnabled, onCheckedChange = { on ->
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    if (on && !granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else prefs.setAlertsEnabled(on)
                })
            }
        }
        if (alertsEnabled) {
            item {
                Column(Modifier.padding(16.dp, 4.dp)) {
                    Text(stringResource(R.string.c_settings_storage_below), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20, 50).forEach { gb ->
                            FilterChip(selected = storageAlertGb == gb, onClick = { prefs.setStorageAlertGb(gb) },
                                label = { Text(stringResource(R.string.c_settings_gb, gb)) })
                        }
                    }
                    Text(stringResource(R.string.c_settings_ram_below), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { pct ->
                            FilterChip(selected = ramAlertPercent == pct, onClick = { prefs.setRamAlertPercent(pct) },
                                label = { Text(stringResource(R.string.c_percent, pct)) })
                        }
                    }
                }
            }
        }

        if (alertsEnabled) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.c_settings_drain_title))
                        Text(
                            stringResource(R.string.c_settings_drain_body, drainThreshold),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = drainAlerts, onCheckedChange = prefs::setDrainAlertsEnabled)
                }
            }
            if (drainAlerts) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 200).forEach { mah ->
                            FilterChip(selected = drainThreshold == mah, onClick = { prefs.setDrainThresholdMah(mah) },
                                label = { Text(stringResource(R.string.c_settings_mah_per_hour, mah)) })
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_deleting)) }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.c_settings_recycle_bin))
                    Text(
                        stringResource(R.string.c_settings_recycle_bin_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = recycleBin, onCheckedChange = prefs::setRecycleBinEnabled)
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_setup)) }
        item {
            OutlinedButton(onClick = { prefs.setOnboarded(false) }, modifier = Modifier.padding(16.dp, 4.dp)) {
                Text(stringResource(R.string.c_settings_run_setup_again))
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_about)) }
        item {
            OutlinedButton(onClick = { Community.open(context, Community.PRIVACY_URL) }, modifier = Modifier.padding(16.dp, 4.dp)) {
                Text(stringResource(R.string.c_settings_privacy_policy))
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_killing)) }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.c_settings_include_system))
                    Text(
                        stringResource(R.string.c_settings_include_system_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeSystem, onCheckedChange = prefs::setIncludeSystemInKillAll)
            }
        }

        item { SectionTitle(stringResource(R.string.c_settings_section_whitelist, whitelist.size)) }
        item {
            Text(
                stringResource(R.string.c_settings_whitelist_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val entries = whitelist.map { it to (lookup.find(it)?.label ?: it) }.sortedBy { it.second.lowercase() }
        items(entries, key = { it.first }) { (pkg, label) ->
            Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pkg in prefs.locked) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.c_settings_always_protected),
                        modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(onClick = { prefs.setWhitelisted(pkg, false) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.c_settings_remove_from_whitelist))
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
    )
}

@Composable
private fun StatusRow(title: String, detail: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { OutlinedButton(onClick = onAction) { Text(it) } }
    }
}
