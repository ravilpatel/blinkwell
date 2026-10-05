package com.mitalipurohit.blinkwell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryGuardLogicTest {

    // Pure logic function mirroring BatteryOptimizationHelper.isBatteryGuardTriggered
    private fun evaluateBatteryGuard(
        guardEnabled: Boolean,
        guardThreshold: Int,
        batteryPct: Int,
        isCharging: Boolean,
        isPowerSaveMode: Boolean
    ): Boolean {
        if (!guardEnabled) return false
        if (isPowerSaveMode) return true
        if (isCharging) return false
        return batteryPct < guardThreshold
    }

    @Test
    fun testDefaultBatteryGuard_LowBatteryTriggersGuard() {
        // Default: guardEnabled = true, threshold = 25%
        // Battery at 20%, not charging, no power save mode
        val triggered = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 25,
            batteryPct = 20,
            isCharging = false,
            isPowerSaveMode = false
        )
        assertTrue("Battery at 20% (< 25%) should trigger Battery Guard", triggered)
    }

    @Test
    fun testDefaultBatteryGuard_SufficientBatteryDoesNotTrigger() {
        // Battery at 25% or 50%, not charging, no power save mode
        val triggered25 = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 25,
            batteryPct = 25,
            isCharging = false,
            isPowerSaveMode = false
        )
        assertFalse("Battery at 25% (equal to threshold) should NOT trigger Battery Guard", triggered25)

        val triggered50 = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 25,
            batteryPct = 50,
            isCharging = false,
            isPowerSaveMode = false
        )
        assertFalse("Battery at 50% (> 25%) should NOT trigger Battery Guard", triggered50)
    }

    @Test
    fun testBatterySaverMode_AlwaysTriggersGuard() {
        // Power save mode is ON, even if battery is high (e.g. 75%)
        val triggered = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 25,
            batteryPct = 75,
            isCharging = false,
            isPowerSaveMode = true
        )
        assertTrue("Battery Saver Mode being ON must trigger Battery Guard", triggered)
    }

    @Test
    fun testDeviceCharging_OverridesLowBatteryTrigger() {
        // Battery is 15% (< 25%), but phone is plugged into charger
        val triggered = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 25,
            batteryPct = 15,
            isCharging = true,
            isPowerSaveMode = false
        )
        assertFalse("Charging device should NOT be blocked by low battery guard", triggered)
    }

    @Test
    fun testDisabledBatteryGuard_NeverTriggers() {
        // User turned toggle OFF
        val triggered = evaluateBatteryGuard(
            guardEnabled = false,
            guardThreshold = 25,
            batteryPct = 5,
            isCharging = false,
            isPowerSaveMode = true
        )
        assertFalse("When Battery Guard is disabled by user, it must never block monitoring", triggered)
    }

    @Test
    fun testCustomConfigurableThreshold() {
        // User set custom threshold to 15%
        val triggeredBelow15 = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 15,
            batteryPct = 12,
            isCharging = false,
            isPowerSaveMode = false
        )
        assertTrue("Battery at 12% (< 15%) should trigger custom 15% guard", triggeredBelow15)

        val triggeredAbove15 = evaluateBatteryGuard(
            guardEnabled = true,
            guardThreshold = 15,
            batteryPct = 18,
            isCharging = false,
            isPowerSaveMode = false
        )
        assertFalse("Battery at 18% (>= 15%) should NOT trigger custom 15% guard", triggeredAbove15)
    }
}
