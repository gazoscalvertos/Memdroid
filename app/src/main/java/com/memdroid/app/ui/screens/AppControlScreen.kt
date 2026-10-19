package com.memdroid.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.AppControl
import com.memdroid.app.data.AppLookup
import com.memdroid.app.data.AppMeta
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppControlMode { BACKGROUND, FREEZE }

class AppControlViewModel(app: Application) : AndroidViewModel(app) {
    private val lookup = AppLookup(app)
    private val prefs = Prefs.get(app)

    private val _apps = MutableStateFlow<List<AppMeta>>(emptyList())
    val apps: StateFlow<List<AppMeta>> = _apps

    private val _frozen = MutableStateFlow<Set<String>>(emptySet())
    val frozen: StateFlow<Set<String>> = _frozen

    private val _restricted = MutableStateFlow<Set<String>>(emptySet())
    val restricted: StateFlow<Set<String>> = _restricted

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            // User apps only, and never Memdroid, Shizuku or the other always-protected apps.
            _apps.value = withContext(Dispatchers.IO) {
                lookup.installed().filter { !it.isSystem && it.packageName !in prefs.locked }
                    .sortedBy { it.label.lowercase() }
            }
            _frozen.value = AppControl.frozen()
            // Some launchers' package lists skip disabled apps, so make sure frozen ones are listed.
            val missing = _frozen.value - _apps.value.map { it.packageName }.toSet()
            if (missing.isNotEmpty()) {
                _apps.value = (_apps.value + withContext(Dispatchers.IO) {
                    missing.mapNotNull { lookup.find(it) }.filter { !it.isSystem && it.packageName !in prefs.locked }
                }).sortedBy { it.label.lowercase() }
            }
            _restricted.value = AppControl.backgroundRestricted(_apps.value.map { it.packageName })
            _loading.value = false
        }
    }

    fun setFrozen(app: AppMeta, frozen: Boolean) {
        viewModelScope.launch {
            if (AppControl.setFrozen(app.packageName, frozen)) {
                _frozen.value = if (frozen) _frozen.value + app.packageName else _frozen.value - app.packageName
                _message.value = if (frozen) "${app.label} is frozen." else "${app.label} is back."
            } else {
                _message.value = "Couldn't change ${app.label}."
            }
        }
    }

    fun setRestricted(app: AppMeta, restricted: Boolean) {
        viewModelScope.launch {
            if (AppControl.setBackgroundRestricted(app.packageName, restricted)) {
                _restricted.value =
                    if (restricted) _restricted.value + app.packageName else _restricted.value - app.packageName
                _message.value = null
            } else {
                _message.value = "Couldn't change ${app.label}."
            }
        }
    }
}

/** Freeze apps, or stop them running and restarting in the background. Needs Shizuku. */
@Composable
fun AppControlScreen(mode: AppControlMode, vm: AppControlViewModel = viewModel()) {
    val shizuku by ShizukuShell.state.collectAsState()
    val apps by vm.apps.collectAsState()
    val frozen by vm.frozen.collectAsState()
    val restricted by vm.restricted.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    var confirmFreeze by remember { mutableStateOf<AppMeta?>(null) }

    LaunchedEffect(shizuku) { if (shizuku == ShizukuState.READY) vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        Text(
            when (mode) {
                AppControlMode.BACKGROUND ->
                    "Apps you limit can still be opened normally, but Android won't let them run or restart in " +
                        "the background. Messaging apps may deliver notifications late."
                AppControlMode.FREEZE ->
                    "A frozen app disappears from your home screen and can't run at all until you unfreeze it here. " +
                        "Nothing is deleted."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp),
        )
        when (shizuku) {
            ShizukuState.NOT_RUNNING -> {
                NoticeCard("Shizuku isn't running", "This tool needs Shizuku. Start it with wireless debugging, then come back.")
                return@Column
            }
            ShizukuState.NO_PERMISSION -> {
                NoticeCard(
                    "Allow Memdroid in Shizuku",
                    "Grant access to change other apps.",
                    action = "Grant access",
                    onAction = ShizukuShell::requestPermission,
                )
                return@Column
            }
            ShizukuState.READY -> Unit
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
        }

        val active = if (mode == AppControlMode.FREEZE) frozen else restricted
        val sorted = apps.sortedByDescending { it.packageName in active }
        LazyColumn(Modifier.fillMaxSize()) {
            items(sorted, key = { it.packageName }) { app ->
                val on = app.packageName in active
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    mode == AppControlMode.FREEZE && on -> "Frozen"
                                    mode == AppControlMode.BACKGROUND && on -> "Limited in the background"
                                    else -> app.packageName
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        when (mode) {
                            AppControlMode.BACKGROUND ->
                                Switch(checked = on, onCheckedChange = { vm.setRestricted(app, it) })
                            AppControlMode.FREEZE ->
                                if (on) {
                                    Button(onClick = { vm.setFrozen(app, false) }) { Text("Unfreeze") }
                                } else {
                                    OutlinedButton(onClick = { confirmFreeze = app }) { Text("Freeze") }
                                }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    confirmFreeze?.let { app ->
        ConfirmDialog(
            title = "Freeze ${app.label}?",
            items = listOf(app.label),
            summary = "It will disappear from your home screen and stop running until you unfreeze it in Tools › Freeze apps. " +
                "Its data is kept.",
            confirmLabel = "Freeze",
            onConfirm = { vm.setFrozen(app, true); confirmFreeze = null },
            onDismiss = { confirmFreeze = null },
        )
    }
}
