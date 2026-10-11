package io.github.chinatsu033.airpodsmode

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import io.github.chinatsu033.airpodsmode.bt.AirPodsConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AirPodsModeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var connection: AirPodsConnection
        private set

    override fun onCreate() {
        super.onCreate()
        // Do NOT call HiddenApiBypass here — defer until Bluetooth connect.
        connection = AirPodsConnection(appScope)
        try {
            createNotificationChannel()
        } catch (t: Throwable) {
            Log.w(TAG, "notification channel", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "airpods_connection"
        private const val TAG = "AirPodsModeApp"
    }
}
