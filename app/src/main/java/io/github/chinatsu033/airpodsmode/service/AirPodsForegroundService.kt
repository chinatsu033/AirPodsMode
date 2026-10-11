package io.github.chinatsu033.airpodsmode.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.MainActivity
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.bt.AirPodsModel
import io.github.chinatsu033.airpodsmode.bt.BondedAirPodsFinder
import io.github.chinatsu033.airpodsmode.tile.ModeCycleTileService
import io.github.chinatsu033.airpodsmode.ui.QsControlPanelActivity
import io.github.chinatsu033.airpodsmode.util.LastLocationCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service for AACP connection.
 * IMPORTANT (Android 14+/One UI 9): never start with LOCATION type unless location
 * permission is already granted — otherwise SecurityException kills the process.
 */
class AirPodsForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var aclReceiverRegistered = false

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
            val device: BluetoothDevice? = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
            } catch (_: Throwable) {
                null
            }
            val tracked = app().connection.state.value.deviceAddress ?: return
            if (!device?.address.equals(tracked, ignoreCase = true)) return
            scope.launch(Dispatchers.IO) {
                try {
                    val name = app().connection.state.value.deviceName
                    LastLocationCapture.capture(this@AirPodsForegroundService, name)
                } catch (t: Throwable) {
                    Log.w(TAG, "location capture failed", t)
                }
                try {
                    app().connection.disconnect()
                } catch (_: Throwable) {
                }
                try {
                    updateNotification(getString(R.string.status_disconnected))
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            registerAclReceiver()
        } catch (t: Throwable) {
            Log.w(TAG, "ACL register failed", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always promote to foreground FIRST — crash if this throws.
        try {
            startAsForegroundSafe()
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed — stopping", t)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            registerAclReceiver()
        } catch (_: Throwable) {
        }

        when (intent?.action) {
            ACTION_CONNECT -> scope.launch { connectSafe() }
            ACTION_DISCONNECT -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        val name = app().connection.state.value.deviceName
                        if (app().connection.state.value.deviceAddress != null) {
                            LastLocationCapture.capture(this@AirPodsForegroundService, name)
                        }
                    } catch (_: Throwable) {
                    }
                    try {
                        app().connection.disconnect()
                    } catch (_: Throwable) {
                    }
                    stopSelf()
                }
            }
            ACTION_SET_MODE -> {
                val wire = intent.getByteExtra(EXTRA_MODE, NoiseMode.ADAPTIVE.wireValue)
                val mode = NoiseMode.fromWire(wire) ?: NoiseMode.ADAPTIVE
                val level = if (intent.hasExtra(EXTRA_ADAPTIVE_LEVEL)) {
                    intent.getIntExtra(EXTRA_ADAPTIVE_LEVEL, AdaptiveNoisePreset.DEFAULT.level)
                } else null
                scope.launch {
                    ensureConnected()
                    try {
                        app().connection.setMode(mode, adaptiveLevel = level)
                    } catch (t: Throwable) {
                        Log.w(TAG, "setMode", t)
                    }
                }
            }
            ACTION_SET_ADAPTIVE_LEVEL -> {
                val level = intent.getIntExtra(EXTRA_ADAPTIVE_LEVEL, AdaptiveNoisePreset.DEFAULT.level)
                scope.launch {
                    ensureConnected()
                    try {
                        app().connection.setAdaptiveLevel(level)
                    } catch (t: Throwable) {
                        Log.w(TAG, "setAdaptiveLevel", t)
                    }
                }
            }
            ACTION_SET_CA -> {
                val enabled = intent.getBooleanExtra(EXTRA_CA_ENABLED, false)
                scope.launch {
                    ensureConnected()
                    try {
                        app().connection.setConversationalAwareness(enabled)
                    } catch (t: Throwable) {
                        Log.w(TAG, "setCA", t)
                    }
                }
            }
            ACTION_CYCLE -> {
                scope.launch {
                    ensureConnected()
                    try {
                        val model = app().connection.state.value.model
                        val next = cycleForModel(app().connection.state.value.mode, model)
                        app().connection.setMode(
                            next,
                            adaptiveLevel = if (next == NoiseMode.ADAPTIVE) {
                                AdaptiveNoisePreset.DEFAULT.level
                            } else null,
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "cycle", t)
                    }
                }
            }
            else -> scope.launch { connectSafe() }
        }
        observeState()
        return START_STICKY
    }

    private fun cycleForModel(current: NoiseMode?, model: AirPodsModel): NoiseMode {
        val modes = buildList {
            if (model.supportsAnc) add(NoiseMode.ANC)
            if (model.supportsTransparency) add(NoiseMode.TRANSPARENCY)
            if (model.supportsAdaptive) add(NoiseMode.ADAPTIVE)
        }
        if (modes.isEmpty()) return NoiseMode.TRANSPARENCY
        val idx = modes.indexOf(current)
        return modes[(idx + 1).mod(modes.size)]
    }

    private fun app() = application as AirPodsModeApp

    private suspend fun ensureConnected() {
        val st = app().connection.state.value
        if (!st.connected && !st.connecting) connectSafe()
    }

    private suspend fun connectSafe() {
        try {
            val device = BondedAirPodsFinder.preferred(this) ?: run {
                updateNotification(getString(R.string.status_no_device))
                return
            }
            app().connection.connect(device)
        } catch (t: Throwable) {
            Log.w(TAG, "connectSafe", t)
            updateNotification(getString(R.string.status_error_short))
        }
    }

    private fun observeState() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            try {
                app().connection.state.collectLatest { st ->
                    try {
                        updateNotification(statusText(st))
                        ModeCycleTileService.requestUpdate(this@AirPodsForegroundService)
                    } catch (t: Throwable) {
                        Log.w(TAG, "observe update", t)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "observe", t)
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
            st.connected -> getString(
                R.string.status_connected_mode,
                st.deviceName ?: st.model.displayName,
                modeLabel,
            )
            st.lastError != null -> getString(R.string.status_error, st.lastError!!)
            else -> getString(R.string.status_disconnected)
        }
    }

    private fun registerAclReceiver() {
        if (aclReceiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        ContextCompat.registerReceiver(
            this,
            aclReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        aclReceiverRegistered = true
    }

    private fun startAsForegroundSafe() {
        val notification = buildNotification(getString(R.string.status_connecting))
        // Only CONNECTED_DEVICE by default. Adding LOCATION without permission crashes on A14+.
        val type = if (Build.VERSION.SDK_INT >= 34) {
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (hasLocationPermission()) {
                t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            t
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            } catch (t: SecurityException) {
                // Fallback: connected-device only
                Log.w(TAG, "FGS type fallback", t)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (t: Throwable) {
            Log.w(TAG, "notify failed", t)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val panelPi = PendingIntent.getActivity(
            this, 10,
            Intent(this, QsControlPanelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val tPi = modePending(NoiseMode.TRANSPARENCY, 11)
        val aPi = modePending(NoiseMode.ADAPTIVE, 12)
        val nPi = modePending(NoiseMode.ANC, 13)
        return NotificationCompat.Builder(this, AirPodsModeApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(panelPi)
            .addAction(0, getString(R.string.mode_transparency), tPi)
            .addAction(0, getString(R.string.mode_adaptive), aPi)
            .addAction(0, getString(R.string.mode_anc), nPi)
            .build()
    }

    private fun modePending(mode: NoiseMode, req: Int): PendingIntent {
        val i = Intent(this, AirPodsForegroundService::class.java)
            .setAction(ACTION_SET_MODE)
            .putExtra(EXTRA_MODE, mode.wireValue)
        if (mode == NoiseMode.ADAPTIVE) {
            i.putExtra(EXTRA_ADAPTIVE_LEVEL, AdaptiveNoisePreset.DEFAULT.level)
        }
        return PendingIntent.getService(
            this, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        if (aclReceiverRegistered) {
            try {
                unregisterReceiver(aclReceiver)
            } catch (_: Throwable) {
            }
            aclReceiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AirPodsFGS"
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
            try {
                val i = Intent(context, AirPodsForegroundService::class.java).setAction(action)
                if (mode != null) i.putExtra(EXTRA_MODE, mode.wireValue)
                if (adaptiveLevel != null) i.putExtra(EXTRA_ADAPTIVE_LEVEL, adaptiveLevel)
                if (caEnabled != null) i.putExtra(EXTRA_CA_ENABLED, caEnabled)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start FGS", t)
            }
        }
    }
}
