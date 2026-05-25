package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object AppDiscoveryUtils {

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledAppInfo>()
        
        // Exclude our own app from the list to avoid lock recursion
        val selfPackage = context.packageName

        for (resolveInfo in list) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == selfPackage) continue
            
            val appName = resolveInfo.loadLabel(pm).toString()
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            
            if (result.none { it.packageName == packageName }) {
                result.add(InstalledAppInfo(packageName, appName, icon))
            }
        }
        return result.sortedBy { it.appName }
    }
}
