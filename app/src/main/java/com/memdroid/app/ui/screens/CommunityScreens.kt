package com.memdroid.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.memdroid.app.data.Community

@Composable
fun FeatureIdeasScreen(onSuggest: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Ideas for future versions. Vote with a 👍 on GitHub, and the most popular get built first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
        }
        items(Community.ideas) { idea ->
            Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium)
                    Text(idea.description, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { Community.open(context, Community.ideaUrl(idea)) }) { Text("Vote on GitHub") }
                }
            }
        }
        item {
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSuggest) { Text("Suggest a feature") }
                TextButton(onClick = {
                    Community.open(context, "${Community.REPO_URL}/issues?q=is%3Aopen+label%3Aenhancement+sort%3Areactions-%2B1-desc")
                }) { Text("See all requests") }
            }
        }
    }
}

@Composable
fun FeedbackScreen(startAsBug: Boolean = true) {
    val context = LocalContext.current
    var bug by rememberSaveable { mutableStateOf(startAsBug) }
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = bug, onClick = { bug = true }, label = { Text("Report a bug") })
            FilterChip(selected = !bug, onClick = { bug = false }, label = { Text("Request a feature") })
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(if (bug) "What went wrong?" else "What would you like?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            label = { Text("Details") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        Text(
            "This opens a pre-filled GitHub issue with your app version and phone model. " +
                "You'll need a free GitHub account to submit it. Reports are public, so leave out anything personal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { Community.open(context, Community.newIssueUrl(context, bug, title, details)) },
            enabled = title.isNotBlank(),
        ) { Text("Continue on GitHub") }
    }
}

@Composable
fun ChangelogScreen() {
    LazyColumn(Modifier.fillMaxSize()) {
        items(Community.changelog) { release ->
            Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Version ${release.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(release.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    release.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
fun HeroesScreen() {
    val context = LocalContext.current
    val heroes = remember { Community.heroes(context) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Memdroid is free. These heroes chipped in to keep it going. " +
                        "Supporters choose whether to be listed by name or anonymously.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (Community.DONATE_URL.isNotEmpty()) {
                    Button(onClick = { Community.open(context, Community.DONATE_URL) }) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Text("  Support Memdroid")
                    }
                }
            }
        }
        if (heroes.isEmpty()) {
            item {
                Text(
                    "No heroes yet. Be the first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(heroes) { hero ->
            Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        if (hero.anonymous || hero.name.isBlank()) "Anonymous hero" else hero.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    if (hero.since.isNotBlank()) {
                        Text(hero.since, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
