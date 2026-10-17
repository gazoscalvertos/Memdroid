package com.memdroid.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.AppStorage
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.StorageOverview
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import com.memdroid.app.ui.UsageCard
import com.memdroid.app.ui.appDetailsIntent
import com.memdroid.app.ui.uninstallIntent
import com.memdroid.app.ui.usageAccessIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StorageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)

    private val _overview = MutableStateFlow<StorageOverview?>(null)
    val overview: StateFlow<StorageOverview?> = _overview

    private val _apps = MutableStateFlow<List<AppStorage>>(emptyList())
    val apps: StateFlow<List<AppStorage>> = _apps

    private val _usageAccess = MutableStateFlow(hasUsageAccess(app))
    val usageAccess: StateFlow<Boolean> = _usageAccess

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _usageAccess.value = hasUsageAccess(getApplication())
            _overview.value = repo.overview()
            if (_usageAccess.value) _apps.value = repo.appStorage()
            _loading.value = false
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _loading.value = true
            val before = repo.overview().freeBytes
            val ok = repo.clearAllCaches()
            refresh()
            val after = repo.overview().freeBytes
            _message.value = if (ok) "Caches cleared, ${formatBytes((after - before).coerceAtLeast(0))} freed."
            else "Couldn't clear caches."
        }
    }
}

@Composable
fun StorageScreen(modifier: Modifier = Modifier, vm: StorageViewModel = viewModel()) {
    val context = LocalContext.current
    val overview by vm.overview.collectAsState()
    val apps by vm.apps.collectAsState()
    val usageAccess by vm.usageAccess.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val shizuku by ShizukuShell.state.collectAsState()

    var systemTab by remember { mutableStateOf(false) }
    var confirmClearCaches by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    val visible = apps.filter { it.isSystem == systemTab }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            overview?.let { o ->
                val appsTotal = apps.sumOf { it.totalBytes }
                UsageCard(
                    title = "Storage",
                    fraction = o.usedFraction,
                    detail = buildString {
                        append("${formatBytes(o.usedBytes)} used of ${formatBytes(o.totalBytes)} · ${formatBytes(o.freeBytes)} free\n")
                        if (appsTotal > 0) append("Apps ${formatBytes(appsTotal)} · ")
                        append("Images ${formatBytes(o.imagesBytes)} · Videos ${formatBytes(o.videoBytes)} · Audio ${formatBytes(o.audioBytes)}")
                    },
                )
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
            }
            if (shizuku == ShizukuState.READY) {
                OutlinedButton(
                    onClick = { confirmClearCaches = true },
                    enabled = !loading,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text("Clear all app caches") }
            }
            if (!usageAccess) {
                NoticeCard(
                    "Allow usage access",
                    "Memdroid needs usage access to show how much space each app uses and when you last opened it.",
                    action = "Open settings",
                    onAction = { context.startActivity(usageAccessIntent()) },
                )
            } else {
                TabRow(selectedTabIndex = if (systemTab) 1 else 0) {
                    Tab(selected = !systemTab, onClick = { systemTab = false },
                        text = { Text("User apps (${apps.count { !it.isSystem }})") })
                    Tab(selected = systemTab, onClick = { systemTab = true },
                        text = { Text("System apps (${apps.count { it.isSystem }})") })
                }
            }
        }
        items(visible, key = { it.packageName }) { app ->
            AppStorageRow(
                app,
                onDetails = { context.startActivity(appDetailsIntent(app.packageName)) },
                onUninstall = if (app.isSystem) null else ({ context.startActivity(uninstallIntent(app.packageName)) }),
            )
            HorizontalDivider()
        }
    }

    if (confirmClearCaches) {
        ConfirmDialog(
            title = "Clear all app caches?",
            items = apps.filter { it.cacheBytes > 0 }.sortedByDescending { it.cacheBytes }
                .map { "${it.label}: ${formatBytes(it.cacheBytes)}" },
            summary = "Cached files for every app will be deleted. Apps will rebuild them as needed. " +
                "Total: ${formatBytes(apps.sumOf { it.cacheBytes })}.",
            confirmLabel = "Delete caches",
            onConfirm = { vm.clearAllCaches(); confirmClearCaches = false },
            onDismiss = { confirmClearCaches = false },
        )
    }
}

@Composable
private fun AppStorageRow(app: AppStorage, onDetails: () -> Unit, onUninstall: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { menu = true }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "App ${formatBytes(app.appBytes)} · Data ${formatBytes(app.dataBytes)} · Cache ${formatBytes(app.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    app.lastUsed?.let { "Last used ${formatDate(it)}" } ?: "Not used in the last year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatBytes(app.totalBytes), style = MaterialTheme.typography.titleSmall)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; onDetails() })
            onUninstall?.let {
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; it() })
            }
        }
    }
}
