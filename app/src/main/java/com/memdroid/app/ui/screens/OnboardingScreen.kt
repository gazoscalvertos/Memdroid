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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memdroid.app.R
import com.memdroid.app.data.Community
import com.memdroid.app.data.ShizukuShell
import com.memdroid.app.data.ShizukuState
import com.memdroid.app.data.hasAllFilesAccess
import com.memdroid.app.data.hasUsageAccess
import com.memdroid.app.ui.PermissionCard
import com.memdroid.app.ui.allFilesAccessIntent
import com.memdroid.app.ui.usageAccessIntent

private const val STEPS = 7

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
                    0 -> {
                        StepArt(Icons.Filled.Memory, listOf(Color(0xFF00C853), Color(0xFF00897B)))
                        Text(
                            stringResource(R.string.c_onboarding_welcome),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.c_onboarding_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Pillar(Icons.Filled.Lock, stringResource(R.string.c_onboarding_privacy_title), stringResource(R.string.c_onboarding_privacy_body))
                        Pillar(Icons.Filled.CloudOff, stringResource(R.string.c_onboarding_offline_title), stringResource(R.string.c_onboarding_offline_body))
                        Pillar(Icons.Filled.Block, stringResource(R.string.c_onboarding_no_ads_title), stringResource(R.string.c_onboarding_no_ads_body))
                        Pillar(Icons.Filled.VolunteerActivism, stringResource(R.string.c_onboarding_free_title), stringResource(R.string.c_onboarding_free_body))
                    }
                    1 -> {
                        StepArt(Icons.Filled.Insights, listOf(Color(0xFF2979FF), Color(0xFF7C4DFF)))
                        Step(
                            stringResource(R.string.c_onboarding_what_title),
                            stringResource(R.string.c_onboarding_what_body),
                        )
                    }
                    2 -> {
                        StepArt(Icons.Filled.QueryStats, listOf(Color(0xFF00B0FF), Color(0xFF0091EA)))
                        Step(stringResource(R.string.c_onboarding_usage_title), stringResource(R.string.c_onboarding_usage_body), done = usage)
                        if (!usage) {
                            PermissionCard(stringResource(R.string.c_onboarding_usage_card_title), stringResource(R.string.c_onboarding_usage_card_body)) {
                                context.startActivity(usageAccessIntent())
                            }
                        }
                    }
                    3 -> {
                        StepArt(Icons.Filled.FolderOpen, listOf(Color(0xFFFFB300), Color(0xFFFF6D00)))
                        Step(stringResource(R.string.c_onboarding_files_title), stringResource(R.string.c_onboarding_files_body), done = files)
                        if (!files) {
                            PermissionCard(stringResource(R.string.c_onboarding_files_card_title), stringResource(R.string.c_onboarding_files_card_body)) {
                                context.startActivity(allFilesAccessIntent(context.packageName))
                            }
                        }
                    }
                    4 -> {
                        StepArt(Icons.Filled.NotificationsActive, listOf(Color(0xFFFF4081), Color(0xFFD500F9)))
                        Step(stringResource(R.string.c_onboarding_notifications_title), stringResource(R.string.c_onboarding_notifications_body), done = notifications)
                        if (!notifications) {
                            Button(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.c_onboarding_allow_notifications)) }
                        }
                    }
                    5 -> {
                        StepArt(Icons.Filled.AdminPanelSettings, listOf(Color(0xFF536DFE), Color(0xFF304FFE)))
                        Step(
                            stringResource(R.string.c_onboarding_shizuku_title),
                            stringResource(R.string.c_onboarding_shizuku_body),
                            done = shizuku == ShizukuState.READY,
                        )
                        when (shizuku) {
                            ShizukuState.NOT_RUNNING -> OutlinedButton(onClick = {
                                Community.open(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            }) { Text(stringResource(R.string.c_onboarding_get_shizuku)) }
                            ShizukuState.NO_PERMISSION -> Button(onClick = ShizukuShell::requestPermission) { Text(stringResource(R.string.c_onboarding_allow_in_shizuku)) }
                            ShizukuState.READY -> Unit
                        }
                    }
                    else -> {
                        StepArt(Icons.Filled.Celebration, listOf(Color(0xFFFFC107), Color(0xFFFF8F00)))
                        Step(
                            stringResource(R.string.c_onboarding_done_title),
                            stringResource(R.string.c_onboarding_done_body),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step in 1 until STEPS - 1) TextButton(onClick = { step++ }) { Text(stringResource(R.string.c_onboarding_skip)) } else Spacer(Modifier)
                    Button(onClick = { if (step == STEPS - 1) onDone() else step++ }) {
                        Text(if (step == STEPS - 1) stringResource(R.string.c_onboarding_start) else stringResource(R.string.c_onboarding_next))
                    }
                }
            }
        }
    }
}

/** A big gradient badge with the step's icon, gently floating, with a soft glow and a few bubbles around it. */
@Composable
private fun StepArt(icon: ImageVector, colors: List<Color>) {
    val float by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "offset",
    )
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(190.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(colors.first().copy(alpha = 0.35f), Color.Transparent)))
        )
        Bubble(colors.last(), 18.dp, x = (-78).dp, y = (-52).dp + (float / 2).dp)
        Bubble(colors.first(), 12.dp, x = 82.dp, y = (-36).dp - (float / 2).dp)
        Bubble(colors.last(), 10.dp, x = 70.dp, y = 62.dp + (float / 3).dp)
        Bubble(colors.first(), 8.dp, x = (-64).dp, y = 66.dp)
        Box(
            Modifier.offset(y = float.dp).size(112.dp).shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun Bubble(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(Modifier.offset(x, y).size(size).clip(CircleShape).background(color.copy(alpha = 0.6f)))
}

@Composable
private fun Pillar(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (done) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.c_onboarding_done), tint = MaterialTheme.colorScheme.primary)
    }
    Text(body, style = MaterialTheme.typography.bodyLarge)
}

private fun notificationsAllowed(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
