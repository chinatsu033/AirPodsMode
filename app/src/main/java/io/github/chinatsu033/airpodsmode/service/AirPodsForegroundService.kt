package io.github.chinatsu033.airpodsmode.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.MainActivity
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.bt.BondedAirPodsFinder
import io.github.chinatsu033.airpodsmode.tile.ModeCycleTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AirPodsForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        when (intent?.action) {
            ACTION_CONNECT -> scope.launch { connect() }
            ACTION_DISCONNECT -> {
                app().connection.disconnect()
                stopSelf()
            }
            ACTION_SET_MODE -> {
                val wire = intent.getByteExtra(EXTRA_MODE, NoiseMode.ADAPTIVE.wireValue)
                val mode = NoiseMode.fromWire(wire) ?: NoiseMode.ADAPTIVE
                val level = if (intent.hasExtra(EXTRA_ADAPTIVE_LEVEL)) {
                    intent.getIntExtra(EXTRA_ADAPTIVE_LEVEL, AdaptiveNoisePreset.DEFAULT.level)
                } else null
                scope.launch {
                    ensureConnected()
                    app().connection.setMode(mode, adaptiveLevel = level)
                }
            }
            ACTION_SET_ADAPTIVE_LEVEL -> {
                val level = intent.getIntExtra(
                    EXTRA_ADAPTIVE_LEVEL,
                    AdaptiveNoisePreset.DEFAULT.level,
                )
                scope.launch {
                    ensureConnected()
                    app().connection.setAdaptiveLevel(level)
                }
            }
            ACTION_SET_CA -> {
                val enabled = intent.getBooleanExtra(EXTRA_CA_ENABLED, false)
                scope.launch {
                    ensureConnected()
                    app().connection.setConversationalAwareness(enabled)
                }
            }
            ACTION_CYCLE -> {
                scope.launch {
                    ensureConnected()
                    val next = NoiseMode.cycleNext(app().connection.state.value.mode)
                    app().connection.setMode(
                        next,
                        adaptiveLevel = if (next == NoiseMode.ADAPTIVE) {
                            AdaptiveNoisePreset.DEFAULT.level
                        } else null,
                    )
                }
            }
            else -> scope.launch { connect() }
        }
        observeState()
        return START_STICKY
    }

    private fun app() = application as AirPodsModeApp

    private suspend fun ensureConnected() {
        val st = app().connection.state.value
        if (!st.connected && !st.connecting) connect()
    }

    private suspend fun connect() {
        val device = BondedAirPodsFinder.preferred(this) ?: run {
            updateNotification(getString(R.string.status_no_device))
            return
        }
        app().connection.connect(device)
    }

    private fun observeState() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            app().connection.state.collectLatest { st ->
                updateNotification(statusText(st))
                ModeCycleTileService.requestUpdate(this@AirPodsForegroundService)
            }
        }
    }

    private fun statusText(st: io.github.chinatsu033.airpodsmode.bt.AirPodsConnection.State): String {
        val modeLabel = when (st.mode) {
            NoiseMode.ANC -> getString(R.string.mode_anc)
            NoiseMode.TRANSPARENCY -> getString(R.string.mode_transparency)
            NoiseMode.ADAPTIVE -> getString(R.string.mode_adaptive)
            NoiseMode.OFF -> getString(R.string.mode_off)
            null -> getString(R.string.mode_unknown)
        }
        return when {
            st.connecting -> getString(R.string.status_connecting)
            st.connected -> getString(R.string.status_connected_mode, st.deviceName ?: "AirPods", modeLabel)
            st.lastError != null -> getString(R.string.status_error, st.lastError)
            else -> getString(R.string.status_disconnected)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification(getString(R.string.status_connecting))
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, AirPodsModeApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "io.github.chinatsu033.airpodsmode.CONNECT"
        const val ACTION_DISCONNECT = "io.github.chinatsu033.airpodsmode.DISCONNECT"
        const val ACTION_SET_MODE = "io.github.chinatsu033.airpodsmode.SET_MODE"
        const val ACTION_SET_ADAPTIVE_LEVEL = "io.github.chinatsu033.airpodsmode.SET_ADAPTIVE_LEVEL"
        const val ACTION_SET_CA = "io.github.chinatsu033.airpodsmode.SET_CA"
        const val ACTION_CYCLE = "io.github.chinatsu033.airpodsmode.CYCLE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ADAPTIVE_LEVEL = "adaptive_level"
        const val EXTRA_CA_ENABLED = "ca_enabled"
        private const val NOTIFICATION_ID = 42

        fun start(
            context: Context,
            action: String = ACTION_CONNECT,
            mode: NoiseMode? = null,
            adaptiveLevel: Int? = null,
            caEnabled: Boolean? = null,
        ) {
            val i = Intent(context, AirPodsForegroundService::class.java).setAction(action)
            if (mode != null) i.putExtra(EXTRA_MODE, mode.wireValue)
            if (adaptiveLevel != null) i.putExtra(EXTRA_ADAPTIVE_LEVEL, adaptiveLevel)
            if (caEnabled != null) i.putExtra(EXTRA_CA_ENABLED, caEnabled)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
