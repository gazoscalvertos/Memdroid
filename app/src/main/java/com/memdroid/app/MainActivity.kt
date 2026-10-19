package com.memdroid.app

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.memdroid.app.ui.screens.TipSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.memdroid.app.data.AutoKillService
import com.memdroid.app.data.Community
import com.memdroid.app.data.CrashReports
import com.memdroid.app.data.Prefs
import com.memdroid.app.data.MonitorWorker
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.ui.isMediumWidth
import com.memdroid.app.ui.screens.BatteryScreen
import com.memdroid.app.ui.screens.OnboardingScreen
import com.memdroid.app.ui.screens.ToolsScreen
import com.memdroid.app.ui.screens.RamScreen
import com.memdroid.app.ui.screens.SettingsScreen
import com.memdroid.app.ui.screens.StorageScreen
import com.memdroid.app.ui.theme.MemdroidTheme

class MainActivity : ComponentActivity() {
    /** Set when the widget's Kill all button opened the app; the RAM screen asks to confirm. */
    private val killAllRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorWorker.schedule(this)
        AutoKillService.sync(this)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MemdroidTheme {
                MemdroidApp(
                    killAllRequested = killAllRequested.value,
                    onKillAllHandled = { killAllRequested.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_KILL_ALL, false) == true) {
            killAllRequested.value = true
            intent.removeExtra(EXTRA_KILL_ALL)
        }
    }

    companion object {
        const val EXTRA_KILL_ALL = "kill_all"
    }

    override fun onResume() {
        super.onResume()
        ShizukuShell.refresh()
    }
}

private enum class Tab(@StringRes val title: Int, val icon: ImageVector) {
    RAM(R.string.a_tab_ram, Icons.Filled.Memory),
    STORAGE(R.string.a_tab_storage, Icons.Filled.Storage),
    BATTERY(R.string.a_tab_battery, Icons.Filled.BatteryChargingFull),
    TOOLS(R.string.a_tab_tools, Icons.Filled.Build),
    SETTINGS(R.string.a_tab_settings, Icons.Filled.Settings),
}

@Composable
private fun MemdroidApp(killAllRequested: Boolean, onKillAllHandled: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val onboarded by prefs.onboarded.collectAsState()
    if (!onboarded) {
        OnboardingScreen(onDone = { prefs.setOnboarded(true) })
        return
    }
    CrashReportPrompt()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var toolRequest by remember { mutableStateOf<String?>(null) }
    var showTips by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(killAllRequested) { if (killAllRequested) tabIndex = Tab.RAM.ordinal }

    // After a few successful cleanups, ask once for a Play rating. Play decides whether to show it.
    val reviewDue by prefs.reviewDue.collectAsState()
    LaunchedEffect(reviewDue) {
        val activity = context as? Activity
        if (reviewDue && activity != null) {
            try {
                val manager = ReviewManagerFactory.create(activity)
                manager.launchReview(activity, manager.requestReview())
            } catch (_: Exception) {
                // Not installed from Play, or Play is unavailable.
            }
            prefs.setReviewAsked()
        }
    }
    if (showTips) {
        TipSheet(
            onDismiss = { showTips = false },
            onOpenHeroes = { showTips = false; toolRequest = "HEROES"; tabIndex = Tab.TOOLS.ordinal },
        )
    }
    val tab = Tab.entries[tabIndex]
    // Wide screens (unfolded foldables, tablets, landscape) get a side rail instead of a bottom bar.
    val wide = isMediumWidth()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.RAM) "Memdroid" else stringResource(tab.title)) },
                actions = {
                    IconButton(onClick = { showTips = true }) {
                        val beerDescription = stringResource(R.string.a_cd_buy_me_a_beer)
                        Text("🍺", fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = beerDescription })
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.title)) },
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) {
                NavigationRail {
                    Tab.entries.forEachIndexed { index, t ->
                        NavigationRailItem(
                            selected = index == tabIndex,
                            onClick = { tabIndex = index },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.title)) },
                        )
                    }
                }
            }
            val modifier = Modifier.weight(1f)
            when (tab) {
                Tab.RAM -> RamScreen(modifier, killAllRequested = killAllRequested, onKillAllHandled = onKillAllHandled)
                Tab.STORAGE -> StorageScreen(modifier, onOpenTool = { toolRequest = it; tabIndex = Tab.TOOLS.ordinal })
                Tab.BATTERY -> BatteryScreen(modifier)
                Tab.TOOLS -> ToolsScreen(modifier, request = toolRequest, onRequestHandled = { toolRequest = null })
                Tab.SETTINGS -> SettingsScreen(modifier)
            }
        }
    }
}

/** After a crash, offers to report it as a pre-filled GitHub issue. Nothing is sent without a tap. */
@Composable
private fun CrashReportPrompt() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashReports.pending(context)) }
    val trace = crash ?: return
    AlertDialog(
        onDismissRequest = { CrashReports.clear(context); crash = null },
        title = { Text(stringResource(R.string.a_crash_title)) },
        text = {
            Text(stringResource(R.string.a_crash_text))
        },
        confirmButton = {
            TextButton(onClick = {
                Community.open(context, CrashReports.issueUrl(context, "Crash: ${trace.lineSequence().drop(1).firstOrNull()?.take(80) ?: "unknown"}", trace))
                CrashReports.clear(context)
                crash = null
            }) { Text(stringResource(R.string.a_crash_report)) }
        },
        dismissButton = {
            TextButton(onClick = { CrashReports.clear(context); crash = null }) { Text(stringResource(R.string.a_crash_not_now)) }
        },
    )
}
