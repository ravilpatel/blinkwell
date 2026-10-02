package com.mitalipurohit.blinkwell.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity
import com.mitalipurohit.blinkwell.ui.theme.AccentAmber
import com.mitalipurohit.blinkwell.ui.theme.AccentEmerald
import com.mitalipurohit.blinkwell.ui.theme.AccentRose
import com.mitalipurohit.blinkwell.ui.theme.TealDark
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDayInfo by remember { mutableStateOf<DayStreakInfo?>(null) }
    var selectedGraphPoint by remember { mutableStateOf<GraphPoint?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            item {
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Overview Metric Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = "Today's Average",
                        subTitle = "Days BPM",
                        value = "%.1f".format(uiState.todayDaysBpm),
                        unit = "BPM",
                        icon = Icons.Default.Speed,
                        tint = TealPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Healthy Streak",
                        subTitle = "Consecutive",
                        value = "${uiState.streakSummary.currentHealthyStreak}",
                        unit = "Days",
                        icon = Icons.Default.LocalFireDepartment,
                        tint = if (uiState.streakSummary.currentHealthyStreak > 0) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = stringResource(R.string.stats_alerts_today),
                        subTitle = "Low blink alerts",
                        value = "${uiState.todayAlerts}",
                        unit = "Alerts",
                        icon = Icons.Default.NotificationsActive,
                        tint = AccentRose,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = stringResource(R.string.stats_total_sessions),
                        subTitle = "30-day window",
                        value = "${uiState.totalSessions}",
                        unit = "Sessions",
                        icon = Icons.Default.History,
                        tint = TealDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Time Range Filters (15 min, 1 hour, 1 day, 7 day, 30 day)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Time Range Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimeRangeFilter.values().forEach { filter ->
                            val isSelected = uiState.selectedTimeRange == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedGraphPoint = null
                                    viewModel.setTimeRange(filter)
                                },
                                label = {
                                    Text(
                                        text = filter.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Interactive Annotated Graph View
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (uiState.graphData.isDaysBpm) "Days BPM Trend" else "Blink Rate (BPM)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.graphData.isDaysBpm) "Day's average blink rate" else "Real-time blinks per minute",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TealPrimary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Avg: %.1f BPM".format(uiState.graphData.averageBpm),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected Point Info Banner
                        AnimatedVisibility(visible = selectedGraphPoint != null) {
                            selectedGraphPoint?.let { point ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (point.secondaryLabel.isNotEmpty()) "${point.secondaryLabel} (${point.xLabel})" else point.xLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${if (point.isDaysBpm) "Days BPM" else "BPM"}: %.1f".format(point.bpm),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (point.bpm >= uiState.thresholdBpm) AccentEmerald else AccentRose
                                        )
                                    }
                                }
                            }
                        }

                        // Annotated Graph with X & Y Numeric Axes
                        AnnotatedBpmGraph(
                            graphData = uiState.graphData,
                            selectedPoint = selectedGraphPoint,
                            onPointSelected = { selectedGraphPoint = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )
                    }
                }
            }

            // Streak Viewer Card with Green/Red Color Coding & 30-Day Storage
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.stats_streak_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Color-coded eye health history (30 days max)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Streak Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StreakPillBadge(
                                icon = Icons.Default.LocalFireDepartment,
                                title = "Current",
                                value = "${uiState.streakSummary.currentHealthyStreak}d",
                                tint = AccentAmber,
                                modifier = Modifier.weight(1f)
                            )
                            StreakPillBadge(
                                icon = Icons.Default.Star,
                                title = "Best",
                                value = "${uiState.streakSummary.bestHealthyStreak}d",
                                tint = TealPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            StreakPillBadge(
                                icon = Icons.Default.CheckCircle,
                                title = "Healthy",
                                value = "${uiState.streakSummary.totalHealthyDays}/30",
                                tint = AccentEmerald,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Selected Day Detail Box
                        AnimatedVisibility(visible = selectedDayInfo != null) {
                            selectedDayInfo?.let { day ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when (day.status) {
                                        StreakStatus.HEALTHY -> AccentEmerald.copy(alpha = 0.12f)
                                        StreakStatus.NOT_HEALTHY -> AccentRose.copy(alpha = 0.12f)
                                        StreakStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${day.dateFormatted} (Day ${day.dayIndex})${if (day.isToday) " • Today" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = when (day.status) {
                                                    StreakStatus.HEALTHY -> "🟢 Healthy (Target Met)"
                                                    StreakStatus.NOT_HEALTHY -> "🔴 Not-Healthy (Low Rate)"
                                                    StreakStatus.NO_DATA -> "⚪ No Session Recorded"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = if (day.daysBpm > 0) "Days BPM: %.1f".format(day.daysBpm) else "No Data",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when (day.status) {
                                                StreakStatus.HEALTHY -> AccentEmerald
                                                StreakStatus.NOT_HEALTHY -> AccentRose
                                                StreakStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 30-Day Grid Viewer (6 columns x 5 rows)
                        Text(
                            text = "Last 30 Days (Tap to view details):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 6,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.streakSummary.thirtyDaysStreakList.forEach { day ->
                                val isSelected = selectedDayInfo?.dayIndex == day.dayIndex
                                DayStreakTile(
                                    day = day,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedDayInfo = if (isSelected) null else day
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Color Coding Legend
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Color Coding Legend:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentEmerald))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Healthy (Days BPM ≥ ${uiState.thresholdBpm})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentRose))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Not-Healthy (< ${uiState.thresholdBpm})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 30-Day Retention Notice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.stats_retention_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Recent Sessions Section
            item {
                Text(
                    text = "Recent Monitoring Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.recentSessions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.recentSessions) { session ->
                    SessionItemCard(session, threshold = uiState.thresholdBpm)
                }
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    subTitle: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StreakPillBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
        }
    }
}

@Composable
fun DayStreakTile(
    day: DayStreakInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (day.status) {
        StreakStatus.HEALTHY -> AccentEmerald
        StreakStatus.NOT_HEALTHY -> AccentRose
        StreakStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (day.status) {
        StreakStatus.HEALTHY, StreakStatus.NOT_HEALTHY -> Color.White
        StreakStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, TealPrimary, RoundedCornerShape(8.dp))
                } else if (day.isToday) {
                    Modifier.border(1.5.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "D${day.dayIndex}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = if (day.daysBpm > 0) "%.0f".format(day.daysBpm) else "--",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun AnnotatedBpmGraph(
    graphData: GraphUiData,
    selectedPoint: GraphPoint?,
    onPointSelected: (GraphPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = graphData.points
    val threshold = graphData.thresholdBpm.toFloat()
    val yAnnotations = graphData.yNumericAnnotations
    val maxY = (yAnnotations.maxOrNull() ?: 30).toFloat()
    val minY = 0f

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No data recorded in this time range",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
                Text(
                    text = "Start monitoring to view graph",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    Column(modifier = modifier) {
        // Y-Axis label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (graphData.isDaysBpm) "Days BPM" else "BPM (Blinks/min)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
            Text(
                text = "Target: $threshold BPM",
                style = MaterialTheme.typography.labelSmall,
                color = AccentEmerald,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Chart Canvas with Y-axis numbers on the left
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Y-Axis Numeric Annotations Column
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAnnotations.reversed().forEach { yVal ->
                    Text(
                        text = "$yVal",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            // Canvas Chart Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val width = size.width
                                val stepX = width / (points.size - 1).coerceAtLeast(1)
                                val closestIndex = (offset.x / stepX).toInt().coerceIn(0, points.size - 1)
                                onPointSelected(points[closestIndex])
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw horizontal gridlines for each Y annotation
                    val yCount = yAnnotations.size
                    yAnnotations.forEachIndexed { idx, _ ->
                        val normalized = idx.toFloat() / (yCount - 1).coerceAtLeast(1)
                        val y = height - (normalized * height)
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.35f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Draw Target Threshold reference line
                    val targetNorm = ((threshold - minY) / (maxY - minY)).coerceIn(0f, 1f)
                    val targetY = height - (targetNorm * height)
                    drawLine(
                        color = AccentEmerald.copy(alpha = 0.8f),
                        start = Offset(0f, targetY),
                        end = Offset(width, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )

                    // 3. Construct Data Path and Area Gradient Fill
                    val stepX = width / (points.size - 1).coerceAtLeast(1)
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { index, pt ->
                        val x = if (points.size == 1) width / 2f else index * stepX
                        val normY = ((pt.bpm.toFloat() - minY) / (maxY - minY)).coerceIn(0f, 1f)
                        val y = height - (normY * height)

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            // Smooth bezier curve segment
                            val prevPt = points[index - 1]
                            val prevX = (index - 1) * stepX
                            val prevNormY = ((prevPt.bpm.toFloat() - minY) / (maxY - minY)).coerceIn(0f, 1f)
                            val prevY = height - (prevNormY * height)

                            val controlX1 = prevX + (x - prevX) / 2f
                            val controlY1 = prevY
                            val controlX2 = prevX + (x - prevX) / 2f
                            val controlY2 = y

                            path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                        }
                    }

                    if (points.size > 1) {
                        fillPath.lineTo(width, height)
                        fillPath.close()

                        // Gradient Fill Under Curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(TealPrimary.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )

                        // Main Curve Stroke
                        drawPath(
                            path = path,
                            color = TealPrimary,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4. Draw Point Markers
                    points.forEachIndexed { index, pt ->
                        val x = if (points.size == 1) width / 2f else index * stepX
                        val normY = ((pt.bpm.toFloat() - minY) / (maxY - minY)).coerceIn(0f, 1f)
                        val y = height - (normY * height)
                        val isSelected = selectedPoint?.timestamp == pt.timestamp

                        val dotColor = if (pt.bpm >= threshold) AccentEmerald else AccentRose
                        val dotRadius = if (isSelected) 6.dp.toPx() else 3.5.dp.toPx()

                        // Outer ring for selected point
                        if (isSelected) {
                            drawCircle(
                                color = TealPrimary.copy(alpha = 0.3f),
                                radius = dotRadius + 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }

                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = dotRadius / 2f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X-Axis Numeric / Time Annotations Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            graphData.xNumericAnnotations.forEach { annotation ->
                Text(
                    text = annotation,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // X-Axis Label
        Text(
            text = "Time (${graphData.timeRange.displayName})",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 2.dp)
        )
    }
}

@Composable
fun SessionItemCard(
    session: BlinkSessionEntity,
    threshold: Int
) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(session.startTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Mode: ${session.mode.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val isHealthy = session.avgBpm >= threshold
                Text(
                    text = "Avg: %.1f BPM".format(session.avgBpm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthy) AccentEmerald else AccentRose
                )
                if (session.alertCount > 0) {
                    Text(
                        text = "${session.alertCount} Alerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentRose
                    )
                }
            }
        }
    }
}
