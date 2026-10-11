package io.github.chinatsu033.airpodsmode.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimHelper {
    private const val TAG = "BatteryOptim"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun openAppBatterySettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** Best-effort Samsung One UI auto-start / never-sleeping deep links. */
    fun samsungAutostartIntents(context: Context): List<Intent> {
        val pkg = context.packageName
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.sm_cn",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
            ),
            Intent("com.samsung.android.sm.ACTION_BATTERY").setPackage("com.samsung.android.lool"),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity",
                ),
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            openAppBatterySettings(context),
        )
        val pm = context.packageManager
        return candidates.filter {
            try {
                it.resolveActivity(pm) != null
            } catch (t: Throwable) {
                Log.i(TAG, "intent resolve fail", t)
                false
            }
        }.onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also { /* touch pkg for lint */ pkg }
    }

    fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)
}
