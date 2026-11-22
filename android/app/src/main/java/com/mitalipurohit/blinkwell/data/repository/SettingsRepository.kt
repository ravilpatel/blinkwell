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
    val userAge: Flow<String> = preferenceManager.userAge
    val wearsGlasses: Flow<String> = preferenceManager.wearsGlasses
    val occupation: Flow<String> = preferenceManager.occupation
    val occupationOther: Flow<String> = preferenceManager.occupationOther
    val isComputerBased: Flow<Boolean> = preferenceManager.isComputerBased
    val environment: Flow<String> = preferenceManager.environment
    val workingHours: Flow<String> = preferenceManager.workingHours
    val medication: Flow<String> = preferenceManager.medication
    val appReasons: Flow<String> = preferenceManager.appReasons
    val selectedLanguage: Flow<String> = preferenceManager.selectedLanguage
    val activeSessionId: Flow<String?> = preferenceManager.activeSessionId
    val batteryGuardEnabled: Flow<Boolean> = preferenceManager.batteryGuardEnabled
    val batteryGuardThreshold: Flow<Int> = preferenceManager.batteryGuardThreshold
    val hasCompletedFirst5MinTest: Flow<Boolean> = preferenceManager.hasCompletedFirst5MinTest
    val isMonitoringPromptDismissed: Flow<Boolean> = preferenceManager.isMonitoringPromptDismissed

    suspend fun setFirst5MinTestCompleted(completed: Boolean) {
        preferenceManager.setFirst5MinTestCompleted(completed)
    }

    suspend fun setMonitoringPromptDismissed(dismissed: Boolean) {
        preferenceManager.setMonitoringPromptDismissed(dismissed)
    }

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

    suspend fun setUserProfile(
        age: String,
        glasses: String,
        occupation: String,
        occupationOther: String,
        isComputerBased: Boolean,
        environment: String,
        workingHours: String,
        medication: String,
        reasons: String
    ) {
        preferenceManager.setUserProfile(
            age = age,
            glasses = glasses,
            occupation = occupation,
            occupationOther = occupationOther,
            isComputerBased = isComputerBased,
            environment = environment,
            workingHours = workingHours,
            medication = medication,
            reasons = reasons
        )
    }

    suspend fun saveFeedback(
        usageDuration: String,
        comfortImprovement: String,
        reminderComfort: String,
        preferredMode: String,
        comments: String
    ) {
        preferenceManager.saveFeedback(
            usageDuration = usageDuration,
            comfortImprovement = comfortImprovement,
            reminderComfort = reminderComfort,
            preferredMode = preferredMode,
            comments = comments
        )
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
