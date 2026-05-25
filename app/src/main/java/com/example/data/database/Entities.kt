package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val limitMinutes: Int,
    val usedMinutesToday: Int = 0,
    val isLocked: Boolean = false
)

@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String, // "Study time", "Sleep time"
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isActive: Boolean = true
)

@Entity(tableName = "security_logs")
data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "INTRUDER_ATTEMPT", "AUTHENTICATION", "VERIFICATION_SUCCESS", "LIMIT_EXCEEDED"
    val description: String,
    val imagePath: String? = null // local photo file path if a selfie is taken
)
