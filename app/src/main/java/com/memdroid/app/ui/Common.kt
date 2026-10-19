package com.memdroid.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.memdroid.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(Modifier.size(size)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

@Composable
fun UsageCard(title: String, fraction: Float, detail: String, warning: String? = null) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action?.let { Button(onClick = onAction) { Text(it) } }
        }
    }
}

/**
 * Asks for a special permission, with steps for Android's "restricted setting" block that
 * sideloaded apps hit on Android 13 and later.
 */
@Composable
fun PermissionCard(title: String, body: String, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.b_open_settings)) }
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(if (showHelp) stringResource(R.string.b_hide_help) else stringResource(R.string.b_toggle_greyed_out))
                }
            }
            if (showHelp) {
                Text(
                    stringResource(R.string.b_restricted_settings_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { context.startActivity(appDetailsIntent(context.packageName)) }) {
                    Text(stringResource(R.string.b_open_app_info))
                }
            }
        }
    }
}

/** Confirmation dialog listing every item the action will touch. */
@Composable
fun ConfirmDialog(
    title: String,
    items: List<String>,
    summary: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                Column(
                    Modifier.padding(top = 8.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                ) {
                    items.forEach { Text(stringResource(R.string.b_bullet_item, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.b_cancel)) } },
    )
}

fun appDetailsIntent(pkg: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun uninstallIntent(pkg: String) =
    Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun usageAccessIntent() =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun allFilesAccessIntent(pkg: String) =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * How a delete ends, for confirmation dialogs: kept in the recycle bin for 30 days when it's on,
 * otherwise gone for good.
 */
@Composable
fun deleteOutcome(): String {
    val context = LocalContext.current
    val bin by com.memdroid.app.data.Prefs.get(context).recycleBinEnabled.collectAsState()
    return if (bin) stringResource(R.string.b_delete_outcome_bin)
    else stringResource(R.string.b_delete_outcome_permanent)
}
