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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.memdroid.app.data.DuplicateFinder
import com.memdroid.app.data.DuplicateGroup
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
import java.io.File

class DuplicatesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)

    private val _groups = MutableStateFlow<List<DuplicateGroup>?>(null)
    val groups: StateFlow<List<DuplicateGroup>?> = _groups

    /** Paths marked for deletion. By default every copy except the oldest. */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun scan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val found = DuplicateFinder(getApplication<Application>()).find { _status.value = it }
            _groups.value = found
            _selected.value = found.flatMap { g -> g.files.drop(1).map { it.absolutePath } }.toSet()
            _status.value = if (found.isEmpty()) getApplication<Application>().getString(R.string.b_no_duplicates)
            else getApplication<Application>().resources.getQuantityString(
                R.plurals.b_duplicates_found, found.size, found.size, formatBytes(found.sumOf { it.wastedBytes }),
            )
            _scanning.value = false
        }
    }

    /** Toggles a copy, but never lets every copy in a group be selected. */
    fun toggle(group: DuplicateGroup, file: File) {
        val path = file.absolutePath
        val current = _selected.value
        if (path in current) {
            _selected.value = current - path
        } else {
            val othersKept = group.files.count { it.absolutePath != path && it.absolutePath !in current }
            if (othersKept > 0) _selected.value = current + path
        }
    }

    /** Called only from the confirmation dialog. */
    fun deleteSelected() {
        viewModelScope.launch {
            val targets = _groups.value.orEmpty().flatMap { it.files }.filter { it.absolutePath in _selected.value }
            val deleted = repo.delete(targets)
            if (deleted > 0) Prefs.get(getApplication()).recordWin()
            _groups.value = _groups.value.orEmpty()
                .map { g -> DuplicateGroup(g.size, g.files.filter { it.exists() }) }
                .filter { it.files.size > 1 }
            _selected.value = emptySet()
            _status.value = getApplication<Application>().resources.getQuantityString(R.plurals.b_deleted_x_of_y, targets.size, deleted, targets.size)
        }
    }
}

@Composable
fun DuplicatesScreen(vm: DuplicatesViewModel = viewModel()) {
    val context = LocalContext.current
    var allFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) {
        allFiles = hasAllFilesAccess()
        onPauseOrDispose { }
    }
    if (!allFiles) {
        PermissionCard(
            stringResource(R.string.b_all_files_access_title),
            stringResource(R.string.b_all_files_body_compare),
            onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
        )
        return
    }

    val groups by vm.groups.collectAsState()
    val selected by vm.selected.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val status by vm.status.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    val allFilesInGroups = groups.orEmpty().flatMap { g -> g.files.map { it to g.size } }
    val toDelete = allFilesInGroups.filter { it.first.absolutePath in selected }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::scan, enabled = !scanning) { Text(stringResource(R.string.b_scan)) }
            Button(onClick = { confirm = true }, enabled = toDelete.isNotEmpty() && !scanning) {
                Text(stringResource(R.string.b_delete_selected_count_size, toDelete.size, formatBytes(toDelete.sumOf { it.second })))
            }
        }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp)) }
        Text(
            stringResource(R.string.b_duplicates_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups.orEmpty(), key = { it.files.first().absolutePath }) { group ->
                Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            pluralStringResource(R.plurals.b_copies_each, group.files.size, group.files.size, formatBytes(group.size)),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        group.files.forEach { f ->
                            val checked = f.absolutePath in selected
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.toggle(group, f) }.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { vm.toggle(group, f) })
                                Box(Modifier.clickable { openFile(context, f) }) { FileThumbnail(f) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${f.parent} · ${formatDate(f.lastModified())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { openFile(context, f) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.b_cd_open))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.b_delete_duplicates_title, toDelete.size, toDelete.size),
            items = toDelete.map { stringResource(R.string.b_dup_item, it.first.name, formatBytes(it.second), it.first.parent.toString()) },
            summary = stringResource(R.string.b_dup_summary, formatBytes(toDelete.sumOf { it.second }), deleteOutcome()),
            confirmLabel = stringResource(R.string.b_delete),
            onConfirm = { vm.deleteSelected(); confirm = false },
            onDismiss = { confirm = false },
        )
    }
}
