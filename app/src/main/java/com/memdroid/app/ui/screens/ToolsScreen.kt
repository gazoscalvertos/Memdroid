package com.memdroid.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.memdroid.app.R

private enum class Tool(@StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector) {
    AUTO_KILL(R.string.a_tool_auto_kill_title, R.string.a_tool_auto_kill_desc, Icons.Filled.AutoMode),
    HISTORY(R.string.a_tool_history_title, R.string.a_tool_history_desc, Icons.Filled.Timeline),
    APP_GROWTH(R.string.a_tool_app_growth_title, R.string.a_tool_app_growth_desc, Icons.AutoMirrored.Filled.TrendingUp),
    LARGE_FILES(R.string.a_tool_large_files_title, R.string.a_tool_large_files_desc, Icons.Filled.FilePresent),
    OLD_FILES(R.string.a_tool_old_files_title, R.string.a_tool_old_files_desc, Icons.Filled.History),
    DUPLICATES(R.string.a_tool_duplicates_title, R.string.a_tool_duplicates_desc, Icons.Filled.ContentCopy),
    UNUSED_APPS(R.string.a_tool_unused_apps_title, R.string.a_tool_unused_apps_desc, Icons.Filled.HourglassEmpty),
    BACKGROUND(R.string.a_tool_background_title, R.string.a_tool_background_desc, Icons.Filled.Block),
    FREEZE(R.string.a_tool_freeze_title, R.string.a_tool_freeze_desc, Icons.Filled.AcUnit),
    RECYCLE_BIN(R.string.a_tool_recycle_bin_title, R.string.a_tool_recycle_bin_desc, Icons.Filled.DeleteSweep),
    IDEAS(R.string.a_tool_ideas_title, R.string.a_tool_ideas_desc, Icons.Filled.Lightbulb),
    FEEDBACK(R.string.a_tool_feedback_title, R.string.a_tool_feedback_desc, Icons.Filled.BugReport),
    CHANGELOG(R.string.a_tool_changelog_title, R.string.a_tool_changelog_desc, Icons.Filled.NewReleases),
    HEROES(R.string.a_tool_heroes_title, R.string.a_tool_heroes_desc, Icons.Filled.Favorite),
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier, request: String? = null, onRequestHandled: () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf<Tool?>(null) }
    // Other screens (like smart cleanup) can open a tool by name.
    LaunchedEffect(request) {
        if (request != null) {
            open = Tool.entries.firstOrNull { it.name == request }
            onRequestHandled()
        }
    }
    BackHandler(enabled = open != null) { open = null }

    val tool = open
    if (tool == null) {
        LazyColumn(modifier.fillMaxSize()) {
            items(Tool.entries) { t -> Column {
                if (t == Tool.IDEAS) {
                    Text(
                        stringResource(R.string.a_tools_community_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                }
                Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp).clickable { open = t }) {
                    ListItem(
                        headlineContent = { Text(stringResource(t.title)) },
                        supportingContent = { Text(stringResource(t.description)) },
                        leadingContent = { Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            } }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { open = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a_cd_back_to_tools))
            }
            Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium)
        }
        when (tool) {
            Tool.AUTO_KILL -> AutoKillScreen()
            Tool.HISTORY -> HistoryScreen()
            Tool.APP_GROWTH -> AppGrowthScreen()
            Tool.LARGE_FILES -> FilesScreen()
            Tool.OLD_FILES -> OldFilesScreen()
            Tool.RECYCLE_BIN -> RecycleBinScreen()
            Tool.DUPLICATES -> DuplicatesScreen()
            Tool.UNUSED_APPS -> UnusedAppsScreen()
            Tool.BACKGROUND -> AppControlScreen(AppControlMode.BACKGROUND)
            Tool.FREEZE -> AppControlScreen(AppControlMode.FREEZE)
            Tool.IDEAS -> FeatureIdeasScreen(onSuggest = { open = Tool.FEEDBACK })
            Tool.FEEDBACK -> FeedbackScreen()
            Tool.CHANGELOG -> ChangelogScreen()
            Tool.HEROES -> HeroesScreen()
        }
    }
}
