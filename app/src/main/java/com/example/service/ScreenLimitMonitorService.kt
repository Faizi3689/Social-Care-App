package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.data.database.AppLimitEntity
import com.example.data.repository.SocialCareRepository
import com.example.utils.UsageStatsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.Calendar

class ScreenLimitMonitorService : Service() {

    private lateinit var repository: SocialCareRepository
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var blockedPackageName: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private val CHANNEL_ID = "social_care_service_channel"
    private val NOTIFICATION_ID = 901
    private val WARNING_NOTIFICATION_ID = 902

    private val notified80Percent = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        repository = SocialCareRepository(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForegroundServiceNotification()
        
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Social Care Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage limits and focus mode schedules in the background."
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.example.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Digital Wellbeing Active")
            .setContentText("Social Care is protecting your focus and balancing app usage.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAppUsageAndSchedules()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000) // Poll stats every 5 seconds (performance-friendly)
            }
        }
    }

    private suspend fun checkAppUsageAndSchedules() {
        if (!UsageStatsHelper.hasUsageStatsPermission(applicationContext)) {
            return
        }

        val stats = UsageStatsHelper.getDailyAppUsage(applicationContext)
        val limits = repository.getAppLimits().first()
        val schedules = repository.getFocusSchedules().first()

        var shouldBlockAny = false
        var currentBlockedAppLabel = ""
        var currentBlockedPackage = ""

        // 1. Check App Limits
        for (limit in limits) {
            val usedMillis = stats[limit.packageName] ?: 0L
            val usedMinutes = (usedMillis / 60000).toInt()
            
            repository.updateAppUsage(limit.packageName, usedMinutes)

            if (limit.limitMinutes > 0) {
                val warningThreshold = (limit.limitMinutes * 0.8).toInt()
                if (usedMinutes >= warningThreshold && usedMinutes < limit.limitMinutes) {
                    if (!notified80Percent.contains(limit.packageName)) {
                        sendWarningNotification(
                            limit.appLabel,
                            "You are close to reaching your daily limit (${usedMinutes}/${limit.limitMinutes} min used)."
                        )
                        notified80Percent.add(limit.packageName)
                    }
                }

                if (usedMinutes >= limit.limitMinutes) {
                    shouldBlockAny = true
                    currentBlockedAppLabel = limit.appLabel
                    currentBlockedPackage = limit.packageName
                    break
                }
            }
        }

        // 2. Check Focus Schedules
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentDayMinutes = currentHour * 60 + currentMinute

        for (schedule in schedules) {
            if (schedule.isActive) {
                val startDayMinutes = schedule.startHour * 60 + schedule.startMinute
                val endDayMinutes = schedule.endHour * 60 + schedule.endMinute

                val isInsideTime = if (startDayMinutes <= endDayMinutes) {
                    currentDayMinutes in startDayMinutes..endDayMinutes
                } else {
                    currentDayMinutes >= startDayMinutes || currentDayMinutes <= endDayMinutes
                }

                if (isInsideTime) {
                    shouldBlockAny = true
                    currentBlockedAppLabel = "Social Focus Active (${schedule.title})"
                    currentBlockedPackage = "focus_mode_schedule_block"
                    break
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (shouldBlockAny) {
                showBlockOverlay(currentBlockedAppLabel, currentBlockedPackage)
            } else {
                dismissBlockOverlay()
            }
        }
    }

    private fun sendWarningNotification(appName: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName Limit Warning")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
    }

    private fun showBlockOverlay(appLabel: String, packageName: String) {
        if (isOverlayShowing && blockedPackageName == packageName) {
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            sendWarningNotification(appLabel, "Your time limit is finished. Take a break.")
            return
        }

        dismissBlockOverlay()

        val context = applicationContext
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1E1E2C.toInt())
            setPadding(64, 64, 64, 64)
        }

        val titleView = TextView(this).apply {
            text = "Social Care Alert"
            textSize = 28f
            setTextColor(0xFFE94560.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val appLabelView = TextView(this).apply {
            text = appLabel
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val descView = TextView(this).apply {
            text = "Your time limit is finished. Take a break."
            textSize = 16f
            setTextColor(0xFF8F8F9F.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        val btnUnlock = Button(this).apply {
            text = "Unlock with Parental PIN"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE94560.toInt())
            setOnClickListener {
                val launchIntent = Intent(context, Class.forName("com.example.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("TRIGGER_PIN_VERIFY", true)
                    putExtra("VERIFICATION_PACKAGE", packageName)
                }
                context.startActivity(launchIntent)
            }
        }

        root.addView(titleView)
        root.addView(appLabelView)
        root.addView(descView)
        root.addView(btnUnlock)

        try {
            windowManager.addView(root, params)
            overlayView = root
            isOverlayShowing = true
            blockedPackageName = packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissBlockOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            isOverlayShowing = false
            blockedPackageName = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissBlockOverlay()
        serviceScope.cancel()
    }
}
