package com.mitalipurohit.blinkwell.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mitalipurohit.blinkwell.BlinkWellApp

class StopMonitoringReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_STOP_MONITORING) {
            // Stop background monitoring service if running
            if (BlinkMonitorService.isRunning) {
                BlinkMonitorService.stop(context)
            }

            // Signal in-app monitoring to stop if active
            BlinkWellApp.instance.requestStopMonitoring()

            // Dismiss the sticky status notification
            val notificationHelper = NotificationHelper(context)
            notificationHelper.cancelStatusNotification()
        }
    }
}
