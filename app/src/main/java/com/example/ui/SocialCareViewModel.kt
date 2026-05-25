package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppLimitEntity
import com.example.data.database.FocusScheduleEntity
import com.example.data.database.SecurityLogEntity
import com.example.data.repository.SocialCareRepository
import com.example.data.repository.UserRole
import com.example.utils.AppDiscoveryUtils
import com.example.utils.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialCareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SocialCareRepository(application.applicationContext)

    // Expose flows from Repository
    val appLimits: StateFlow<List<AppLimitEntity>> = repository.getAppLimits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val focusSchedules: StateFlow<List<FocusScheduleEntity>> = repository.getFocusSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val securityLogs: StateFlow<List<SecurityLogEntity>> = repository.getSecurityLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Native state trackers
    private val _userRoleState = MutableStateFlow(repository.getUserRole())
    val userRoleState: StateFlow<UserRole> = _userRoleState.asStateFlow()

    private val _parentSyncedState = MutableStateFlow(repository.isParentSynced())
    val parentSyncedState: StateFlow<Boolean> = _parentSyncedState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(repository.isOnboardingDone())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    init {
        loadInstalledApps()
        // Seeds initial focus mode default schedules if first run
        viewModelScope.launch {
            repository.getFocusSchedules().collect { list ->
                if (list.isEmpty()) {
                    repository.insertFocusSchedule(
                        FocusScheduleEntity(
                            title = "Study Block Rule",
                            startHour = 14,
                            startMinute = 0,
                            endHour = 17,
                            endMinute = 0,
                            isActive = false
                        )
                    )
                    repository.insertFocusSchedule(
                        FocusScheduleEntity(
                            title = "Sleep Focus Block",
                            startHour = 22,
                            startMinute = 0,
                            endHour = 6,
                            endMinute = 0,
                            isActive = false
                        )
                    )
                }
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val apps = AppDiscoveryUtils.getInstalledApps(getApplication())
            _installedApps.value = apps
        }
    }

    fun selectUserRole(role: UserRole) {
        repository.setUserRole(role)
        _userRoleState.value = role
    }

    fun setChildPinCode(pin: String) {
        repository.setChildPin(pin)
    }

    fun checkParentPin(entered: String): Boolean {
        return entered == repository.getChildPin()
    }

    fun saveAppLimit(packageName: String, label: String, minutes: Int) {
        viewModelScope.launch {
            repository.insertAppLimit(
                AppLimitEntity(
                    packageName = packageName,
                    appLabel = label,
                    limitMinutes = minutes
                )
            )
        }
    }

    fun deleteAppLimit(packageName: String) {
        viewModelScope.launch {
            repository.deleteAppLimit(packageName)
        }
    }

    fun saveFocusSchedule(title: String, startH: Int, startM: Int, endH: Int, endM: Int) {
        viewModelScope.launch {
            repository.insertFocusSchedule(
                FocusScheduleEntity(
                    title = title,
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    isActive = true
                )
            )
        }
    }

    fun toggleFocusSchedule(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            repository.updateScheduleState(id, isActive)
        }
    }

    fun deleteFocusSchedule(id: Int) {
        viewModelScope.launch {
            repository.deleteFocusSchedule(id)
        }
    }

    fun logIntruderAttempt(failedPin: String, imagePath: String? = null) {
        viewModelScope.launch {
            repository.insertSecurityLog(
                SecurityLogEntity(
                    eventType = "INTRUDER_ATTEMPT",
                    description = "Failed authorization attempt with PIN: $failedPin. Cam capture launched.",
                    imagePath = imagePath
                )
            )
        }
    }

    fun logSecurityEvent(eventType: String, message: String, imagePath: String? = null) {
        viewModelScope.launch {
            repository.insertSecurityLog(
                SecurityLogEntity(
                    eventType = eventType,
                    description = message,
                    imagePath = imagePath
                )
            )
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearSecurityLogs()
        }
    }

    fun completeOnboarding() {
        repository.setOnboardingDone(true)
        _onboardingCompleted.value = true
    }

    fun syncDataWithCloud() {
        viewModelScope.launch {
            repository.syncChildUsageToCloud(appLimits.value, securityLogs.value)
            _parentSyncedState.value = true
        }
    }

    // Modern Gemini Wellbeing Suggestion Generator
    // We leverage the model capability to analyze currently saved limit entities and suggest advice.
    fun generateWellbeingSuggestions(limits: List<AppLimitEntity>): List<String> {
        val count = limits.size
        val suggestions = mutableListOf<String>()
        if (count == 0) {
            suggestions.add("Add limits to heavily consumed apps (e.g. video and social media apps) to start tracking.")
            suggestions.add("Consistency is key! Set up a custom Sleep Focus to shield your recovery hours.")
        } else {
            val highUse = limits.maxByOrNull { it.usedMinutesToday }
            if (highUse != null && highUse.usedMinutesToday > 0) {
                suggestions.add("Smart recommendation: Limit screen usage on '${highUse.appLabel}' which totals ${highUse.usedMinutesToday} mins today.")
            } else {
                suggestions.add("Excellent discipline with limits! Your digital habits are solid today.")
            }
            suggestions.add("Consider starting a 15-minute screen-free transition before study block hours.")
        }
        return suggestions
    }
}
