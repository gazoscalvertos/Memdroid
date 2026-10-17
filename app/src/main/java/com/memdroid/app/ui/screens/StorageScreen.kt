package com.memdroid.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.memdroid.app.data.StorageBreakdown
import com.memdroid.app.data.StorageCategory
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.appDetailsIntent
import com.memdroid.app.ui.isExpandedWidth
import com.memdroid.app.ui.uninstallIntent
import com.memdroid.app.ui.usageAccessIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val CATEGORY_COLORS = mapOf(
    StorageCategory.APPS to Color(0xFF4285F4),
    StorageCategory.APP_DATA to Color(0xFF7BAAF7),
    StorageCategory.APP_CACHE to Color(0xFFFBBC04),
    StorageCategory.IMAGES to Color(0xFF34A853),
    StorageCategory.VIDEOS to Color(0xFFEA4335),
    StorageCategory.AUDIO to Color(0xFFA142F4),
    StorageCategory.OTHER_FILES to Color(0xFF24C1E0),
    StorageCategory.SYSTEM to Color(0xFF9AA0A6),
)

private enum class AppSort(val label: String) { SIZE("Size"), CACHE("Cache"), NAME("Name") }

class StorageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)

    private val _breakdown = MutableStateFlow<StorageBreakdown?>(null)
    val breakdown: StateFlow<StorageBreakdown?> = _breakdown

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
            _apps.value = if (_usageAccess.value) repo.appStorage() else emptyList()
            _breakdown.value = repo.breakdown(_apps.value)
            _loading.value = false
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _loading.value = true
            val before = repo.freeBytes()
            val ok = repo.clearAllCaches()
            val freed = (repo.freeBytes() - before).coerceAtLeast(0)
            _message.value = if (ok) "Caches cleared, ${formatBytes(freed)} freed." else "Couldn't clear caches."
            refresh()
        }
    }
}

@Composable
fun StorageScreen(modifier: Modifier = Modifier, vm: StorageViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Overview", "Apps", "Folders").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> OverviewTab(vm) { tab = 1 }
            1 -> AppsTab(vm)
            else -> FolderBrowser()
        }
    }
}

@Composable
private fun UsageAccessCard() {
    val context = LocalContext.current
    PermissionCard(
        "Allow usage access",
        "Memdroid needs usage access to measure apps, app data and caches. Until then, that space is counted as System.",
        onOpenSettings = { context.startActivity(usageAccessIntent()) },
    )
}

@Composable
private fun OverviewTab(vm: StorageViewModel, onShowApps: () -> Unit) {
    val breakdown by vm.breakdown.collectAsState()
    val apps by vm.apps.collectAsState()
    val usageAccess by vm.usageAccess.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val shizuku by ShizukuShell.state.collectAsState()
    var confirmClearCaches by remember { mutableStateOf(false) }

    val summary: @Composable () -> Unit = {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!usageAccess) UsageAccessCard()
        breakdown?.let { BreakdownCard(it) }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
        }
        if (shizuku == ShizukuState.READY && apps.isNotEmpty()) {
            OutlinedButton(
                onClick = { confirmClearCaches = true },
                enabled = !loading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Text("Clear all app caches (${formatBytes(apps.sumOf { it.cacheBytes })})") }
        }
    }
    val topLists: @Composable () -> Unit = {
        if (apps.isNotEmpty()) {
            TopList("Biggest apps", apps.take(5).map { it to it.totalBytes }, onShowApps)
            TopList(
                "Biggest caches",
                apps.sortedByDescending { it.cacheBytes }.take(5).map { it to it.cacheBytes },
                onShowApps,
            )
        }
    }

    if (isExpandedWidth()) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) { item { Column { summary() } } }
            LazyColumn(Modifier.weight(1f)) { item { Column { topLists() } } }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Column { summary() } }
            item { Column { topLists() } }
        }
    }

    if (confirmClearCaches) {
        ConfirmDialog(
            title = "Clear all app caches?",
            items = apps.filter { it.cacheBytes > 0 }.sortedByDescending { it.cacheBytes }
                .map { "${it.label}: ${formatBytes(it.cacheBytes)}" },
            summary = "Cached files for every app will be deleted. Apps rebuild them as needed. " +
                "Total: ${formatBytes(apps.sumOf { it.cacheBytes })}.",
            confirmLabel = "Delete caches",
            onConfirm = { vm.clearAllCaches(); confirmClearCaches = false },
            onDismiss = { confirmClearCaches = false },
        )
    }
}

@Composable
private fun BreakdownCard(b: StorageBreakdown) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${formatBytes(b.usedBytes)} used", style = MaterialTheme.typography.headlineSmall)
            Text(
                "of ${formatBytes(b.totalBytes)} · ${formatBytes(b.freeBytes)} free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // One bar segment per category, then free space.
            Row(
                Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                b.categories.forEach { (cat, bytes) ->
                    val weight = bytes.toFloat() / b.totalBytes
                    if (weight > 0.002f) {
                        Box(Modifier.weight(weight).fillMaxHeight().background(CATEGORY_COLORS.getValue(cat)))
                    }
                }
                val freeWeight = b.freeBytes.toFloat() / b.totalBytes
                if (freeWeight > 0.002f) Spacer(Modifier.weight(freeWeight))
            }
            b.categories.forEach { (cat, bytes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(CATEGORY_COLORS.getValue(cat)))
                    Spacer(Modifier.width(10.dp))
                    Text(cat.label, Modifier.weight(1f))
                    Text(
                        "${(bytes * 100.0 / b.totalBytes).let { if (it < 1) "<1" else it.toInt().toString() }}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(formatBytes(bytes), style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.width(10.dp))
                Text("Free", Modifier.weight(1f))
                Text(formatBytes(b.freeBytes), style = MaterialTheme.typography.titleSmall)
            }
            if (!b.complete) {
                Text(
                    "System includes space Memdroid can't measure yet. Allow usage access for a full breakdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopList(title: String, rows: List<Pair<AppStorage, Long>>, onMore: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "See all",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onMore).padding(4.dp),
                )
            }
            val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            rows.forEach { (app, bytes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(
                            progress = { bytes.toFloat() / max },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            drawStopIndicator = {},
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatBytes(bytes), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun AppsTab(vm: StorageViewModel) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val usageAccess by vm.usageAccess.collectAsState()
    val loading by vm.loading.collectAsState()
    var systemTab by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(AppSort.SIZE) }

    if (!usageAccess) {
        UsageAccessCard()
        return
    }
    val visible = apps.filter { it.isSystem == systemTab }.let { list ->
        when (sort) {
            AppSort.SIZE -> list.sortedByDescending { it.totalBytes }
            AppSort.CACHE -> list.sortedByDescending { it.cacheBytes }
            AppSort.NAME -> list.sortedBy { it.label.lowercase() }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Column {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = !systemTab, onClick = { systemTab = false },
                    label = { Text("User (${apps.count { !it.isSystem }})") })
                FilterChip(selected = systemTab, onClick = { systemTab = true },
                    label = { Text("System (${apps.count { it.isSystem }})") })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sort by", style = MaterialTheme.typography.bodySmall)
                AppSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                }
            }
        } }
        items(visible, key = { it.packageName }) { app ->
            AppStorageRow(
                app,
                onDetails = { context.startActivity(appDetailsIntent(app.packageName)) },
                onUninstall = if (app.isSystem) null else ({ context.startActivity(uninstallIntent(app.packageName)) }),
            )
            HorizontalDivider()
        }
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
                    "App ${formatBytes(app.appBytes)} · Data ${formatBytes(app.dataBytes - app.cacheBytes)} · Cache ${formatBytes(app.cacheBytes)}",
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
            DropdownMenuItem(text = { Text("App info (clear cache or data)") }, onClick = { menu = false; onDetails() })
            onUninstall?.let {
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; it() })
            }
        }
    }
}
