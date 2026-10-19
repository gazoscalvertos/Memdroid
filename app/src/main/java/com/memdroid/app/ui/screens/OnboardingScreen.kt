package com.memdroid.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memdroid.app.data.Community
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.allFilesAccessIntent
import com.memdroid.app.ui.usageAccessIntent

private const val STEPS = 6

/** First-run setup: explains each permission and lets the user grant or skip it. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var usage by remember { mutableStateOf(hasUsageAccess(context)) }
    var files by remember { mutableStateOf(hasAllFilesAccess()) }
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    val shizuku by ShizukuShell.state.collectAsState()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = it
    }
    LifecycleResumeEffect(Unit) {
        usage = hasUsageAccess(context)
        files = hasAllFilesAccess()
        notifications = notificationsAllowed(context)
        ShizukuShell.refresh()
        onPauseOrDispose { }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(12.dp))
            Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    0 -> Step(
                        "Welcome to Memdroid",
                        "Memdroid shows what's using your device's memory, storage and battery, and helps you free it up. " +
                            "Let's set up a few permissions. You can skip any of them and change them later in Settings.",
                    )
                    1 -> {
                        Step("Usage access", "Lets Memdroid measure each app's storage, see when you last used it and spot battery drain.", done = usage)
                        if (!usage) {
                            PermissionCard("Allow usage access", "Find Memdroid in the list and turn it on.") {
                                context.startActivity(usageAccessIntent())
                            }
                        }
                    }
                    2 -> {
                        Step("All files access", "Needed for large files, duplicates, old downloads, the folder view and the recycle bin.", done = files)
                        if (!files) {
                            PermissionCard("Allow all files access", "Turn on Allow access to manage all files.") {
                                context.startActivity(allFilesAccessIntent(context.packageName))
                            }
                        }
                    }
                    3 -> {
                        Step("Notifications", "Used for the optional low storage, low RAM and battery drain alerts. They're all off until you turn them on.", done = notifications)
                        if (!notifications) {
                            Button(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                        }
                    }
                    4 -> {
                        Step(
                            "Shizuku (optional)",
                            "Android doesn't let normal apps see or stop other apps. Shizuku gives Memdroid that access without rooting, " +
                                "for the running apps list, kill, auto-kill, battery by app and clearing caches. " +
                                "Install it, start it with wireless debugging, then come back here.",
                            done = shizuku == ShizukuState.READY,
                        )
                        when (shizuku) {
                            ShizukuState.NOT_RUNNING -> OutlinedButton(onClick = {
                                Community.open(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            }) { Text("Get Shizuku") }
                            ShizukuState.NO_PERMISSION -> Button(onClick = ShizukuShell::requestPermission) { Text("Allow Memdroid in Shizuku") }
                            ShizukuState.READY -> Unit
                        }
                    }
                    else -> Step(
                        "You're all set",
                        "Start with the Storage tab's smart cleanup to see what you can free. " +
                            "You can run this setup again from Settings.",
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step in 1 until STEPS - 1) TextButton(onClick = { step++ }) { Text("Skip") } else Spacer(Modifier)
                    Button(onClick = { if (step == STEPS - 1) onDone() else step++ }) {
                        Text(if (step == STEPS - 1) "Start" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (done) Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
    }
    Text(body, style = MaterialTheme.typography.bodyLarge)
}

private fun notificationsAllowed(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
