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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.LargeFile
import com.memdroid.app.data.OldFileSource
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.FileThumbnail
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.allFilesAccessIntent
import com.memdroid.app.ui.deleteOutcome
import com.memdroid.app.ui.openFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OldFilesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)

    private val _sources = MutableStateFlow(setOf(OldFileSource.DOWNLOADS, OldFileSource.SCREENSHOTS))
    val sources: StateFlow<Set<OldFileSource>> = _sources

    private val _days = MutableStateFlow(90)
    val days: StateFlow<Int> = _days

    private val _files = MutableStateFlow<List<LargeFile>>(emptyList())
    val files: StateFlow<List<LargeFile>> = _files

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun toggleSource(source: OldFileSource) {
        val s = _sources.value
        _sources.value = if (source in s && s.size > 1) s - source else s + source
        scan()
    }

    fun setDays(days: Int) {
        _days.value = days
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _loading.value = true
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(_days.value.toLong())
            _files.value = repo.findOldFiles(_sources.value.flatMap { it.dirs }, cutoff)
            _loading.value = false
        }
    }

    /** Called only from the confirmation dialog. */
    fun delete(targets: List<LargeFile>) {
        viewModelScope.launch {
            val n = repo.delete(targets.map { it.file })
            if (n > 0) Prefs.get(getApplication()).recordWin()
            _message.value = "Removed $n of ${targets.size} files."
            scan()
        }
    }
}

@Composable
fun OldFilesScreen(vm: OldFilesViewModel = viewModel()) {
    val context = LocalContext.current
    if (!hasAllFilesAccess()) {
        PermissionCard(
            "Allow all files access",
            "To find old downloads and screenshots, Memdroid needs permission to see all files.",
            onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
        )
        return
    }
    val sources by vm.sources.collectAsState()
    val days by vm.days.collectAsState()
    val files by vm.files.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pending by remember { mutableStateOf<List<LargeFile>?>(null) }

    LaunchedEffect(Unit) { vm.scan() }
    val chosen = files.filter { it.file.absolutePath in selected }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Row(Modifier.padding(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OldFileSource.entries.forEach { s ->
                        FilterChip(selected = s in sources, onClick = { vm.toggleSource(s) }, label = { Text(s.label) })
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Older than", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                    listOf(30, 90, 180, 365).forEach { d ->
                        FilterChip(selected = days == d, onClick = { vm.setDays(d) },
                            label = { Text(if (d == 365) "1 year" else "$d days") })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${files.size} files · ${formatBytes(files.sumOf { it.size })}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        selected = if (chosen.size == files.size) emptySet() else files.map { it.file.absolutePath }.toSet()
                    }, enabled = files.isNotEmpty()) { Text(if (chosen.size == files.size && files.isNotEmpty()) "Clear" else "Select all") }
                }
                Button(
                    onClick = { pending = chosen },
                    enabled = chosen.isNotEmpty() && !loading,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Delete selected (${chosen.size}, ${formatBytes(chosen.sumOf { it.size })})") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp)) }
            }
        }
        items(files, key = { it.file.absolutePath }) { f ->
            val path = f.file.absolutePath
            val checked = path in selected
            Row(
                Modifier.fillMaxWidth().clickable { selected = if (checked) selected - path else selected + path }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + path else selected - path })
                Box(Modifier.clickable { openFile(context, f.file) }) { FileThumbnail(f.file) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatBytes(f.size)} · ${formatDate(f.modified)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }

    pending?.let { targets ->
        ConfirmDialog(
            title = "Delete ${targets.size} old files?",
            items = targets.map { "${it.file.name} (${formatBytes(it.size)})" },
            summary = "${formatBytes(targets.sumOf { it.size })} ${deleteOutcome()}",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(targets); selected = emptySet(); pending = null },
            onDismiss = { pending = null },
        )
    }
}
