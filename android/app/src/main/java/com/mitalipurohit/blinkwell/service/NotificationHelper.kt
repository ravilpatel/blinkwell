package com.mitalipurohit.blinkwell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mitalipurohit.blinkwell.R
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import com.mitalipurohit.blinkwell.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE_ID = "blinkwell_service_channel"
        const val CHANNEL_ALERTS_ID = "blinkwell_alerts_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val ALERT_TRANSITION_NOTIFICATION_ID = 1003
        const val BURST_COMPLETION_NOTIFICATION_ID = 1004

        const val ACTION_STOP_MONITORING = "com.mitalipurohit.blinkwell.action.STOP_MONITORING"

        // Color coding for sticky notification status
        val COLOR_GREEN = Color.parseColor("#10B981") // Green: Normal blink rate
        val COLOR_RED = Color.parseColor("#EF4444")   // Red: Lower blink rate
        val COLOR_YELLOW = Color.parseColor("#F59E0B") // Yellow: Face not detected / lighting issue
        val COLOR_TEAL = Color.parseColor("#0F766E")   // Teal: Burst Mode Neutral Status
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastNotifiedCategory: BlinkStatusCategory? = null
    private var lastNotifiedBpm: Int = -1
    private var lastNotificationTimeMs: Long = 0L

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for Foreground Sticky Notification (Silent, Low importance to prevent annoying sound)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                context.getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing sticky notification displaying real-time blink status"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            // Channel for Low-Blink Alerts (High importance for visible, timely wellness nudges)
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Visible wellness notifications when blink rate is low"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private data class NotificationContent(
        val color: Int,
        val title: String,
        val body: String,
        val subtext: String
    )

    fun buildStatusNotification(
        category: BlinkStatusCategory = BlinkStatusCategory.NORMAL,
        bpm: Double = 0.0,
        thresholdBpm: Int = 13
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Stop Monitoring Quick Action PendingIntent
        val stopIntent = Intent(context, StopMonitoringReceiver::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val content = when (category) {
            BlinkStatusCategory.NORMAL -> {
                NotificationContent(
                    color = COLOR_GREEN,
                    title = context.getString(R.string.notification_status_normal_title, bpm.toInt()),
                    body = context.getString(R.string.notification_status_normal_body),
                    subtext = context.getString(R.string.home_status_monitoring)
                )
            }
            BlinkStatusCategory.LOW_RATE -> {
                NotificationContent(
                    color = COLOR_RED,
                    title = context.getString(R.string.notification_status_low_title, bpm.toInt()),
                    body = context.getString(R.string.notification_status_low_body, thresholdBpm),
                    subtext = context.getString(R.string.notification_channel_alerts)
                )
            }
            BlinkStatusCategory.FACE_NOT_DETECTED -> {
                NotificationContent(
                    color = COLOR_YELLOW,
                    title = context.getString(R.string.notification_status_not_detected_title),
                    body = context.getString(R.string.notification_status_not_detected_body),
                    subtext = context.getString(R.string.home_no_face_detected)
                )
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setSubText(content.subtext)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(content.color)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Updates the sticky status notification:
     * - Reflects real-time blink rate and status category whenever camera is active.
     * - Immediate update on category change, or throttled to 1s on BPM changes to ensure real-time responsiveness without flooding OS.
     */
    fun updateStatusNotification(
        category: BlinkStatusCategory,
        bpm: Double,
        thresholdBpm: Int = 13,
        isWarmedUp: Boolean = true,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val bpmInt = bpm.toInt()
        val categoryChanged = category != lastNotifiedCategory
        val bpmChanged = bpmInt != lastNotifiedBpm
        val timeElapsed = now - lastNotificationTimeMs >= 1000L // 1s throttle for smooth real-time updates

        val shouldUpdate = force || categoryChanged || (bpmChanged && timeElapsed) || (now - lastNotificationTimeMs >= 3000L)

        if (shouldUpdate) {
            lastNotifiedCategory = category
            lastNotifiedBpm = bpmInt
            lastNotificationTimeMs = now

            val notification = buildStatusNotification(category, bpm, thresholdBpm)
            try {
                NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, notification)
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun cancelStatusNotification() {
        lastNotifiedCategory = null
        lastNotifiedBpm = -1
        lastNotificationTimeMs = 0L
        try {
            notificationManager.cancel(SERVICE_NOTIFICATION_ID)
        } catch (ignored: Exception) {
        }
    }

    /**
     * Sends an active, visible high-priority alert notification when moving from Green to Red.
     */
    fun showGreenToRedAlertNotification(currentBpm: Double, thresholdBpm: Int = 13) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = context.getString(R.string.notification_green_to_red_title)
        val body = context.getString(R.string.notification_green_to_red_body, currentBpm.toInt(), thresholdBpm)

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR_RED)
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        try {
            NotificationManagerCompat.from(context).notify(ALERT_TRANSITION_NOTIFICATION_ID, builder.build())
        } catch (ignored: SecurityException) {
        }
    }

    fun showAlertNotification(body: String = context.getString(R.string.notification_alert_body)) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setContentTitle(context.getString(R.string.notification_alert_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR_RED)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, builder.build())
        } catch (ignored: SecurityException) {
        }
    }

    /**
     * Builds the quiet, neutral ongoing foreground notification during 5-minute Burst Mode.
     */
    fun buildBurstProgressNotification(remainingSeconds: Long): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(context, StopMonitoringReceiver::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val body = context.getString(R.string.notification_burst_status_body, minutes, seconds)

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle(context.getString(R.string.notification_burst_status_title))
            .setContentText(body)
            .setSubText("5-Min Test")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR_TEAL)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Updates ongoing notification during 5-minute Burst Mode.
     */
    fun updateBurstStatusNotification(remainingSeconds: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || (now - lastNotificationTimeMs >= 1000L)) {
            lastNotificationTimeMs = now
            val notification = buildBurstProgressNotification(remainingSeconds)
            try {
                NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, notification)
            } catch (ignored: SecurityException) {
            }
        }
    }

    /**
     * Shows the completion notification when 5-minute Burst Mode finishes.
     */
    fun showBurstCompletionNotification(finalBpm: Double, totalBlinks: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = context.getString(R.string.notification_burst_complete_title, finalBpm)
        val body = context.getString(R.string.notification_burst_complete_body, finalBpm, totalBlinks)

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR_TEAL)
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        try {
            NotificationManagerCompat.from(context).notify(BURST_COMPLETION_NOTIFICATION_ID, builder.build())
        } catch (ignored: SecurityException) {
        }
    }
}
