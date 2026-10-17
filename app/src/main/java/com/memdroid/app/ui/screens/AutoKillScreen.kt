package com.memdroid.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memdroid.app.data.AppLookup
import com.memdroid.app.data.AppMeta
import com.memdroid.app.data.AutoKillService
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoKillScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuShell.state.collectAsState()
    val chosen by prefs.autoKillApps.collectAsState()
    val whitelist by prefs.whitelist.collectAsState()
    val onScreenOff by prefs.autoKillOnScreenOff.collectAsState()
    val onLowRam by prefs.autoKillOnLowRam.collectAsState()
    val ramPercent by prefs.autoKillRamPercent.collectAsState()
    val log by prefs.autoKillLog.collectAsState()
    var filter by remember { mutableStateOf("") }
    var confirmRunNow by remember { mutableStateOf(false) }

    val apps by produceState(emptyList<AppMeta>()) {
        value = withContext(Dispatchers.IO) {
            AppLookup(context).installed().filter { !it.isSystem && it.packageName !in prefs.locked }
        }
    }
    val shown = apps
        .filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
        .sortedWith(compareBy({ it.packageName !in chosen }, { it.label.lowercase() }))

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                if (shizuku != ShizukuState.READY) {
                    NoticeCard("Shizuku needed", "Auto-kill can only stop apps while Shizuku is running and Memdroid has access.")
                }
                RuleRow("When the screen turns off", "Stops your chosen apps 30 seconds after you lock the phone.", onScreenOff) {
                    prefs.setAutoKillOnScreenOff(it)
                    AutoKillService.sync(context)
                }
                RuleRow("When RAM runs low", "Checks every minute and stops your chosen apps when available RAM is below $ramPercent%.", onLowRam) {
                    prefs.setAutoKillOnLowRam(it)
                    AutoKillService.sync(context)
                }
                if (onLowRam) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 20, 25).forEach { pct ->
                            FilterChip(selected = ramPercent == pct, onClick = { prefs.setAutoKillRamPercent(pct) },
                                label = { Text("$pct%") })
                        }
                    }
                }
                Text(
                    "While a rule is on, Memdroid keeps a small notification so Android lets it keep watching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
                log?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Apps to stop (${chosen.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { confirmRunNow = true },
                        enabled = shizuku == ShizukuState.READY && (chosen - whitelist).isNotEmpty(),
                    ) { Text("Run now") }
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
        items(shown, key = { it.packageName }) { app ->
            val protected = app.packageName in whitelist
            val checked = app.packageName in chosen && !protected
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !protected) { prefs.setAutoKill(app.packageName, !checked) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { prefs.setAutoKill(app.packageName, it) }, enabled = !protected)
                AppIcon(app.packageName, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (protected) {
                        Text("Whitelisted, never stopped", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
    }

    if (confirmRunNow) {
        val targets = apps.filter { it.packageName in chosen && it.packageName !in whitelist }
        ConfirmDialog(
            title = "Stop ${targets.size} apps now?",
            items = targets.map { it.label },
            summary = "These apps will be force stopped. Unsaved work in them may be lost.",
            confirmLabel = "Force stop",
            onConfirm = {
                confirmRunNow = false
                scope.launch { AutoKillService.runRules(context, "run now") }
            },
            onDismiss = { confirmRunNow = false },
        )
    }
}

@Composable
private fun RuleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
