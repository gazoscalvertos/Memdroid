package com.memdroid.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.memdroid.app.data.LargeFile
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.NoticeCard
import com.memdroid.app.ui.allFilesAccessIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val THRESHOLDS_MB = listOf(50L, 100L, 250L, 500L)

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)

    private val _files = MutableStateFlow<List<LargeFile>?>(null)
    val files: StateFlow<List<LargeFile>?> = _files

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun scan(minMb: Long) {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val found = repo.findLargeFiles(minMb * 1_000_000) { _status.value = "Scanned $it files…" }
            _files.value = found
            _status.value = "Found ${found.size} files of $minMb MB or more " +
                "(${formatBytes(found.sumOf { it.size })} in total)."
            _scanning.value = false
        }
    }

    /** Called only from the confirmation dialog. */
    fun delete(targets: List<LargeFile>) {
        viewModelScope.launch {
            val deleted = repo.delete(targets.map { it.file })
            _files.value = _files.value?.filter { it.file.exists() }
            _status.value = "Deleted $deleted of ${targets.size} files."
        }
    }
}

@Composable
fun FilesScreen(modifier: Modifier = Modifier, vm: FilesViewModel = viewModel()) {
    val context = LocalContext.current
    val files by vm.files.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val status by vm.status.collectAsState()

    var allFilesAccess by remember { mutableStateOf(hasAllFilesAccess()) }
    var thresholdMb by remember { mutableStateOf(100L) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<List<LargeFile>?>(null) }

    LifecycleResumeEffect(Unit) {
        allFilesAccess = hasAllFilesAccess()
        onPauseOrDispose { }
    }

    val selectedFiles = files.orEmpty().filter { it.file.absolutePath in selected }

    Column(modifier.fillMaxSize()) {
        if (!allFilesAccess) {
            NoticeCard(
                "Allow all files access",
                "To find large files anywhere on your phone, Memdroid needs permission to see all files. " +
                    "Nothing is ever deleted without asking you first.",
                action = "Open settings",
                onAction = { context.startActivity(allFilesAccessIntent(context.packageName)) },
            )
            return@Column
        }

        Text("Find files larger than", modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THRESHOLDS_MB.forEach { mb ->
                FilterChip(
                    selected = thresholdMb == mb,
                    onClick = { thresholdMb = mb },
                    label = { Text("$mb MB") },
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { selected = emptySet(); vm.scan(thresholdMb) }, enabled = !scanning) {
                Text("Scan")
            }
            Button(onClick = { pendingDelete = selectedFiles }, enabled = selectedFiles.isNotEmpty() && !scanning) {
                Text("Delete selected (${selectedFiles.size})")
            }
        }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(files.orEmpty(), key = { it.file.absolutePath }) { f ->
                val path = f.file.absolutePath
                val checked = path in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { selected = if (checked) selected - path else selected + path }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        selected = if (it) selected + path else selected - path
                    })
                    Column(Modifier.weight(1f)) {
                        Text(f.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBytes(f.size)} · ${formatDate(f.modified)}\n${f.file.parent}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    pendingDelete?.let { targets ->
        ConfirmDialog(
            title = "Delete ${targets.size} files?",
            items = targets.map { "${it.file.name} (${formatBytes(it.size)})" },
            summary = "${formatBytes(targets.sumOf { it.size })} will be permanently deleted. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(targets)
                selected = emptySet()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
