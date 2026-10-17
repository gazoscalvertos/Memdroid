package com.memdroid.app.ui.screens

import android.app.Application
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.RamRepository
import com.memdroid.app.data.RunningApp
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.SystemRam
import com.memdroid.app.data.formatBytes
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import com.memdroid.app.ui.UsageCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class SortOrder(val label: String) {
    RAM_HIGH("Most RAM"),
    RAM_LOW("Least RAM"),
    NAME("Name"),
}

class RamViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RamRepository(app)
    val prefs = Prefs.get(app)

    private val _ram = MutableStateFlow(repo.systemRam())
    val ram: StateFlow<SystemRam> = _ram

    private val _apps = MutableStateFlow<List<RunningApp>>(emptyList())
    val apps: StateFlow<List<RunningApp>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            while (true) {
                _ram.value = repo.systemRam()
                delay(2000)
            }
        }
        viewModelScope.launch {
            ShizukuShell.state.collect { if (it == ShizukuState.READY) refreshApps() }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _loading.value = true
            repo.runningApps()
                .onSuccess { _apps.value = it; _message.value = null }
                .onFailure { _message.value = "Couldn't list running apps: ${it.message}" }
            _loading.value = false
        }
    }

    fun kill(packages: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            val whitelist = prefs.whitelist.value
            val targets = packages.filter { it !in whitelist }
            val availableBefore = repo.systemRam().availableBytes
            val stopped = targets.count { repo.forceStop(it) }
            delay(800)
            _ram.value = repo.systemRam()
            val freed = (_ram.value.availableBytes - availableBefore).coerceAtLeast(0)
            _message.value = "Stopped $stopped of ${targets.size} apps, about ${formatBytes(freed)} freed."
            refreshApps()
        }
    }
}

@Composable
fun RamScreen(modifier: Modifier = Modifier, vm: RamViewModel = viewModel()) {
    val ram by vm.ram.collectAsState()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val shizuku by ShizukuShell.state.collectAsState()
    val whitelist by vm.prefs.whitelist.collectAsState()
    val includeSystem by vm.prefs.includeSystemInKillAll.collectAsState()

    var systemTab by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingKill by remember { mutableStateOf<List<RunningApp>?>(null) }
    var sort by remember { mutableStateOf(SortOrder.RAM_HIGH) }

    val visible = apps.filter { it.isSystem == systemTab }.let { list ->
        when (sort) {
            SortOrder.RAM_HIGH -> list.sortedByDescending { it.memoryBytes }
            SortOrder.RAM_LOW -> list.sortedBy { it.memoryBytes }
            SortOrder.NAME -> list.sortedBy { it.label.lowercase() }
        }
    }
    val killable = visible.filter { it.packageName !in whitelist }
    val selectedApps = visible.filter { it.packageName in selected && it.packageName !in whitelist }

    Column(modifier.fillMaxSize()) {
        UsageCard(
            title = "RAM",
            fraction = ram.usedFraction,
            detail = "${formatBytes(ram.usedBytes)} used of ${formatBytes(ram.totalBytes)} · " +
                "${formatBytes(ram.availableBytes)} available",
            warning = if (ram.lowMemory) "The system is low on memory." else null,
        )

        when (shizuku) {
            ShizukuState.NOT_RUNNING -> NoticeCard(
                "Shizuku isn't running",
                "To see running apps and stop them, install Shizuku from the Play Store and start it " +
                    "with wireless debugging. Memdroid can still show overall RAM without it.",
            )
            ShizukuState.NO_PERMISSION -> NoticeCard(
                "Allow Memdroid in Shizuku",
                "Shizuku is running. Grant Memdroid access to list and stop apps.",
                action = "Grant access",
                onAction = ShizukuShell::requestPermission,
            )
            ShizukuState.READY -> {
                TabRow(selectedTabIndex = if (systemTab) 1 else 0) {
                    Tab(selected = !systemTab, onClick = { systemTab = false; selected = emptySet() },
                        text = { Text("User apps (${apps.count { !it.isSystem }})") })
                    Tab(selected = systemTab, onClick = { systemTab = true; selected = emptySet() },
                        text = { Text("System apps (${apps.count { it.isSystem }})") })
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { pendingKill = selectedApps },
                        enabled = selectedApps.isNotEmpty() && !loading,
                    ) { Text("Kill selected (${selectedApps.size})") }
                    val killAllAllowed = !systemTab || includeSystem
                    OutlinedButton(
                        onClick = { pendingKill = killable },
                        enabled = killAllAllowed && killable.isNotEmpty() && !loading,
                    ) { Text("Kill all") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = vm::refreshApps, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val allSelected = killable.isNotEmpty() && selectedApps.size == killable.size
                    TriStateCheckbox(
                        state = when {
                            selectedApps.isEmpty() -> ToggleableState.Off
                            allSelected -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = {
                            selected = if (allSelected) emptySet() else killable.map { it.packageName }.toSet()
                        },
                        enabled = killable.isNotEmpty(),
                    )
                    Text("Select all", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = sort == order,
                            onClick = { sort = order },
                            label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (systemTab && !includeSystem) {
                    Text(
                        "Kill all is off for system apps. You can turn it on in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        val protected = app.packageName in whitelist
                        RunningAppRow(
                            app = app,
                            protected = protected,
                            locked = app.packageName in vm.prefs.locked,
                            checked = app.packageName in selected && !protected,
                            onCheck = {
                                selected = if (it) selected + app.packageName else selected - app.packageName
                            },
                            onToggleWhitelist = { vm.prefs.setWhitelisted(app.packageName, !protected) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingKill?.let { targets ->
        ConfirmDialog(
            title = "Force stop ${targets.size} apps?",
            items = targets.map { "${it.label} (${formatBytes(it.memoryBytes)})" },
            summary = "These apps will be closed. Unsaved work in them may be lost, and some may restart on their own.",
            confirmLabel = "Force stop",
            onConfirm = {
                vm.kill(targets.map { it.packageName })
                selected = emptySet()
                pendingKill = null
            },
            onDismiss = { pendingKill = null },
        )
    }
}

@Composable
private fun RunningAppRow(
    app: RunningApp,
    protected: Boolean,
    locked: Boolean,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    onToggleWhitelist: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !protected) { onCheck(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheck, enabled = !protected)
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(formatBytes(app.memoryBytes))
                    if (app.processCount > 1) append(" · ${app.processCount} processes")
                    if (protected) append(" · Protected")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleWhitelist, enabled = !locked) {
            Icon(
                if (protected) Icons.Filled.Shield else Icons.Outlined.Shield,
                contentDescription = if (protected) "Remove from whitelist" else "Add to whitelist",
                tint = if (protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
