package com.memdroid.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private enum class Tool(val title: String, val description: String, val icon: ImageVector) {
    AUTO_KILL("Auto-kill rules", "Stop chosen apps when the screen turns off or RAM runs low", Icons.Filled.AutoMode),
    HISTORY("Usage history", "RAM, storage and battery over the last 30 days", Icons.Filled.Timeline),
    APP_GROWTH("App growth", "Which apps' storage is growing fastest", Icons.AutoMirrored.Filled.TrendingUp),
    LARGE_FILES("Large files", "Find and delete files over 50 MB", Icons.Filled.FilePresent),
    DUPLICATES("Duplicate files", "Find identical photos, videos and files", Icons.Filled.ContentCopy),
    UNUSED_APPS("Unused apps", "Apps you haven't opened in a while", Icons.Filled.HourglassEmpty),
    IDEAS("Feature ideas", "Vote on what gets built next", Icons.Filled.Lightbulb),
    FEEDBACK("Report a bug or request a feature", "Opens a pre-filled GitHub issue", Icons.Filled.BugReport),
    CHANGELOG("What's new", "Changes in each version", Icons.Filled.NewReleases),
    HEROES("Heroes wall", "The people supporting Memdroid", Icons.Filled.Favorite),
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<Tool?>(null) }
    BackHandler(enabled = open != null) { open = null }

    val tool = open
    if (tool == null) {
        LazyColumn(modifier.fillMaxSize()) {
            items(Tool.entries) { t -> Column {
                if (t == Tool.IDEAS) {
                    Text(
                        "Community",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                }
                Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp).clickable { open = t }) {
                    ListItem(
                        headlineContent = { Text(t.title) },
                        supportingContent = { Text(t.description) },
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
            }
            Text(tool.title, style = MaterialTheme.typography.titleMedium)
        }
        when (tool) {
            Tool.AUTO_KILL -> AutoKillScreen()
            Tool.HISTORY -> HistoryScreen()
            Tool.APP_GROWTH -> AppGrowthScreen()
            Tool.LARGE_FILES -> FilesScreen()
            Tool.DUPLICATES -> DuplicatesScreen()
            Tool.UNUSED_APPS -> UnusedAppsScreen()
            Tool.IDEAS -> FeatureIdeasScreen(onSuggest = { open = Tool.FEEDBACK })
            Tool.FEEDBACK -> FeedbackScreen()
            Tool.CHANGELOG -> ChangelogScreen()
            Tool.HEROES -> HeroesScreen()
        }
    }
}
