package com.memdroid.app

import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.memdroid.app.data.AutoKillService
import com.memdroid.app.data.MonitorWorker
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.ui.isMediumWidth
import com.memdroid.app.ui.screens.BatteryScreen
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
        ShizukuShell.init()
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

private enum class Tab(val title: String, val icon: ImageVector) {
    RAM("RAM", Icons.Filled.Memory),
    STORAGE("Storage", Icons.Filled.Storage),
    BATTERY("Battery", Icons.Filled.BatteryChargingFull),
    TOOLS("Tools", Icons.Filled.Build),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun MemdroidApp(killAllRequested: Boolean, onKillAllHandled: () -> Unit) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(killAllRequested) { if (killAllRequested) tabIndex = Tab.RAM.ordinal }
    val tab = Tab.entries[tabIndex]
    // Wide screens (unfolded foldables, tablets, landscape) get a side rail instead of a bottom bar.
    val wide = isMediumWidth()
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == Tab.RAM) "Memdroid" else tab.title) }) },
        bottomBar = {
            if (!wide) NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
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
                            label = { Text(t.title) },
                        )
                    }
                }
            }
            val modifier = Modifier.weight(1f)
            when (tab) {
                Tab.RAM -> RamScreen(modifier, killAllRequested = killAllRequested, onKillAllHandled = onKillAllHandled)
                Tab.STORAGE -> StorageScreen(modifier)
                Tab.BATTERY -> BatteryScreen(modifier)
                Tab.TOOLS -> ToolsScreen(modifier)
                Tab.SETTINGS -> SettingsScreen(modifier)
            }
        }
    }
}
