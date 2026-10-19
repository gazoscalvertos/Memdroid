package com.memdroid.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.memdroid.app.R
import com.memdroid.app.data.HistoryStore
import com.memdroid.app.data.Snapshot
import com.memdroid.app.data.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val RANGES = listOf(
    1 to R.string.d_history_range_24h,
    7 to R.string.d_history_range_7d,
    30 to R.string.d_history_range_30d,
)

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(7) }
    val history by produceState(emptyList<Snapshot>(), days) {
        value = withContext(Dispatchers.IO) {
            HistoryStore.load(context, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RANGES.forEach { (d, label) ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(label)) })
            }
        }
        if (history.size < 2) {
            Text(
                stringResource(R.string.d_history_empty),
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        val last = history.last()
        ChartCard(
            stringResource(R.string.d_history_ram_title),
            stringResource(R.string.d_history_ram_now, formatBytes(last.ramUsed), formatBytes(last.ramTotal)),
            history.map { it.time to it.ramUsed.toFloat() / it.ramTotal },
            Color(0xFF4285F4),
        )
        ChartCard(
            stringResource(R.string.d_history_storage_title),
            stringResource(
                R.string.d_history_storage_now,
                formatBytes(last.storageUsed), signedBytes(last.storageUsed - history.first().storageUsed),
            ),
            history.map { it.time to it.storageUsed.toFloat() / it.storageTotal },
            Color(0xFF34A853),
        )
        ChartCard(
            stringResource(R.string.d_history_battery_title),
            stringResource(R.string.d_history_battery_now, last.batteryLevel),
            history.filter { it.batteryLevel >= 0 }.map { it.time to it.batteryLevel / 100f },
            Color(0xFFFBBC04),
        )
    }
}

private fun signedBytes(delta: Long) = (if (delta >= 0) "+" else "-") + formatBytes(kotlin.math.abs(delta))

/** A simple line chart of fractions between 0 and 1 over time. */
@Composable
private fun ChartCard(title: String, subtitle: String, points: List<Pair<Long, Float>>, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)) {
                listOf(0f, 0.5f, 1f).forEach { y ->
                    drawLine(grid, Offset(0f, size.height * (1 - y)), Offset(size.width, size.height * (1 - y)), 1f)
                }
                if (points.size < 2) return@Canvas
                val start = points.first().first
                val span = (points.last().first - start).coerceAtLeast(1)
                val path = Path()
                points.forEachIndexed { i, (t, v) ->
                    val x = size.width * (t - start) / span
                    val y = size.height * (1 - v.coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 4f))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.d_history_axis_bottom), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.d_history_axis_top), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
