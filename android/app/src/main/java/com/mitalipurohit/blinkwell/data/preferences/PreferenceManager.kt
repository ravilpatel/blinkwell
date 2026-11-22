package com.mitalipurohit.blinkwell.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blinkwell_preferences")

class PreferenceManager(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_APP_MODE = stringPreferencesKey("app_mode") // "burst" | "monitoring"
        val KEY_MONITORING_MODE = stringPreferencesKey("monitoring_mode") // "background" | "app_only"
        val KEY_SAMPLING_MODE = stringPreferencesKey("sampling_mode") // "duty_cycle" | "continuous"
        val KEY_BPM_THRESHOLD = intPreferencesKey("bpm_threshold") // default: 10
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_RESEARCH_CONSENT = booleanPreferencesKey("research_consent")
        val KEY_COHORT_ARM = stringPreferencesKey("cohort_arm") // "software_engineer" | "student" | "general"
        val KEY_USER_AGE = stringPreferencesKey("user_age")
        val KEY_WEARS_GLASSES = stringPreferencesKey("wears_glasses")
        val KEY_OCCUPATION = stringPreferencesKey("occupation")
        val KEY_OCCUPATION_OTHER = stringPreferencesKey("occupation_other")
        val KEY_IS_COMPUTER_BASED = booleanPreferencesKey("is_computer_based")
        val KEY_ENVIRONMENT = stringPreferencesKey("environment")
        val KEY_WORKING_HOURS = stringPreferencesKey("working_hours")
        val KEY_MEDICATION = stringPreferencesKey("medication")
        val KEY_APP_REASONS = stringPreferencesKey("app_reasons")
        val KEY_FEEDBACK_USAGE_DURATION = stringPreferencesKey("feedback_usage_duration")
        val KEY_FEEDBACK_COMFORT = stringPreferencesKey("feedback_comfort")
        val KEY_FEEDBACK_REMINDERS = stringPreferencesKey("feedback_reminders")
        val KEY_FEEDBACK_PREFERRED_MODE = stringPreferencesKey("feedback_preferred_mode")
        val KEY_FEEDBACK_COMMENTS = stringPreferencesKey("feedback_comments")
        val KEY_FEEDBACK_SUBMITTED_TIME = stringPreferencesKey("feedback_submitted_time")
        val KEY_SELECTED_LANGUAGE = stringPreferencesKey("selected_language") // "system", "en", "hi"
        val KEY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        val KEY_DEVICE_REGISTERED = booleanPreferencesKey("device_registered")
        val KEY_BATTERY_GUARD_ENABLED = booleanPreferencesKey("battery_guard_enabled")
        val KEY_BATTERY_GUARD_THRESHOLD = intPreferencesKey("battery_guard_threshold")
        val KEY_FIRST_5MIN_TEST_COMPLETED = booleanPreferencesKey("first_5min_test_completed")
        val KEY_MONITORING_PROMPT_DISMISSED = booleanPreferencesKey("monitoring_prompt_dismissed")
    }

    val isDeviceRegistered: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_REGISTERED] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val appMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_APP_MODE] ?: "burst"
    }

    val monitoringMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONITORING_MODE] ?: "background"
    }

    val samplingMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAMPLING_MODE] ?: "duty_cycle"
    }

    val bpmThreshold: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_BPM_THRESHOLD] ?: 13
    }

    val alertsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALERTS_ENABLED] ?: true
    }

    val researchConsent: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RESEARCH_CONSENT] ?: false
    }

    val cohortArm: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_COHORT_ARM] ?: "software_engineer"
    }

    val userAge: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_AGE] ?: "26-40"
    }

    val wearsGlasses: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_WEARS_GLASSES] ?: "none"
    }

    val occupation: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OCCUPATION] ?: "software_engineer"
    }

    val occupationOther: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OCCUPATION_OTHER] ?: ""
    }

    val isComputerBased: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_COMPUTER_BASED] ?: true
    }

    val environment: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENVIRONMENT] ?: "ac"
    }

    val workingHours: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_WORKING_HOURS] ?: "6-8"
    }

    val medication: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MEDICATION] ?: "none"
    }

    val appReasons: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_APP_REASONS] ?: "eye_strain"
    }

    val selectedLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LANGUAGE] ?: "en"
    }

    val activeSessionId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_SESSION_ID]
    }

    val batteryGuardEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BATTERY_GUARD_ENABLED] ?: true
    }

    val batteryGuardThreshold: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_BATTERY_GUARD_THRESHOLD] ?: 25
    }

    val hasCompletedFirst5MinTest: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_5MIN_TEST_COMPLETED] ?: false
    }

    val isMonitoringPromptDismissed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONITORING_PROMPT_DISMISSED] ?: false
    }

    suspend fun setFirst5MinTestCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_5MIN_TEST_COMPLETED] = completed
        }
    }

    suspend fun setMonitoringPromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MONITORING_PROMPT_DISMISSED] = dismissed
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAppMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_MODE] = mode
        }
    }

    suspend fun setMonitoringMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MONITORING_MODE] = mode
        }
    }

    suspend fun setSamplingMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAMPLING_MODE] = mode
        }
    }

    suspend fun setBpmThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BPM_THRESHOLD] = threshold
        }
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setResearchConsent(consented: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RESEARCH_CONSENT] = consented
        }
    }

    suspend fun setCohortArm(arm: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_COHORT_ARM] = arm
        }
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
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_AGE] = age
            preferences[KEY_WEARS_GLASSES] = glasses
            preferences[KEY_OCCUPATION] = occupation
            preferences[KEY_OCCUPATION_OTHER] = occupationOther
            preferences[KEY_IS_COMPUTER_BASED] = isComputerBased
            preferences[KEY_ENVIRONMENT] = environment
            preferences[KEY_WORKING_HOURS] = workingHours
            preferences[KEY_MEDICATION] = medication
            preferences[KEY_APP_REASONS] = reasons
        }
    }

    suspend fun saveFeedback(
        usageDuration: String,
        comfortImprovement: String,
        reminderComfort: String,
        preferredMode: String,
        comments: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FEEDBACK_USAGE_DURATION] = usageDuration
            preferences[KEY_FEEDBACK_COMFORT] = comfortImprovement
            preferences[KEY_FEEDBACK_REMINDERS] = reminderComfort
            preferences[KEY_FEEDBACK_PREFERRED_MODE] = preferredMode
            preferences[KEY_FEEDBACK_COMMENTS] = comments
            preferences[KEY_FEEDBACK_SUBMITTED_TIME] = System.currentTimeMillis().toString()
        }
    }

    suspend fun setSelectedLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LANGUAGE] = language
        }
    }

    suspend fun setActiveSessionId(sessionId: String?) {
        context.dataStore.edit { preferences ->
            if (sessionId != null) {
                preferences[KEY_ACTIVE_SESSION_ID] = sessionId
            } else {
                preferences.remove(KEY_ACTIVE_SESSION_ID)
            }
        }
    }

    suspend fun setDeviceRegistered(registered: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_REGISTERED] = registered
        }
    }

    suspend fun setBatteryGuardEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BATTERY_GUARD_ENABLED] = enabled
        }
    }

    suspend fun setBatteryGuardThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BATTERY_GUARD_THRESHOLD] = threshold
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
