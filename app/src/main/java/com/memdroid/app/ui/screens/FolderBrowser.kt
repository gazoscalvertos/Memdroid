package com.memdroid.app.ui.screens

import android.app.Application
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memdroid.app.data.FolderEntry
import com.memdroid.app.data.StorageRepository
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.allFilesAccessIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class FolderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    val root: File = Environment.getExternalStorageDirectory()

    private val _dir = MutableStateFlow(root)
    val dir: StateFlow<File> = _dir

    private val _entries = MutableStateFlow<List<FolderEntry>>(emptyList())
    val entries: StateFlow<List<FolderEntry>> = _entries

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var job: Job? = null

    fun open(dir: File) {
        _dir.value = dir
        _message.value = null
        load()
    }

    fun up() {
        if (_dir.value != root) open(_dir.value.parentFile ?: root)
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            _entries.value = emptyList()
            _entries.value = repo.listFolder(_dir.value)
            _loading.value = false
        }
    }

    /** Android/ holds other apps' private data; deleting from it can break those apps. */
    fun canDelete(entry: FolderEntry): Boolean =
        !entry.file.absolutePath.startsWith(File(root, "Android").absolutePath)

    /** Called only from the confirmation dialog. */
    fun delete(entry: FolderEntry) {
        viewModelScope.launch {
            val ok = repo.delete(listOf(entry.file)) == 1
            _message.value = if (ok) "Deleted ${entry.file.name}, ${formatBytes(entry.size)} freed."
            else "Couldn't delete ${entry.file.name}."
            load()
        }
    }
}

@Composable
fun FolderBrowser(vm: FolderViewModel = viewModel()) {
    val context = LocalContext.current
    var allFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) {
        allFiles = hasAllFilesAccess()
        onPauseOrDispose { }
    }
    if (!allFiles) {
        PermissionCard(
            "Allow all files access",
            "To show which folders use the most space, Memdroid needs permission to see all files. " +
                "Nothing is deleted without asking you first.",
            onOpenSettings = { context.startActivity(allFilesAccessIntent(context.packageName)) },
        )
        return
    }

    val dir by vm.dir.collectAsState()
    val entries by vm.entries.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    var pendingDelete by remember { mutableStateOf<FolderEntry?>(null) }

    LaunchedEffect(Unit) { if (entries.isEmpty()) vm.load() }
    BackHandler(enabled = dir != vm.root) { vm.up() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::up, enabled = dir != vm.root) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Internal storage" + dir.absolutePath.removePrefix(vm.root.absolutePath),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!loading) {
                    Text(
                        "${formatBytes(entries.sumOf { it.size })} in ${entries.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
        }
        val max = entries.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.file.absolutePath }) { entry ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = entry.isDirectory) { vm.open(entry.file) }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.isDirectory) Icons.Filled.Folder else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(entry.size), style = MaterialTheme.typography.titleSmall)
                        }
                        LinearProgressIndicator(
                            progress = { entry.size.toFloat() / max },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            drawStopIndicator = {},
                        )
                        if (entry.isDirectory) {
                            Text(
                                "${entry.fileCount} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { pendingDelete = entry }, enabled = vm.canDelete(entry)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = if (entry.isDirectory) "Delete folder ${entry.file.name}?" else "Delete ${entry.file.name}?",
            items = listOf(entry.file.absolutePath.removePrefix(vm.root.absolutePath + "/")),
            summary = if (entry.isDirectory) {
                "The folder and all ${entry.fileCount} files in it (${formatBytes(entry.size)}) will be permanently deleted. This can't be undone."
            } else {
                "${formatBytes(entry.size)} will be permanently deleted. This can't be undone."
            },
            confirmLabel = "Delete",
            onConfirm = { vm.delete(entry); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
