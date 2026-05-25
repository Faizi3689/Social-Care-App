package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialCareDao {

    // App Limit Queries
    @Query("SELECT * FROM app_limits ORDER BY limitMinutes ASC")
    fun getAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppLimit(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppLimit(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)

    @Query("UPDATE app_limits SET usedMinutesToday = :usedMinutes WHERE packageName = :packageName")
    suspend fun updateAppUsage(packageName: String, usedMinutes: Int)

    // Focus Schedules Queries
    @Query("SELECT * FROM focus_schedules ORDER BY startHour ASC, startMinute ASC")
    fun getFocusSchedules(): Flow<List<FocusScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSchedule(schedule: FocusScheduleEntity)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun deleteFocusSchedule(id: Int)

    @Query("UPDATE focus_schedules SET isActive = :isActive WHERE id = :id")
    suspend fun updateScheduleState(id: Int, isActive: Boolean)

    // Security Activity Logs Queries
    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC")
    fun getSecurityLogs(): Flow<List<SecurityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityLog(log: SecurityLogEntity)

    @Query("DELETE FROM security_logs")
    suspend fun clearSecurityLogs()
}
