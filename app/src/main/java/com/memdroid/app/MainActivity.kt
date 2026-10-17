package com.memdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun MemdroidApp() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == Tab.RAM) "Memdroid" else tab.title) }) },
        bottomBar = {
            NavigationBar {
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
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.RAM -> RamScreen(modifier)
            Tab.STORAGE -> StorageScreen(modifier)
            Tab.FILES -> FilesScreen(modifier)
            Tab.SETTINGS -> SettingsScreen(modifier)
        }
    }
}
