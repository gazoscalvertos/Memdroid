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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memdroid.app.data.AppLookup
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
        item { SectionTitle("Access") }
        item {
            StatusRow(
                "Shizuku",
                when (shizuku) {
                    ShizukuState.READY -> "Connected. Memdroid can list and stop apps."
                    ShizukuState.NO_PERMISSION -> "Running, but Memdroid isn't allowed yet."
                    ShizukuState.NOT_RUNNING -> "Not running. Start it in the Shizuku app with wireless debugging. " +
                        "It needs starting again after each reboot."
                },
                action = if (shizuku == ShizukuState.NO_PERMISSION) "Grant" else null,
                onAction = ShizukuShell::requestPermission,
            )
        }
        item {
            if (usageAccess) {
                StatusRow("Usage access", "Allowed", action = null, onAction = {})
            } else {
                PermissionCard(
                    "Usage access",
                    "Needed for app, app data and cache sizes.",
                    onOpenSettings = { context.startActivity(usageAccessIntent()) },
                )
            }
        }
        item {
            if (allFiles) {
                StatusRow("All files access", "Allowed", action = null, onAction = {})
            } else {
                PermissionCard(
                    "All files access",
                    "Needed for the folder breakdown and finding large files.",
                    onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
                )
            }
        }

        item { SectionTitle("Alerts") }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Low storage and memory alerts")
                    Text(
                        "Off by default. Memdroid checks every 30 minutes and notifies you once when space or memory runs low.",
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
                    Text("Alert when free storage is below", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20, 50).forEach { gb ->
                            FilterChip(selected = storageAlertGb == gb, onClick = { prefs.setStorageAlertGb(gb) },
                                label = { Text("$gb GB") })
                        }
                    }
                    Text("Alert when available RAM is below", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { pct ->
                            FilterChip(selected = ramAlertPercent == pct, onClick = { prefs.setRamAlertPercent(pct) },
                                label = { Text("$pct%") })
                        }
                    }
                }
            }
        }

        item { SectionTitle("Killing apps") }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include system apps in Kill all")
                    Text(
                        "Off by default. Stopping system apps can make the phone misbehave until they restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeSystem, onCheckedChange = prefs::setIncludeSystemInKillAll)
            }
        }

        item { SectionTitle("Whitelist (${whitelist.size})") }
        item {
            Text(
                "Whitelisted apps are never killed. Add apps with the shield icon on the RAM screen.",
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
                    Icon(Icons.Filled.Lock, contentDescription = "Always protected",
                        modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(onClick = { prefs.setWhitelisted(pkg, false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove from whitelist")
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
