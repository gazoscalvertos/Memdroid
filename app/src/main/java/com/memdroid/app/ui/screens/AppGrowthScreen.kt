package com.memdroid.app.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memdroid.app.R
import com.memdroid.app.data.AppGrowth
import com.memdroid.app.data.AppHistoryStore
import com.memdroid.app.data.formatBytes
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.AppIcon
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.usageAccessIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppGrowthScreen() {
    val context = LocalContext.current
    if (!hasUsageAccess(context)) {
        PermissionCard(
            stringResource(R.string.d_usage_access_title),
            stringResource(R.string.d_growth_usage_access_text),
            onOpenSettings = { context.startActivity(usageAccessIntent()) },
        )
        return
    }
    var days by rememberSaveable { mutableIntStateOf(7) }
    val state by produceState<Pair<Int, List<AppGrowth>>?>(null, days) {
        value = withContext(Dispatchers.IO) {
            AppHistoryStore.recordIfDue(context)
            AppHistoryStore.recordCount(context) to AppHistoryStore.growth(context, days)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 60).forEach { d ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text(pluralStringResource(R.plurals.d_days, d, d)) })
            }
        }
        val (records, growth) = state ?: return@Column
        if (growth.isEmpty()) {
            Text(
                stringResource(R.string.d_growth_empty, records),
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        Text(
            stringResource(R.string.d_growth_total, signed(growth.sumOf { it.change })),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(growth.filter { it.change != 0L }, key = { it.packageName }) { g ->
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(g.packageName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBytes(g.before)} → ${formatBytes(g.now)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        signed(g.change),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (g.change > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun signed(bytes: Long) = (if (bytes >= 0) "+" else "-") + formatBytes(kotlin.math.abs(bytes))
