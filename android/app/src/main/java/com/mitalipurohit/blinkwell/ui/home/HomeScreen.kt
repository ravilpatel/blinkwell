package com.mitalipurohit.blinkwell.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.detection.BlinkAnalyzer
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import com.mitalipurohit.blinkwell.ui.theme.AccentAmber
import com.mitalipurohit.blinkwell.ui.theme.AccentEmerald
import com.mitalipurohit.blinkwell.ui.theme.AccentRose
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary
import java.util.concurrent.Executors

private enum class DashboardState {
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
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.startMonitoring(context)
    }

    // In App-Only mode, bind CameraX when monitoring is active
    DisposableEffect(uiState.isMonitoring, uiState.currentMode) {
        var cameraProvider: ProcessCameraProvider? = null
        val executor = Executors.newSingleThreadExecutor()
        var analyzer: BlinkAnalyzer? = null

        if (uiState.isMonitoring && uiState.currentMode == "app_only") {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    cameraProvider?.unbindAll()

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()

                    analyzer = BlinkAnalyzer(viewModel.blinkDetector)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(320, 240))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor, analyzer!!)

                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis
                    )
                } catch (ignored: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try {
                cameraProvider?.unbindAll()
                analyzer?.release()
                executor.shutdown()
            } catch (ignored: Exception) {
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Mode Badge & Face Presence Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Badge
                val isBg = uiState.currentMode == "background"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isBg) TealPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isBg) stringResource(R.string.home_mode_badge_bg) else stringResource(R.string.home_mode_badge_app),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBg) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Status Indicator (matches sticky notification color code: Green / Red / Yellow)
                val statusCategory = uiState.metrics.statusCategory
                val isWarmedUp = uiState.metrics.isWarmedUp
                val statusTint = when {
                    !uiState.isMonitoring -> MaterialTheme.colorScheme.outlineVariant
                    !isWarmedUp -> AccentAmber
                    statusCategory == BlinkStatusCategory.NORMAL -> AccentEmerald
                    statusCategory == BlinkStatusCategory.LOW_RATE -> AccentRose
                    else -> AccentAmber
                }

                val statusText = when {
                    !uiState.isMonitoring -> stringResource(R.string.home_status_ready)
                    !isWarmedUp -> "Calibrating (${uiState.metrics.warmupSecondsElapsed}/30s)"
                    statusCategory == BlinkStatusCategory.NORMAL -> stringResource(R.string.home_face_detected)
                    statusCategory == BlinkStatusCategory.LOW_RATE -> "Low Rate • Blink More"
                    else -> stringResource(R.string.home_no_face_detected)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            Spacer(modifier = Modifier.height(24.dp))

            // Central Dashboard Area: Animated switch between Standby, Calibrating & Speedometer Dashboard
            val dashboardState = when {
                !uiState.isMonitoring -> DashboardState.STANDBY
                !uiState.metrics.isWarmedUp -> DashboardState.CALIBRATING
                else -> DashboardState.ACTIVE
            }

            AnimatedContent(
                targetState = dashboardState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "dashboard_state"
            ) { state ->
                when (state) {
                    DashboardState.STANDBY -> StandbyGauge()
                    DashboardState.CALIBRATING -> CalibratingGauge(
                        warmupSecondsElapsed = uiState.metrics.warmupSecondsElapsed
                    )
                    DashboardState.ACTIVE -> ActiveSpeedometerGauge(
                        bpm = uiState.metrics.currentBpm,
                        statusCategory = uiState.metrics.statusCategory
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics and Hints
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        !uiState.isMonitoring -> stringResource(R.string.home_target_rate)
                        !uiState.metrics.isWarmedUp -> "Calibrating baseline... First reading in ${30 - uiState.metrics.warmupSecondsElapsed}s"
                        else -> stringResource(R.string.home_target_rate)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isMonitoring && !uiState.metrics.isWarmedUp) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (uiState.isMonitoring && !uiState.metrics.isWarmedUp) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (uiState.isMonitoring) {
                        "Total blinks this session: ${uiState.metrics.totalBlinksInSession}"
                    } else {
                        "Press Start Monitoring to begin"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start / Stop Toggle Button with permission check
            Button(
                onClick = {
                    if (uiState.isMonitoring) {
                        viewModel.stopMonitoring(context)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startMonitoring(context)
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
                    text = if (uiState.isMonitoring) stringResource(R.string.home_btn_stop) else stringResource(R.string.home_btn_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Standby Gauge: Displayed before monitoring starts.
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

        // Center Content
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
 * Calibrating Gauge: Displayed for the first 30 seconds while collecting initial blink rate data.
 * Features an animated oscillating slider moving 0% to 100% and back to 0%, 30s countdown, and pulsing sensor indicator.
 */
@Composable
fun CalibratingGauge(
    warmupSecondsElapsed: Long,
    totalWarmupSeconds: Long = 30L
) {
    val infiniteTransition = rememberInfiniteTransition(label = "calibrating_anim")

    // Oscillating slider progress from 0.0f to 1.0f and back to 0.0f (0% -> 100% -> 0%)
    val oscillatingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oscillating_progress"
    )

    // Gentle pulse animation for the center badge
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

            // 1. Background Track Arc (240 degrees)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.20f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. 30-Second Overall Progress Background Arc (Subtle Teal)
            if (overallWarmupProgress > 0f) {
                drawArc(
                    color = TealPrimary.copy(alpha = 0.25f),
                    startAngle = 150f,
                    sweepAngle = 240f * overallWarmupProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 3. Oscillating Calibrating Scanner Slider Arc (Moving back and forth 0% <-> 100%)
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

        // Center Content of Calibrating Gauge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Pulsing Eye / Sensor Icon Badge
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

            // Oscillating Back-and-Forth Calibration Slider Bar (0% <-> 100% and back)
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
                text = "Collecting initial data",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Active Speedometer Gauge: Displayed after 30-second calibration period.
 * Reflects real-time blink rate and dynamic color status (Emerald / Rose).
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

            // Background Track Arc (240 degrees)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.25f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress Arc
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

        // Center Content
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
