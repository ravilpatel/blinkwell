package com.mitalipurohit.blinkwell.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import com.mitalipurohit.blinkwell.detection.BurstSessionResult
import com.mitalipurohit.blinkwell.ui.theme.AccentAmber
import com.mitalipurohit.blinkwell.ui.theme.AccentEmerald
import com.mitalipurohit.blinkwell.ui.theme.AccentRose
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary
import com.mitalipurohit.blinkwell.util.BatteryOptimizationHelper

private enum class MonitoringDashboardState {
    STANDBY,
    CALIBRATING,
    ACTIVE
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showBgPermissionDialog by remember { mutableStateOf(false) }
    var showBatteryGuardDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val isBatteryBlocked = uiState.appMode == "monitoring" &&
            BatteryOptimizationHelper.isBatteryGuardTriggered(context, uiState.batteryGuardEnabled, uiState.batteryGuardThreshold)
        
        if (isBatteryBlocked) {
            showBatteryGuardDialog = true
        } else if (uiState.appMode == "monitoring" && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            showBgPermissionDialog = true
        } else {
            viewModel.startMonitoring(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: App Title with Information Emoji Button & Live Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and Info Emoji Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showInfoDialog = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TealPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = TealPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "ℹ️",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Status Indicator
                val statusCategory = uiState.metrics.statusCategory
                val isWarmedUp = uiState.metrics.isWarmedUp
                val statusTint = when {
                    !uiState.isMonitoring -> MaterialTheme.colorScheme.outlineVariant
                    uiState.appMode == "burst" -> TealPrimary
                    !isWarmedUp -> AccentAmber
                    statusCategory == BlinkStatusCategory.NORMAL -> AccentEmerald
                    statusCategory == BlinkStatusCategory.LOW_RATE -> AccentRose
                    else -> AccentAmber
                }

                val statusText = when {
                    !uiState.isMonitoring -> stringResource(R.string.home_status_ready)
                    uiState.appMode == "burst" -> "Burst Active • Counting"
                    !isWarmedUp -> "Calibrating (${uiState.metrics.warmupSecondsElapsed}/30s)"
                    statusCategory == BlinkStatusCategory.NORMAL -> stringResource(R.string.home_face_detected)
                    statusCategory == BlinkStatusCategory.LOW_RATE -> "Low Rate • Blink More"
                    else -> stringResource(R.string.home_no_face_detected)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Segmented Mode Switcher (Burst Mode vs Monitoring Mode)
            ModeSegmentedSwitch(
                selectedMode = uiState.appMode,
                isMonitoring = uiState.isMonitoring,
                onModeSelected = { mode ->
                    viewModel.setAppMode(mode)
                    if (mode == "monitoring" && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                        showBgPermissionDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Central Dashboard Area: Animated switch depending on Mode and State
            if (uiState.appMode == "burst") {
                AnimatedContent(
                    targetState = uiState.isMonitoring,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "burst_dashboard_state"
                ) { isMonitoringActive ->
                    if (isMonitoringActive) {
                        ActiveBurstGauge(
                            elapsedSeconds = uiState.metrics.burstElapsedSeconds,
                            totalSeconds = uiState.metrics.burstTotalSeconds,
                            totalBlinks = uiState.metrics.totalBlinksInSession
                        )
                    } else {
                        StandbyBurstGauge()
                    }
                }
            } else {
                val dashboardState = when {
                    !uiState.isMonitoring -> MonitoringDashboardState.STANDBY
                    !uiState.metrics.isWarmedUp -> MonitoringDashboardState.CALIBRATING
                    else -> MonitoringDashboardState.ACTIVE
                }

                AnimatedContent(
                    targetState = dashboardState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "monitoring_dashboard_state"
                ) { state ->
                    when (state) {
                        MonitoringDashboardState.STANDBY -> StandbyGauge()
                        MonitoringDashboardState.CALIBRATING -> CalibratingGauge(
                            warmupSecondsElapsed = uiState.metrics.warmupSecondsElapsed
                        )
                        MonitoringDashboardState.ACTIVE -> ActiveSpeedometerGauge(
                            bpm = uiState.metrics.currentBpm,
                            statusCategory = uiState.metrics.statusCategory
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Sub-metrics and Descriptive Hints
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        uiState.appMode == "burst" && !uiState.isMonitoring -> stringResource(R.string.home_burst_standby_desc)
                        uiState.appMode == "burst" && uiState.isMonitoring -> "Keep using your phone normally. Final BPM calculated at 5:00."
                        !uiState.isMonitoring -> stringResource(R.string.home_target_rate)
                        !uiState.metrics.isWarmedUp -> "Calibrating baseline... First reading in ${30 - uiState.metrics.warmupSecondsElapsed}s"
                        else -> stringResource(R.string.home_target_rate)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isMonitoring) {
                        "Total blinks this session: ${uiState.metrics.totalBlinksInSession}"
                    } else {
                        "Press Start to begin"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Start / Stop Action Button
            Button(
                onClick = {
                    if (uiState.isMonitoring) {
                        viewModel.stopMonitoring(context)
                    } else {
                        val isBatteryBlocked = uiState.appMode == "monitoring" &&
                            BatteryOptimizationHelper.isBatteryGuardTriggered(context, uiState.batteryGuardEnabled, uiState.batteryGuardThreshold)

                        if (isBatteryBlocked) {
                            showBatteryGuardDialog = true
                        } else {
                            val needNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            
                            if (needNotification) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (uiState.appMode == "monitoring" && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                                showBgPermissionDialog = true
                            } else {
                                viewModel.startMonitoring(context)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isMonitoring) AccentRose else TealPrimary
                )
            ) {
                Icon(
                    imageVector = if (uiState.isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        uiState.isMonitoring && uiState.appMode == "burst" -> stringResource(R.string.home_burst_btn_stop)
                        uiState.isMonitoring -> stringResource(R.string.home_btn_stop)
                        uiState.appMode == "burst" -> stringResource(R.string.home_burst_btn_start)
                        else -> stringResource(R.string.home_btn_start)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Burst Result Modal
        uiState.burstResult?.let { result ->
            BurstResultDialog(
                result = result,
                onDismiss = { viewModel.dismissBurstResult() }
            )
        }

        // 7. Eye Health & Blinking Explanation Info Dialog
        if (showInfoDialog) {
            BlinkInfoDialog(onDismiss = { showInfoDialog = false })
        }

        // 8. Background Usage Permission Dialog for Monitoring Mode
        if (showBgPermissionDialog) {
            BackgroundPermissionDialog(
                onAllow = {
                    showBgPermissionDialog = false
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    viewModel.startMonitoring(context)
                },
                onSkip = {
                    showBgPermissionDialog = false
                    viewModel.startMonitoring(context)
                }
            )
        }

        // 9. Battery Guard Protection Alert Dialog
        if (showBatteryGuardDialog) {
            val currentPct = BatteryOptimizationHelper.getBatteryLevel(context)
            val isPowerSave = BatteryOptimizationHelper.isPowerSaveMode(context)
            AlertDialog(
                onDismissRequest = { showBatteryGuardDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.home_battery_guard_alert_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(
                                R.string.home_battery_guard_alert_body,
                                uiState.batteryGuardThreshold,
                                currentPct
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isPowerSave) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentAmber.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "⚡ Battery Saver Mode is currently active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showBatteryGuardDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.home_battery_guard_dismiss),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}

/**
 * Background Monitoring Permission & Battery Optimization Dialog.
 * Informs the user about background execution and reassures on privacy & battery rules:
 * 1. Screen Off Safety (camera is never used when screen is off or locked)
 * 2. 5-Second Power Saver (camera immediately unbinds if no face is visible in the first 5s)
 */
@Composable
fun BackgroundPermissionDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Text(
                text = stringResource(R.string.bg_permission_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.bg_permission_dialog_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(R.string.bg_permission_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.bg_permission_privacy_header),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = stringResource(R.string.bg_permission_rule_screen_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(R.string.bg_permission_rule_5s_noface),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.bg_permission_btn_allow),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.bg_permission_btn_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * Educational Information Dialog explaining what the app does and why blinking prevents dry eyes and vision issues.
 */
@Composable
fun BlinkInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.info_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.info_dialog_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                // Card 1: What BlinkWell Does
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📱 " + stringResource(R.string.info_dialog_what_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.info_dialog_what_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 2: Why It's Critical for Eye Health
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentEmerald.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💧 " + stringResource(R.string.info_dialog_why_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.info_dialog_why_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tip Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentAmber.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.info_dialog_tip),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.info_dialog_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/**
 * Segmented Control Switch for toggling between Burst Mode (5 min) and Monitoring Mode.
 */
@Composable
fun ModeSegmentedSwitch(
    selectedMode: String,
    isMonitoring: Boolean,
    onModeSelected: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Burst Mode Segment (Default)
            val isBurstSelected = selectedMode == "burst"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isBurstSelected) TealPrimary else Color.Transparent
                    )
                    .clickable(enabled = !isMonitoring) {
                        onModeSelected("burst")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isBurstSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Burst Mode (5 min)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isBurstSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBurstSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Monitoring Mode Segment
            val isMonitoringSelected = selectedMode == "monitoring"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isMonitoringSelected) TealPrimary else Color.Transparent
                    )
                    .clickable(enabled = !isMonitoring) {
                        onModeSelected("monitoring")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (isMonitoringSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Monitoring",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isMonitoringSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isMonitoringSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Standby Gauge for Burst Mode: Displays 5:00 ready timer.
 */
@Composable
fun StandbyBurstGauge() {
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 14.dp.toPx()
            drawArc(
                color = Color.LightGray.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "05:00",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "TARGET WINDOW",
                style = MaterialTheme.typography.labelSmall,
                color = TealPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Active Gauge for 5-Minute Burst Mode: Circular progress countdown with live blinks counter.
 */
@Composable
fun ActiveBurstGauge(
    elapsedSeconds: Long,
    totalSeconds: Long = 300L,
    totalBlinks: Int
) {
    val remainingSeconds = (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
    val progress = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "burst_progress"
    )

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = "%02d:%02d".format(minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "burst_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 14.dp.toPx()

            // Background Track Arc (360 degrees)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Dynamic Progress Arc (Teal gradient fill)
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(TealPrimary, AccentEmerald, TealPrimary)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "$totalBlinks blinks recorded",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TealPrimary
            )
        }
    }
}

/**
 * Detailed Result Modal Dialog displayed upon completing a 5-minute Burst Test.
 */
@Composable
fun BurstResultDialog(
    result: BurstSessionResult,
    onDismiss: () -> Unit
) {
    val isHealthy = result.statusCategory == BlinkStatusCategory.NORMAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isHealthy) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isHealthy) AccentEmerald else AccentRose,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_burst_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_burst_result_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Large Final BPM Badge
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = (if (isHealthy) AccentEmerald else AccentRose).copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "%.0f".format(result.finalBpm),
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isHealthy) AccentEmerald else AccentRose
                        )
                        Text(
                            text = "Blinks / Minute",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isHealthy) stringResource(R.string.home_burst_result_status_healthy) else stringResource(R.string.home_burst_result_status_low),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHealthy) AccentEmerald else AccentRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Total Blinks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${result.totalBlinks}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("5m 00s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("15–20", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Personalized Recommendation Advice
                Text(
                    text = if (isHealthy) stringResource(R.string.home_burst_result_advice_healthy) else stringResource(R.string.home_burst_result_advice_low),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.home_burst_result_dismiss),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/**
 * Standby Gauge for Monitoring Mode: Displayed before monitoring starts.
 */
@Composable
fun StandbyGauge() {
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 18.dp.toPx()

            // Background Track Arc (240 degrees)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.25f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "--",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.home_blinks_per_minute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Calibrating Gauge for Monitoring Mode: Displayed for the first 30 seconds.
 */
@Composable
fun CalibratingGauge(
    warmupSecondsElapsed: Long,
    totalWarmupSeconds: Long = 30L
) {
    val infiniteTransition = rememberInfiniteTransition(label = "calibrating_anim")

    val oscillatingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oscillating_progress"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val overallWarmupProgress = (warmupSecondsElapsed.toFloat() / totalWarmupSeconds.toFloat()).coerceIn(0f, 1f)
    val remainingSeconds = (totalWarmupSeconds - warmupSecondsElapsed).coerceAtLeast(0L)

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 14.dp.toPx()

            drawArc(
                color = Color.LightGray.copy(alpha = 0.20f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (overallWarmupProgress > 0f) {
                drawArc(
                    color = TealPrimary.copy(alpha = 0.25f),
                    startAngle = 150f,
                    sweepAngle = 240f * overallWarmupProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            val startAngle = 150f
            val totalSweep = 240f
            val scannerHeadLength = 45f
            val activeSweep = totalSweep - scannerHeadLength
            val currentStart = startAngle + (activeSweep * oscillatingProgress)

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        TealPrimary.copy(alpha = 0.2f),
                        TealPrimary,
                        AccentAmber,
                        TealPrimary
                    )
                ),
                startAngle = currentStart,
                sweepAngle = scannerHeadLength,
                useCenter = false,
                style = Stroke(width = strokeWidth + 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "CALIBRATING",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TealPrimary,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${remainingSeconds}s remaining",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val sliderWidth = 40.dp
                val maxOffset = 130.dp - sliderWidth
                Box(
                    modifier = Modifier
                        .offset(x = maxOffset * oscillatingProgress)
                        .width(sliderWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(TealPrimary, AccentAmber)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Collecting baseline data",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Active Speedometer Gauge for Monitoring Mode: Live BPM and status colors.
 */
@Composable
fun ActiveSpeedometerGauge(
    bpm: Double,
    statusCategory: BlinkStatusCategory
) {
    val animatedBpm by animateFloatAsState(
        targetValue = bpm.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bpm_anim"
    )

    val maxGaugeBpm = 30f
    val sweepProgress = (animatedBpm / maxGaugeBpm).coerceIn(0f, 1f)

    val gaugeColor by animateColorAsState(
        targetValue = when (statusCategory) {
            BlinkStatusCategory.LOW_RATE -> AccentRose
            BlinkStatusCategory.NORMAL -> AccentEmerald
            BlinkStatusCategory.FACE_NOT_DETECTED -> AccentAmber
        },
        label = "color_anim"
    )

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 18.dp.toPx()

            drawArc(
                color = Color.LightGray.copy(alpha = 0.25f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (sweepProgress > 0f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = 150f,
                    sweepAngle = 240f * sweepProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%.0f".format(bpm),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.home_blinks_per_minute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
