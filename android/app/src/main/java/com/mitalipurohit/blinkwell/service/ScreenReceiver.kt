package com.mitalipurohit.blinkwell.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class ScreenReceiver(
    private val onUserUnlocked: () -> Unit,
    private val onScreenOff: () -> Unit
) : BroadcastReceiver() {

    private var isRegistered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                onScreenOff()
            }
            Intent.ACTION_USER_PRESENT -> {
                // User unlocked the phone
                onUserUnlocked()
            }
            Intent.ACTION_SCREEN_ON -> {
                // When screen turns on, if the phone is not keyguard locked, treat as unlocked
                context?.let { ctx ->
                    val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (km != null && !km.isKeyguardLocked) {
                        onUserUnlocked()
                    }
                }
            }
        }
    }

    fun register(context: Context) {
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(this, filter)
            isRegistered = true
        }
    }

    fun unregister(context: Context) {
        if (isRegistered) {
            try {
                context.unregisterReceiver(this)
            } catch (ignored: IllegalArgumentException) {
            }
            isRegistered = false
        }
    }
}

