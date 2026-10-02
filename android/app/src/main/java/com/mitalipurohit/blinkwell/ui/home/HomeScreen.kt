package com.mitalipurohit.blinkwell.ui.home

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.detection.BlinkAnalyzer
import com.mitalipurohit.blinkwell.ui.theme.AccentAmber
import com.mitalipurohit.blinkwell.ui.theme.AccentEmerald
import com.mitalipurohit.blinkwell.ui.theme.AccentRose
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary
import java.util.concurrent.Executors

@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

                // Face Status Indicator
                val faceDetected = uiState.metrics.isFaceDetected
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (faceDetected) AccentEmerald else if (uiState.isMonitoring) AccentAmber else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (faceDetected) stringResource(R.string.home_face_detected) else if (uiState.isMonitoring) stringResource(R.string.home_no_face_detected) else stringResource(R.string.home_status_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Central Gauge / Blink Rate Dial
            BlinkGauge(
                bpm = uiState.metrics.currentBpm,
                isMonitoring = uiState.isMonitoring
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics and Hints
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_target_rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total blinks this session: ${uiState.metrics.totalBlinksInSession}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start / Stop Toggle Button
            Button(
                onClick = { viewModel.toggleMonitoring(context) },
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

@Composable
fun BlinkGauge(
    bpm: Double,
    isMonitoring: Boolean
) {
    val animatedBpm by animateFloatAsState(
        targetValue = bpm.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bpm_anim"
    )

    val maxGaugeBpm = 30f
    val sweepProgress = (animatedBpm / maxGaugeBpm).coerceIn(0f, 1f)

    val gaugeColor by animateColorAsState(
        targetValue = when {
            !isMonitoring -> MaterialTheme.colorScheme.outlineVariant
            bpm < 10.0 -> AccentRose
            bpm < 14.0 -> AccentAmber
            else -> AccentEmerald
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
            if (isMonitoring && sweepProgress > 0f) {
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
                text = if (isMonitoring) "%.0f".format(bpm) else "--",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                fontWeight = FontWeight.Bold,
                color = if (isMonitoring) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
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
