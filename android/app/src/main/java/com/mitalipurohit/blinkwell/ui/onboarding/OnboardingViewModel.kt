package com.mitalipurohit.blinkwell.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.data.remote.SupabaseClientProvider
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: Int = 0,
    val monitorConsent: Boolean = true,
    val researchConsent: Boolean = false,
    val cohortArm: String = "software_engineer", // "software_engineer" | "student" | "general"
    val selectedMode: String = "burst", // "burst" (default) or "monitoring"
    val isCompleting: Boolean = false,
    // Questionnaire state
    val age: String = "26-40",
    val wearsGlasses: String = "none", // "none" | "spectacles" | "contacts" | "both"
    val occupation: String = "software_engineer", // "software_engineer" | "student" | "office" | "healthcare" | "field_work" | "other"
    val occupationOther: String = "",
    val isComputerRelated: Boolean = true,
    val workingHours: String = "6-8", // "<4" | "4-6" | "6-8" | "8-10" | ">10"
    val environment: String = "ac", // "ac" | "non_ac" | "mixed"
    val medication: String = "none", // "none" | "lubricant_drops" | "prescription" | "other"
    val reasonsForUsing: Set<String> = setOf("eye_strain") // "eye_strain", "watering", "blurry_vision", "prevention"
)

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.value = _uiState.value.copy(
            currentStep = _uiState.value.currentStep + 1
        )
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.value = _uiState.value.copy(
                currentStep = _uiState.value.currentStep - 1
            )
        }
    }

    fun setMonitorConsent(consented: Boolean) {
        _uiState.value = _uiState.value.copy(monitorConsent = consented)
    }

    fun setResearchConsent(consented: Boolean) {
        _uiState.value = _uiState.value.copy(researchConsent = consented)
    }

    fun setCohortArm(arm: String) {
        _uiState.value = _uiState.value.copy(cohortArm = arm)
    }

    fun setSelectedMode(mode: String) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun setAge(age: String) {
        _uiState.value = _uiState.value.copy(age = age)
    }

    fun setWearsGlasses(glasses: String) {
        _uiState.value = _uiState.value.copy(wearsGlasses = glasses)
    }

    fun setOccupation(occupation: String) {
        val isComp = when (occupation) {
            "software_engineer", "student", "office" -> true
            "field_work" -> false
            else -> _uiState.value.isComputerRelated
        }
        val cohort = when {
            occupation == "student" -> "student"
            isComp -> "software_engineer"
            else -> "general"
        }
        _uiState.value = _uiState.value.copy(
            occupation = occupation,
            isComputerRelated = isComp,
            cohortArm = cohort
        )
    }

    fun setOccupationOther(other: String) {
        _uiState.value = _uiState.value.copy(occupationOther = other)
    }

    fun setIsComputerRelated(isComputer: Boolean) {
        val cohort = when {
            _uiState.value.occupation == "student" -> "student"
            isComputer -> "software_engineer"
            else -> "general"
        }
        _uiState.value = _uiState.value.copy(
            isComputerRelated = isComputer,
            cohortArm = cohort
        )
    }

    fun setWorkingHours(hours: String) {
        _uiState.value = _uiState.value.copy(workingHours = hours)
    }

    fun setEnvironment(env: String) {
        _uiState.value = _uiState.value.copy(environment = env)
    }

    fun setMedication(med: String) {
        _uiState.value = _uiState.value.copy(medication = med)
    }

    fun toggleReason(reason: String) {
        val current = _uiState.value.reasonsForUsing.toMutableSet()
        if (current.contains(reason)) {
            if (current.size > 1) {
                current.remove(reason)
            }
        } else {
            current.add(reason)
        }
        _uiState.value = _uiState.value.copy(reasonsForUsing = current)
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true)

            val mode = _uiState.value.selectedMode
            val research = _uiState.value.researchConsent
            val cohort = _uiState.value.cohortArm

            settingsRepository.setUserProfile(
                age = _uiState.value.age,
                glasses = _uiState.value.wearsGlasses,
                occupation = _uiState.value.occupation,
                occupationOther = _uiState.value.occupationOther,
                isComputerBased = _uiState.value.isComputerRelated,
                environment = _uiState.value.environment,
                workingHours = _uiState.value.workingHours,
                medication = _uiState.value.medication,
                reasons = _uiState.value.reasonsForUsing.joinToString(",")
            )

            settingsRepository.setAppMode(mode)
            settingsRepository.setResearchConsent(research)
            settingsRepository.setCohortArm(cohort)
            settingsRepository.setOnboardingCompleted(true)

            // Silently authenticate anonymously if research consent is enabled
            if (research) {
                try {
                    SupabaseClientProvider.ensureAnonymousAuth()
                } catch (ignored: Exception) {
                }
                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(com.mitalipurohit.blinkwell.BlinkWellApp.instance)
            }

            _uiState.value = _uiState.value.copy(isCompleting = false)
            onSuccess()
        }
    }
}
