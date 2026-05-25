package com.example.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

object UsageStatsHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.noteOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun getDailyAppUsage(context: Context): Map<String, Long> {
        if (!hasUsageStatsPermission(context)) {
            return emptyMap()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return emptyMap()

        val usageMap = mutableMapOf<String, Long>()
        for (usageStat in stats) {
            val packageName = usageStat.packageName
            val totalTime = usageStat.totalTimeInForeground
            if (totalTime > 0) {
                val current = usageMap[packageName] ?: 0L
                usageMap[packageName] = Math.max(current, totalTime)
            }
        }
        return usageMap
    }
}
