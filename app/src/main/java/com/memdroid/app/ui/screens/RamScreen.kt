package com.memdroid.app.ui.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.R
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

private enum class SortOrder(@StringRes val label: Int) {
    RAM_HIGH(R.string.d_ram_sort_most),
    RAM_LOW(R.string.d_ram_sort_least),
    NAME(R.string.d_ram_sort_name),
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
                .onFailure { _message.value = getApplication<Application>().getString(R.string.d_ram_list_error, it.message) }
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
            if (stopped > 0) prefs.recordWin()
            delay(800)
            _ram.value = repo.systemRam()
            val freed = (_ram.value.availableBytes - availableBefore).coerceAtLeast(0)
            _message.value = getApplication<Application>().resources.getQuantityString(
                R.plurals.d_ram_stopped, targets.size, stopped, targets.size, formatBytes(freed),
            )
            refreshApps()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RamScreen(
    modifier: Modifier = Modifier,
    killAllRequested: Boolean = false,
    onKillAllHandled: () -> Unit = {},
    vm: RamViewModel = viewModel(),
) {
    val ram by vm.ram.collectAsState()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val shizuku by ShizukuShell.state.collectAsState()
    val whitelist by vm.prefs.whitelist.collectAsState()
    val includeSystem by vm.prefs.includeSystemInKillAll.collectAsState()

    var systemTab by rememberSaveable { mutableStateOf(false) }
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

    // The widget's Kill all opens here: confirm stopping every non-whitelisted user app.
    LaunchedEffect(killAllRequested, apps, shizuku) {
        if (!killAllRequested) return@LaunchedEffect
        if (shizuku != ShizukuState.READY) {
            onKillAllHandled()
        } else if (apps.isNotEmpty()) {
            systemTab = false
            pendingKill = apps.filter { !it.isSystem && it.packageName !in whitelist }
            onKillAllHandled()
        }
    }
    val selectedApps = visible.filter { it.packageName in selected && it.packageName !in whitelist }

    val ramCard: @Composable () -> Unit = {
        UsageCard(
            title = stringResource(R.string.d_ram_title),
            fraction = ram.usedFraction,
            detail = stringResource(
                R.string.d_ram_detail,
                formatBytes(ram.usedBytes), formatBytes(ram.totalBytes), formatBytes(ram.availableBytes),
            ),
            warning = if (ram.lowMemory) stringResource(R.string.d_ram_low_memory) else null,
        )
    }

    val appsPane: @Composable ColumnScope.() -> Unit = {
        when (shizuku) {
            ShizukuState.NOT_RUNNING -> NoticeCard(
                stringResource(R.string.d_shizuku_not_running_title),
                stringResource(R.string.d_ram_shizuku_not_running),
            )
            ShizukuState.NO_PERMISSION -> NoticeCard(
                stringResource(R.string.d_shizuku_allow_title),
                stringResource(R.string.d_ram_shizuku_no_permission),
                action = stringResource(R.string.d_grant_access),
                onAction = ShizukuShell::requestPermission,
            )
            ShizukuState.READY -> {
                TabRow(selectedTabIndex = if (systemTab) 1 else 0) {
                    Tab(selected = !systemTab, onClick = { systemTab = false; selected = emptySet() },
                        text = { Text(stringResource(R.string.d_ram_tab_user, apps.count { !it.isSystem })) })
                    Tab(selected = systemTab, onClick = { systemTab = true; selected = emptySet() },
                        text = { Text(stringResource(R.string.d_ram_tab_system, apps.count { it.isSystem })) })
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { pendingKill = selectedApps },
                        enabled = selectedApps.isNotEmpty() && !loading,
                    ) { Text(stringResource(R.string.d_ram_kill_selected, selectedApps.size)) }
                    val killAllAllowed = !systemTab || includeSystem
                    OutlinedButton(
                        onClick = { pendingKill = killable },
                        enabled = killAllAllowed && killable.isNotEmpty() && !loading,
                    ) { Text(stringResource(R.string.d_ram_kill_all)) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = vm::refreshApps, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.d_refresh))
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
                    Text(stringResource(R.string.d_ram_select_all), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = sort == order,
                            onClick = { sort = order },
                            label = { Text(stringResource(order.label), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (systemTab && !includeSystem) {
                    Text(
                        stringResource(R.string.d_ram_kill_all_system_off),
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

    // The summary sits above the list at every width; a side column left empty space below it on tablets.
    Column(modifier.fillMaxSize()) {
        ramCard()
        appsPane()
    }

    pendingKill?.let { targets ->
        ConfirmDialog(
            title = pluralStringResource(R.plurals.d_ram_confirm_title, targets.size, targets.size),
            items = targets.map { "${it.label} (${formatBytes(it.memoryBytes)})" },
            summary = stringResource(R.string.d_ram_confirm_summary),
            confirmLabel = stringResource(R.string.d_force_stop),
            onConfirm = {
                vm.kill(targets.map { it.packageName })
                selected = emptySet()
                pendingKill = null
            },
            onDismiss = { pendingKill = null },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
            val processes = pluralStringResource(R.plurals.d_ram_processes, app.processCount, app.processCount)
            val protectedLabel = stringResource(R.string.d_ram_protected)
            Text(
                buildString {
                    append(formatBytes(app.memoryBytes))
                    if (app.processCount > 1) append(" · $processes")
                    if (protected) append(" · $protectedLabel")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleWhitelist, enabled = !locked) {
            Icon(
                if (protected) Icons.Filled.Shield else Icons.Outlined.Shield,
                contentDescription = stringResource(if (protected) R.string.d_ram_whitelist_remove else R.string.d_ram_whitelist_add),
                tint = if (protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
