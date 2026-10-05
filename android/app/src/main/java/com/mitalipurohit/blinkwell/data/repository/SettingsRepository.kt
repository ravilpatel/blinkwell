package com.mitalipurohit.blinkwell.data.repository

import com.mitalipurohit.blinkwell.data.preferences.PreferenceManager
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val preferenceManager: PreferenceManager) {

    val isOnboardingCompleted: Flow<Boolean> = preferenceManager.isOnboardingCompleted
    val appMode: Flow<String> = preferenceManager.appMode
    val monitoringMode: Flow<String> = preferenceManager.monitoringMode
    val samplingMode: Flow<String> = preferenceManager.samplingMode
    val bpmThreshold: Flow<Int> = preferenceManager.bpmThreshold
    val alertsEnabled: Flow<Boolean> = preferenceManager.alertsEnabled
    val researchConsent: Flow<Boolean> = preferenceManager.researchConsent
    val cohortArm: Flow<String> = preferenceManager.cohortArm
    val selectedLanguage: Flow<String> = preferenceManager.selectedLanguage
    val activeSessionId: Flow<String?> = preferenceManager.activeSessionId
    val batteryGuardEnabled: Flow<Boolean> = preferenceManager.batteryGuardEnabled
    val batteryGuardThreshold: Flow<Int> = preferenceManager.batteryGuardThreshold

    suspend fun setOnboardingCompleted(completed: Boolean) {
        preferenceManager.setOnboardingCompleted(completed)
    }

    suspend fun setAppMode(mode: String) {
        preferenceManager.setAppMode(mode)
    }

    suspend fun setMonitoringMode(mode: String) {
        preferenceManager.setMonitoringMode(mode)
    }

    suspend fun setSamplingMode(mode: String) {
        preferenceManager.setSamplingMode(mode)
    }

    suspend fun setBpmThreshold(threshold: Int) {
        preferenceManager.setBpmThreshold(threshold)
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        preferenceManager.setAlertsEnabled(enabled)
    }

    suspend fun setResearchConsent(consented: Boolean) {
        preferenceManager.setResearchConsent(consented)
    }

    suspend fun setCohortArm(arm: String) {
        preferenceManager.setCohortArm(arm)
    }

    suspend fun setSelectedLanguage(language: String) {
        preferenceManager.setSelectedLanguage(language)
    }

    suspend fun setActiveSessionId(sessionId: String?) {
        preferenceManager.setActiveSessionId(sessionId)
    }

    suspend fun setBatteryGuardEnabled(enabled: Boolean) {
        preferenceManager.setBatteryGuardEnabled(enabled)
    }

    suspend fun setBatteryGuardThreshold(threshold: Int) {
        preferenceManager.setBatteryGuardThreshold(threshold)
    }

    suspend fun clearSettings() {
        preferenceManager.clearAll()
    }
}
