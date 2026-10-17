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
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.AppBattery
import com.memdroid.app.data.BatteryRepository
import com.memdroid.app.data.BatteryStatus
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.RamRepository
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.formatDuration
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import com.memdroid.app.ui.UsageCard
import com.memdroid.app.ui.appDetailsIntent
import com.memdroid.app.ui.isExpandedWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class BatteryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BatteryRepository(app)
    private val ram = RamRepository(app)
    val prefs = Prefs.get(app)

    private val _status = MutableStateFlow(repo.status())
    val status: StateFlow<BatteryStatus?> = _status

    private val _apps = MutableStateFlow<List<AppBattery>>(emptyList())
    val apps: StateFlow<List<AppBattery>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            while (true) {
                _status.value = repo.status()
                delay(10_000)
            }
        }
        viewModelScope.launch {
            ShizukuShell.state.collect { if (it == ShizukuState.READY) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            repo.appUsage()
                .onSuccess { _apps.value = it; _message.value = null }
                .onFailure { _message.value = "Couldn't read battery stats: ${it.message}" }
            _loading.value = false
        }
    }

    fun forceStop(app: AppBattery) {
        viewModelScope.launch {
            val ok = app.packageName !in prefs.whitelist.value && ram.forceStop(app.packageName)
            _message.value = if (ok) "Stopped ${app.label}." else "Couldn't stop ${app.label}."
        }
    }
}

@Composable
fun BatteryScreen(modifier: Modifier = Modifier, vm: BatteryViewModel = viewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val shizuku by ShizukuShell.state.collectAsState()
    val whitelist by vm.prefs.whitelist.collectAsState()
    var systemTab by rememberSaveable { mutableStateOf(false) }
    var pendingStop by remember { mutableStateOf<AppBattery?>(null) }

    val visible = apps.filter { it.isSystem == systemTab }

    val statusPane: @Composable () -> Unit = {
        status?.let { s ->
            UsageCard(
                title = "Battery ${s.level}%",
                fraction = s.level / 100f,
                detail = buildString {
                    append(if (s.charging) "Charging" + (s.source?.let { " via $it" } ?: "") else "On battery")
                    append(" · ${String.format(Locale.getDefault(), "%.1f", s.temperatureC)}°C · Health ${s.health}")
                },
                warning = if (s.temperatureC >= 42f) "The battery is hot." else null,
            )
        }
        when (shizuku) {
            ShizukuState.NOT_RUNNING -> NoticeCard(
                "Shizuku isn't running",
                "Android only shows battery use per app to the system. Start Shizuku to see it here.",
            )
            ShizukuState.NO_PERMISSION -> NoticeCard(
                "Allow Memdroid in Shizuku",
                "Grant access to read battery use by app.",
                action = "Grant access",
                onAction = ShizukuShell::requestPermission,
            )
            ShizukuState.READY -> Unit
        }
    }

    val listPane: @Composable () -> Unit = {
        if (shizuku == ShizukuState.READY) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Since last full charge", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::refresh, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !systemTab, onClick = { systemTab = false },
                        label = { Text("User (${apps.count { !it.isSystem }})") })
                    FilterChip(selected = systemTab, onClick = { systemTab = true },
                        label = { Text("System (${apps.count { it.isSystem }})") })
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        BatteryRow(
                            app,
                            canStop = app.packageName !in whitelist,
                            onClick = { context.startActivity(appDetailsIntent(app.packageName)) },
                            onStop = { pendingStop = app },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (isExpandedWidth()) {
        Row(modifier.fillMaxSize()) {
            Column(Modifier.width(380.dp)) { statusPane() }
            Column(Modifier.weight(1f)) { listPane() }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            statusPane()
            listPane()
        }
    }

    pendingStop?.let { app ->
        ConfirmDialog(
            title = "Force stop ${app.label}?",
            items = listOf(app.label),
            summary = "The app will be closed. Unsaved work in it may be lost, and it may restart on its own.",
            confirmLabel = "Force stop",
            onConfirm = { vm.forceStop(app); pendingStop = null },
            onDismiss = { pendingStop = null },
        )
    }
}

@Composable
private fun BatteryRow(app: AppBattery, canStop: Boolean, onClick: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${String.format(Locale.getDefault(), "%.1f", app.share * 100)}%",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            LinearProgressIndicator(
                progress = { app.share },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                drawStopIndicator = {},
            )
            Text(
                buildString {
                    append("${app.mah.toInt()} mAh")
                    app.foregroundMs?.let { append(" · On screen ${formatDuration(it)} in the last 24 h") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onStop, enabled = canStop) {
            Icon(Icons.Filled.StopCircle, contentDescription = "Force stop")
        }
    }
}
