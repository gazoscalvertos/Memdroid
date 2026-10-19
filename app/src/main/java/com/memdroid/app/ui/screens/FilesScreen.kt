package com.memdroid.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.memdroid.app.ui.FileThumbnail
import com.memdroid.app.ui.openFile
import androidx.compose.foundation.layout.Box
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
import com.memdroid.app.data.LargeFile
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.deleteOutcome
import com.memdroid.app.ui.PermissionCard
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
            val found = repo.findLargeFiles(minMb * 1_000_000) { _status.value = getApplication<Application>().resources.getQuantityString(R.plurals.b_scanned_files, it, it) }
            _files.value = found
            _status.value = getApplication<Application>().resources.getQuantityString(
                R.plurals.b_found_large_files, found.size, found.size, minMb, formatBytes(found.sumOf { it.size }),
            )
            _scanning.value = false
        }
    }

    /** Called only from the confirmation dialog. */
    fun delete(targets: List<LargeFile>) {
        viewModelScope.launch {
            val deleted = repo.delete(targets.map { it.file })
            if (deleted > 0) Prefs.get(getApplication()).recordWin()
            _files.value = _files.value?.filter { it.file.exists() }
            _status.value = getApplication<Application>().resources.getQuantityString(R.plurals.b_deleted_x_of_y, targets.size, deleted, targets.size)
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
            PermissionCard(
                stringResource(R.string.b_all_files_access_title),
                stringResource(R.string.b_all_files_body_large),
                onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
            )
            return@Column
        }

        Text(stringResource(R.string.b_find_files_larger_than), modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THRESHOLDS_MB.forEach { mb ->
                FilterChip(
                    selected = thresholdMb == mb,
                    onClick = { thresholdMb = mb },
                    label = { Text(stringResource(R.string.b_size_mb, mb)) },
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { selected = emptySet(); vm.scan(thresholdMb) }, enabled = !scanning) {
                Text(stringResource(R.string.b_scan))
            }
            Button(onClick = { pendingDelete = selectedFiles }, enabled = selectedFiles.isNotEmpty() && !scanning) {
                Text(stringResource(R.string.b_delete_selected_count, selectedFiles.size))
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
                    Box(Modifier.clickable { openFile(context, f.file) }) { FileThumbnail(f.file) }
                    Spacer(Modifier.width(12.dp))
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
                    IconButton(onClick = { openFile(context, f.file) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.b_cd_open))
                    }
                }
                HorizontalDivider()
            }
        }
    }

    pendingDelete?.let { targets ->
        ConfirmDialog(
            title = pluralStringResource(R.plurals.b_delete_files_title, targets.size, targets.size),
            items = targets.map { stringResource(R.string.b_name_size, it.file.name, formatBytes(it.size)) },
            summary = stringResource(R.string.b_size_outcome, formatBytes(targets.sumOf { it.size }), deleteOutcome()),
            confirmLabel = stringResource(R.string.b_delete),
            onConfirm = {
                vm.delete(targets)
                selected = emptySet()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
