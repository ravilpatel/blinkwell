package com.mitalipurohit.blinkwell.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.ui.MainActivity
import com.mitalipurohit.blinkwell.ui.theme.AccentEmerald
import com.mitalipurohit.blinkwell.ui.theme.TealPrimary

class BlinkWellWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val blinkDetector = BlinkWellApp.blinkDetector
        val bpm = blinkDetector.metrics.value.currentBpm
        val isMonitoring = blinkDetector.metrics.value.isSamplingActive

        provideContent {
            GlanceTheme {
                WidgetContent(context, bpm, isMonitoring)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, bpm: Double, isMonitoring: Boolean) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(TealPrimary)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = if (isMonitoring) "%.0f BPM".format(bpm) else "Ready",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
