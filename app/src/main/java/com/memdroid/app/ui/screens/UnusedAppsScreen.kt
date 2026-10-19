package com.memdroid.app.ui.screens

import android.app.Application
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.R
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.UnusedApp
import com.memdroid.app.data.findUnusedApps
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.uninstallIntent
import com.memdroid.app.ui.usageAccessIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UnusedAppsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    val prefs = Prefs.get(app)

    private val _days = MutableStateFlow(60)
    val days: StateFlow<Int> = _days

    private val _apps = MutableStateFlow<List<UnusedApp>>(emptyList())
    val apps: StateFlow<List<UnusedApp>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun setDays(days: Int) {
        _days.value = days
        refresh()
    }

    fun refresh() {
        if (!hasUsageAccess(getApplication())) return
        viewModelScope.launch {
            _loading.value = true
            _apps.value = findUnusedApps(getApplication(), repo.appStorage(), _days.value)
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UnusedAppsScreen(vm: UnusedAppsViewModel = viewModel()) {
    val context = LocalContext.current
    val days by vm.days.collectAsState()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val ignored by vm.prefs.ignoredUnused.collectAsState()
    var showIgnored by rememberSaveable { mutableStateOf(false) }

    // Refreshes on return from the uninstall dialog too.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    if (!hasUsageAccess(context)) {
        PermissionCard(
            stringResource(R.string.d_usage_access_title),
            stringResource(R.string.d_unused_usage_access_text),
            onOpenSettings = { context.startActivity(usageAccessIntent()) },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90).forEach { d ->
                FilterChip(selected = days == d, onClick = { vm.setDays(d) }, label = { Text(pluralStringResource(R.plurals.d_days, d, d)) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val active = apps.filter { it.app.packageName !in ignored }
        val shown = if (showIgnored) apps.filter { it.app.packageName in ignored } else active
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showIgnored) pluralStringResource(R.plurals.d_unused_ignored_summary, shown.size, shown.size)
                else pluralStringResource(
                    R.plurals.d_unused_summary, active.size,
                    active.size, days, formatBytes(active.sumOf { it.app.totalBytes }),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (ignored.isNotEmpty()) {
                TextButton(onClick = { showIgnored = !showIgnored }) {
                    Text(
                        if (showIgnored) stringResource(R.string.d_unused_back)
                        else stringResource(R.string.d_unused_ignored, ignored.size)
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.app.packageName }) { u ->
                Row(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(u.app.packageName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBytes(u.app.totalBytes)} · " +
                                (u.app.lastUsed?.let { stringResource(R.string.d_unused_last_used, formatDate(it)) }
                                    ?: stringResource(R.string.d_unused_not_used_year)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showIgnored) {
                        TextButton(onClick = { vm.prefs.setIgnoredUnused(u.app.packageName, false) }) { Text(stringResource(R.string.d_unused_restore)) }
                    } else {
                        TextButton(onClick = { vm.prefs.setIgnoredUnused(u.app.packageName, true) }) { Text(stringResource(R.string.d_unused_ignore)) }
                        // Android shows its own confirmation before uninstalling.
                        OutlinedButton(onClick = { context.startActivity(uninstallIntent(u.app.packageName)) }) {
                            Text(stringResource(R.string.d_unused_uninstall))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
