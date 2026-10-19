package com.memdroid.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memdroid.app.R
import com.memdroid.app.data.BinItem
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.RecycleBin
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.formatDate
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.ui.ConfirmDialog
import com.memdroid.app.ui.FileThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun RecycleBinScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val enabled by prefs.recycleBinEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<BinItem>?>(null) }
    val items by produceState(emptyList<BinItem>(), version) {
        value = withContext(Dispatchers.IO) { RecycleBin.items(context).sortedByDescending { it.deletedAt } }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.b_use_recycle_bin))
                        Text(
                            stringResource(R.string.b_recycle_bin_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = prefs::setRecycleBinEnabled)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.b_bin_items_size, items.size, items.size, formatBytes(items.sumOf { it.size })),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { pendingDelete = items }, enabled = items.isNotEmpty()) { Text(stringResource(R.string.b_empty_bin)) }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp)) }
                if (!hasAllFilesAccess()) {
                    Text(stringResource(R.string.b_bin_needs_all_files), modifier = Modifier.padding(16.dp))
                }
            }
        }
        items(items, key = { it.binPath }) { item ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (item.isDirectory) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    FileThumbnail(File(item.binPath))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(item.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
                    Text(
                        pluralStringResource(
                            R.plurals.b_bin_item_detail, daysLeft.toInt(),
                            formatBytes(item.size), formatDate(item.deletedAt), daysLeft, File(item.originalPath).parent.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { RecycleBin.restore(context, item) }
                            message = if (ok) context.getString(R.string.b_restored, item.name) else context.getString(R.string.b_couldnt_restore, item.name)
                            version++
                        }
                    }) { Text(stringResource(R.string.b_restore)) }
                    TextButton(onClick = { pendingDelete = listOf(item) }) { Text(stringResource(R.string.b_delete)) }
                }
            }
            HorizontalDivider()
        }
    }

    pendingDelete?.let { targets ->
        ConfirmDialog(
            title = if (targets.size == 1) stringResource(R.string.b_delete_for_good_title, targets.first().name) else stringResource(R.string.b_empty_bin_title),
            items = targets.map { stringResource(R.string.b_name_size, it.name, formatBytes(it.size)) },
            summary = stringResource(R.string.b_permanent_summary, formatBytes(targets.sumOf { it.size })),
            confirmLabel = stringResource(R.string.b_delete_for_good),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val n = withContext(Dispatchers.IO) { RecycleBin.deleteForever(context, targets) }
                    message = context.resources.getQuantityString(R.plurals.b_permanently_deleted, n, n)
                    version++
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
