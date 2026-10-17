package com.memdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.ui.isMediumWidth
import com.memdroid.app.ui.screens.BatteryScreen
import com.memdroid.app.ui.screens.FilesScreen
import com.memdroid.app.ui.screens.RamScreen
import com.memdroid.app.ui.screens.SettingsScreen
import com.memdroid.app.ui.screens.StorageScreen
import com.memdroid.app.ui.theme.MemdroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuShell.init()
        enableEdgeToEdge()
        setContent {
            MemdroidTheme {
                MemdroidApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuShell.refresh()
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    RAM("RAM", Icons.Filled.Memory),
    STORAGE("Storage", Icons.Filled.Storage),
    FILES("Files", Icons.Filled.Folder),
    BATTERY("Battery", Icons.Filled.BatteryChargingFull),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun MemdroidApp() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
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
                Tab.RAM -> RamScreen(modifier)
                Tab.STORAGE -> StorageScreen(modifier)
                Tab.FILES -> FilesScreen(modifier)
                Tab.BATTERY -> BatteryScreen(modifier)
                Tab.SETTINGS -> SettingsScreen(modifier)
            }
        }
    }
}
