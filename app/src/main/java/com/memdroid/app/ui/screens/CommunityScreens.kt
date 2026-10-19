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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.memdroid.app.R
import com.memdroid.app.data.Community
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.Tips

@Composable
fun FeatureIdeasScreen(onSuggest: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.a_ideas_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
        }
        items(Community.ideas) { idea ->
            Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium)
                    Text(idea.description, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { Community.open(context, Community.ideaUrl(idea)) }) { Text(stringResource(R.string.a_ideas_vote)) }
                }
            }
        }
        item {
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSuggest) { Text(stringResource(R.string.a_ideas_suggest)) }
                TextButton(onClick = {
                    Community.open(context, "${Community.REPO_URL}/issues?q=is%3Aopen+label%3Aenhancement+sort%3Areactions-%2B1-desc")
                }) { Text(stringResource(R.string.a_ideas_see_all)) }
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
            FilterChip(selected = bug, onClick = { bug = true }, label = { Text(stringResource(R.string.a_feedback_bug)) })
            FilterChip(selected = !bug, onClick = { bug = false }, label = { Text(stringResource(R.string.a_feedback_feature)) })
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(if (bug) stringResource(R.string.a_feedback_title_bug) else stringResource(R.string.a_feedback_title_feature)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            label = { Text(stringResource(R.string.a_feedback_details)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        Text(
            stringResource(R.string.a_feedback_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { Community.open(context, Community.newIssueUrl(context, bug, title, details)) },
            enabled = title.isNotBlank(),
        ) { Text(stringResource(R.string.a_feedback_continue)) }
    }
}

@Composable
fun ChangelogScreen() {
    LazyColumn(Modifier.fillMaxSize()) {
        items(Community.changelog) { release ->
            Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.a_changelog_version, release.version), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(release.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    release.changes.forEach { Text(stringResource(R.string.a_changelog_bullet, it), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private val GOLD = Color(0xFFFFC107)

/** Stored in Prefs as the hero name for anonymous sign-ups; shown translated. */
private const val ANONYMOUS_HERO = "Anonymous hero"
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
                    Text(stringResource(R.string.a_heroes_title), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                    Text(
                        stringResource(R.string.a_heroes_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF3E2723),
                    )
                    Text(
                        pluralStringResource(R.plurals.a_heroes_count, heroes.size, heroes.size),
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
                    stringResource(R.string.a_heroes_on_your_way, if (mine == ANONYMOUS_HERO) stringResource(R.string.a_hero_anonymous) else mine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 4.dp),
                )
            }
        }
        if (heroes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.a_heroes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        itemsIndexed(heroes) { index, hero -> HeroCard(index, hero) }
    }
}

/** The Buy me a beer sheet behind the 🍺 button in the app bar. */
@Composable
fun TipSheet(onDismiss: () -> Unit, onOpenHeroes: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            TipJar()
            TextButton(onClick = onOpenHeroes, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.a_heroes_see_wall))
            }
        }
    }
}

@Composable
fun TipJar() {
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
            Text(stringResource(R.string.a_tip_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.a_tip_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                Tips.State.CONNECTING -> Text(stringResource(R.string.a_tip_loading), style = MaterialTheme.typography.bodySmall)
                Tips.State.UNAVAILABLE -> {
                    Text(
                        stringResource(R.string.a_tip_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (Community.DONATE_URL.isNotEmpty()) {
                        Button(onClick = { Community.open(context, Community.DONATE_URL) }) {
                            Text(stringResource(R.string.a_tip_donate))
                        }
                    }
                }
                Tips.State.READY -> products.forEach { tip ->
                    OutlinedButton(
                        onClick = { context.findActivity()?.let { tips.buy(it, tip) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${tip.product.emoji}  ${stringResource(tip.product.titleRes)}", Modifier.weight(1f))
                        Text(tip.price, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (pending) {
                Text(
                    stringResource(R.string.a_tip_pending),
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
        title = { Text(stringResource(R.string.a_optin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.a_optin_text))
                listOf(
                    stringResource(R.string.a_optin_show_name),
                    stringResource(R.string.a_optin_anonymous),
                    stringResource(R.string.a_optin_dont_list),
                ).forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == i, onClick = { choice = i })
                        Text(label)
                    }
                }
                if (choice == 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text(stringResource(R.string.a_optin_name_label)) },
                        singleLine = true,
                    )
                }
                if (choice != 2) {
                    Text(
                        stringResource(R.string.a_optin_note),
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
                        Prefs.get(context).setMyHero(if (anonymous) ANONYMOUS_HERO else name.trim())
                        Community.open(context, Community.heroSignupUrl(name, anonymous, thanks))
                    }
                    onDone()
                },
            ) { Text(if (choice == 2) stringResource(R.string.a_optin_done) else stringResource(R.string.a_optin_join)) }
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
                        if (anonymous) stringResource(R.string.a_hero_anonymous) else hero.name,
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
                val heroNumber = stringResource(R.string.a_hero_number, index + 1)
                val heroSince = if (hero.since.isNotBlank()) stringResource(R.string.a_hero_since, hero.since) else ""
                Text(
                    heroNumber + heroSince,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (first) Text("🏆", style = MaterialTheme.typography.titleLarge)
        }
    }
}
