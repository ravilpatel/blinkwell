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
        val KEY_MONITORING_MODE = stringPreferencesKey("monitoring_mode") // "background" | "app_only"
        val KEY_SAMPLING_MODE = stringPreferencesKey("sampling_mode") // "duty_cycle" | "continuous"
        val KEY_BPM_THRESHOLD = intPreferencesKey("bpm_threshold") // default: 10
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_RESEARCH_CONSENT = booleanPreferencesKey("research_consent")
        val KEY_COHORT_ARM = stringPreferencesKey("cohort_arm") // "software_engineer" | "student" | "general"
        val KEY_SELECTED_LANGUAGE = stringPreferencesKey("selected_language") // "system", "en", "hi"
        val KEY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val monitoringMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONITORING_MODE] ?: "app_only"
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

    val selectedLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LANGUAGE] ?: "system"
    }

    val activeSessionId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_SESSION_ID]
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
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

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
