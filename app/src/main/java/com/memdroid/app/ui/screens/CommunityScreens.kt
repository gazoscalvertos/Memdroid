package com.memdroid.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.memdroid.app.data.Community
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.Tips

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
            "This opens a pre-filled GitHub issue with your app version and device model. " +
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

private val GOLD = Color(0xFFFFC107)
private val AMBER = Color(0xFFFF8F00)

@Composable
fun HeroesScreen() {
    val context = LocalContext.current
    val heroes = remember { Community.heroes(context) }
    val myHero by Prefs.get(context).myHero.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(GOLD, AMBER)))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🍺 Heroes wall", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                    Text(
                        "Memdroid is free, with no ads. These heroes keep it that way. " +
                            "Supporters choose to be listed by name or anonymously.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF3E2723),
                    )
                    Text(
                        if (heroes.size == 1) "1 hero so far" else "${heroes.size} heroes so far",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF3E2723),
                    )
                }
            }
        }
        item { TipJar() }
        myHero?.takeIf { mine -> heroes.none { it.name == mine } }?.let { mine ->
            item {
                Text(
                    "You're on your way to the wall as \"$mine\". You'll appear here for everyone after the next update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 4.dp),
                )
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
        itemsIndexed(heroes) { index, hero -> HeroCard(index, hero) }
    }
}

@Composable
private fun TipJar() {
    val context = LocalContext.current
    val tips = remember { Tips(context) }
    DisposableEffect(tips) {
        tips.connect()
        onDispose { tips.disconnect() }
    }
    val state by tips.state.collectAsState()
    val products by tips.tips.collectAsState()
    val pending by tips.pending.collectAsState()
    val thanks by tips.thanks.collectAsState()

    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Buy me a beer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "A one-off tip through Google Play. Nothing is locked, and you can join the heroes wall afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                Tips.State.CONNECTING -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                Tips.State.UNAVAILABLE -> {
                    Text(
                        "Tips work in the Play Store version of Memdroid.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (Community.DONATE_URL.isNotEmpty()) {
                        Button(onClick = { Community.open(context, Community.DONATE_URL) }) {
                            Text("Buy me a beer 🍺")
                        }
                    }
                }
                Tips.State.READY -> products.forEach { tip ->
                    OutlinedButton(
                        onClick = { context.findActivity()?.let { tips.buy(it, tip) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${tip.product.emoji}  ${tip.product.title}", Modifier.weight(1f))
                        Text(tip.price, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (pending) {
                Text(
                    "Your tip is waiting for the payment to clear. Thank you!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    thanks?.let { HeroOptInDialog(it, onDone = tips::clearThanks) }
}

@Composable
private fun HeroOptInDialog(thanks: Tips.Thanks, onDone: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf(0) } // 0 name, 1 anonymous, 2 don't list
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Cheers! 🍻") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thank you for supporting Memdroid. Would you like to join the heroes wall?")
                listOf("Show my name", "List me as an anonymous hero", "Don't list me").forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == i, onClick = { choice = i })
                        Text(label)
                    }
                }
                if (choice == 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name or nickname") },
                        singleLine = true,
                    )
                }
                if (choice != 2) {
                    Text(
                        "This opens a short request on GitHub. Heroes are added in the next update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice != 0 || name.isNotBlank(),
                onClick = {
                    if (choice != 2) {
                        val anonymous = choice == 1
                        Prefs.get(context).setMyHero(if (anonymous) "Anonymous hero" else name.trim())
                        Community.open(context, Community.heroSignupUrl(name, anonymous, thanks))
                    }
                    onDone()
                },
            ) { Text(if (choice == 2) "Done" else "Join the wall") }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun HeroCard(index: Int, hero: Community.Hero) {
    val anonymous = hero.anonymous || hero.name.isBlank()
    val first = index == 0
    Card(
        Modifier.fillMaxWidth().padding(16.dp, 5.dp),
        border = if (first) BorderStroke(2.dp, GOLD) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(
                    if (anonymous) Brush.linearGradient(listOf(Color(0xFF607D8B), Color(0xFF37474F)))
                    else Brush.linearGradient(listOf(GOLD, AMBER))
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (anonymous) "🕶" else hero.name.trim().first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (anonymous) "Anonymous hero" else hero.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (hero.badge.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                hero.badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    buildString {
                        append(if (first) "Hero #1" else "Hero #${index + 1}")
                        if (hero.since.isNotBlank()) append(" · since ${hero.since}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (first) Text("🏆", style = MaterialTheme.typography.titleLarge)
        }
    }
}
