package io.github.chinatsu033.airpodsmode

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.bt.BondedAirPodsFinder
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService
import io.github.chinatsu033.airpodsmode.ui.AirPodsModeAppScreen
import io.github.chinatsu033.airpodsmode.ui.SettingsScreen
import io.github.chinatsu033.airpodsmode.ui.theme.AirPodsModeTheme
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import io.github.chinatsu033.airpodsmode.util.BatteryOptimHelper
import io.github.chinatsu033.airpodsmode.util.PermissionHelper
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothDevice

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AppPreferences
    private var hasBluetooth by mutableStateOf(false)
    private var hasNotifications by mutableStateOf(false)
    private var hasLocation by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var backgroundEpoch by mutableIntStateOf(0)
    private var batteryIgnored by mutableStateOf(false)
    private var showNotifRationale by mutableStateOf(false)
    private var showBtRationale by mutableStateOf(false)
    private var bondedHint by mutableStateOf("")
    private var uiCrashFallback by mutableStateOf(false)

    private val connectPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        refreshPermState()
        val notifDenied = PermissionHelper.notificationPermissions().any { result[it] == false }
        val btDenied = PermissionHelper.bluetoothPermissions().any { result[it] == false }
        when {
            hasBluetooth && hasNotifications -> doConnect(preferFgs = true)
            hasBluetooth && !hasNotifications -> {
                // Limited foreground connect
                Toast.makeText(this, getString(R.string.toast_notif_denied_foreground), Toast.LENGTH_LONG).show()
                doConnect(preferFgs = false)
            }
            btDenied -> Toast.makeText(this, getString(R.string.toast_bt_required), Toast.LENGTH_LONG).show()
            notifDenied && !hasBluetooth -> Unit
        }
        refreshBondedHint()
    }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermState()
        if (hasLocation) {
            Toast.makeText(this, getString(R.string.toast_location_granted), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
        try {
            prefs.customBackgroundUri = uri
            prefs.backgroundMode = AppPreferences.BackgroundMode.CUSTOM_IMAGE
            backgroundEpoch++
        } catch (_: Throwable) {
        }
    }

    private val aclReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                -> refreshBondedHint()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = try {
            AppPreferences(this)
        } catch (_: Throwable) {
            AppPreferences(applicationContext)
        }
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
        }
        refreshPermState()
        batteryIgnored = try {
            BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
        } catch (_: Throwable) {
            false
        }
        refreshBondedHint()

        // First launch: show rationale then request BT + notifications.
        if (!hasBluetooth || !hasNotifications) {
            if (!hasNotifications && Build.VERSION.SDK_INT >= 33) {
                showNotifRationale = true
            } else if (!hasBluetooth) {
                showBtRationale = true
            } else {
                requestConnectPermissions()
            }
        }

        registerAcl()

        // If opened from QS with prompt flag
        if (intent?.getBooleanExtra(EXTRA_PROMPT_NOTIF, false) == true && !hasNotifications) {
            showNotifRationale = true
        }

        val app = application as AirPodsModeApp
        try {
            setContent {
                if (uiCrashFallback) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2A3D34)), contentAlignment = Alignment.Center) {
                        Text("AirPods Mode (safe mode)", color = Color.White)
                    }
                    return@setContent
                }
                AirPodsModeTheme {
                    if (showNotifRationale) {
                        AlertDialog(
                            onDismissRequest = {
                                showNotifRationale = false
                                // Still request; denial is OK — foreground connect remains.
                                requestConnectPermissions()
                            },
                            title = { Text(getString(R.string.rationale_notif_title)) },
                            text = { Text(getString(R.string.rationale_notif_body)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showNotifRationale = false
                                    requestConnectPermissions()
                                }) { Text(getString(R.string.rationale_continue)) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showNotifRationale = false
                                    requestConnectPermissions()
                                }) { Text(getString(R.string.rationale_later)) }
                            },
                        )
                    }
                    if (showBtRationale) {
                        AlertDialog(
                            onDismissRequest = {
                                showBtRationale = false
                                requestConnectPermissions()
                            },
                            title = { Text(getString(R.string.rationale_bt_title)) },
                            text = { Text(getString(R.string.rationale_bt_body)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showBtRationale = false
                                    requestConnectPermissions()
                                }) { Text(getString(R.string.rationale_continue)) }
                            },
                        )
                    }

                    if (showSettings) {
                        SettingsScreen(
                            prefs = prefs,
                            batteryIgnored = batteryIgnored,
                            onBack = {
                                showSettings = false
                                batteryIgnored = try {
                                    BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
                                } catch (_: Throwable) {
                                    false
                                }
                            },
                            onPickImage = {
                                try {
                                    pickImageLauncher.launch(arrayOf("image/*"))
                                } catch (t: Throwable) {
                                    Toast.makeText(this, t.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onUseSystemWallpaper = {
                                prefs.backgroundMode = AppPreferences.BackgroundMode.SYSTEM_WALLPAPER
                                backgroundEpoch++
                            },
                            onUseSolid = {
                                prefs.backgroundMode = AppPreferences.BackgroundMode.SOLID
                                backgroundEpoch++
                            },
                            onRequestBatteryOptim = {
                                try {
                                    startActivity(BatteryOptimHelper.requestIgnoreBatteryOptimizationsIntent(this))
                                } catch (_: Throwable) {
                                    try {
                                        startActivity(BatteryOptimHelper.openAppBatterySettings(this))
                                    } catch (_: Throwable) {
                                    }
                                }
                            },
                            onOpenSamsungAutostart = {
                                val first = BatteryOptimHelper.samsungAutostartIntents(this).firstOrNull()
                                if (first != null) {
                                    try {
                                        startActivity(first)
                                    } catch (_: Throwable) {
                                    }
                                } else {
                                    Toast.makeText(this, getString(R.string.samsung_autostart_guide), Toast.LENGTH_LONG).show()
                                }
                            },
                            onRequestLocationPermission = {
                                locationPermLauncher.launch(PermissionHelper.locationPermissions())
                            },
                            onClearLocation = { prefs.clearLastLocation() },
                            onOpenMap = { lat, lng ->
                                try {
                                    val gmm = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(AirPods)"))
                                    if (gmm.resolveActivity(packageManager) != null) startActivity(gmm)
                                    else startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"),
                                        ),
                                    )
                                } catch (t: Throwable) {
                                    Toast.makeText(this, t.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBackgroundChanged = { backgroundEpoch++ },
                        )
                    } else {
                        AirPodsModeAppScreen(
                            connection = app.connection,
                            prefs = prefs,
                            hasBluetooth = hasBluetooth,
                            hasNotifications = hasNotifications,
                            bondedHint = bondedHint,
                            backgroundEpoch = backgroundEpoch,
                            onOpenSettings = {
                                batteryIgnored = try {
                                    BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
                                } catch (_: Throwable) {
                                    false
                                }
                                showSettings = true
                            },
                            onRequestPermissions = {
                                if (!hasNotifications) showNotifRationale = true
                                else requestConnectPermissions()
                            },
                            onOpenAppSettings = { PermissionHelper.openAppSettings(this) },
                            onConnect = { onConnectClicked() },
                            onDisconnect = { onDisconnectClicked() },
                            onSetMode = { mode -> dispatchMode(mode) },
                            onSetAdaptiveLevel = { level ->
                                lifecycleScope.launch {
                                    app.connection.setAdaptiveLevel(level)
                                }
                                if (hasNotifications) {
                                    AirPodsForegroundService.start(
                                        this,
                                        AirPodsForegroundService.ACTION_SET_ADAPTIVE_LEVEL,
                                        adaptiveLevel = level,
                                    )
                                }
                            },
                            onSetConversationalAwareness = { enabled ->
                                lifecycleScope.launch {
                                    app.connection.setConversationalAwareness(enabled)
                                }
                                if (hasNotifications) {
                                    AirPodsForegroundService.start(
                                        this,
                                        AirPodsForegroundService.ACTION_SET_CA,
                                        caEnabled = enabled,
                                    )
                                }
                            },
                            onRefreshDevices = { refreshBondedHint() },
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "setContent", t)
            uiCrashFallback = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PROMPT_NOTIF, false) && !PermissionHelper.hasNotifications(this)) {
            showNotifRationale = true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermState()
        refreshBondedHint()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(aclReceiver)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun registerAcl() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(this, aclReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (t: Throwable) {
            Log.w(TAG, "ACL register", t)
        }
    }

    private fun refreshPermState() {
        hasBluetooth = PermissionHelper.hasBluetoothConnect(this)
        hasNotifications = PermissionHelper.hasNotifications(this)
        hasLocation = PermissionHelper.hasLocation(this)
    }

    private fun refreshBondedHint() {
        bondedHint = if (!hasBluetooth) {
            getString(R.string.devices_need_bt)
        } else {
            try {
                getString(R.string.devices_found, BondedAirPodsFinder.describe(this))
            } catch (_: Throwable) {
                ""
            }
        }
    }

    private fun requestConnectPermissions() {
        val needed = PermissionHelper.connectPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            refreshPermState()
            return
        }
        try {
            connectPermLauncher.launch(needed.toTypedArray())
        } catch (t: Throwable) {
            Log.w(TAG, "perm launch", t)
        }
    }

    private fun onConnectClicked() {
        refreshPermState()
        when {
            !hasBluetooth -> {
                showBtRationale = true
            }
            else -> {
                // Notifications optional: FGS when granted, else in-process while app is open.
                doConnect(preferFgs = hasNotifications)
            }
        }
    }

    private fun doConnect(preferFgs: Boolean) {
        refreshBondedHint()
        val device = BondedAirPodsFinder.preferred(this)
        if (device == null) {
            Toast.makeText(this, getString(R.string.status_no_device), Toast.LENGTH_LONG).show()
            return
        }
        val app = application as AirPodsModeApp
        if (preferFgs && hasNotifications) {
            AirPodsForegroundService.start(this, AirPodsForegroundService.ACTION_CONNECT)
        } else {
            // Foreground-only path — works without notification permission while Activity is open.
            Toast.makeText(this, getString(R.string.toast_foreground_connect), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val ok = app.connection.connect(device)
                if (!ok) {
                    Toast.makeText(
                        this@MainActivity,
                        app.connection.state.value.lastError ?: getString(R.string.status_error_short),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun onDisconnectClicked() {
        val app = application as AirPodsModeApp
        lifecycleScope.launch { app.connection.disconnect() }
        if (hasNotifications) {
            AirPodsForegroundService.start(this, AirPodsForegroundService.ACTION_DISCONNECT)
        }
    }

    private fun dispatchMode(mode: NoiseMode) {
        val app = application as AirPodsModeApp
        lifecycleScope.launch {
            if (!app.connection.state.value.connected) {
                doConnect(preferFgs = hasNotifications)
            }
            app.connection.setMode(
                mode,
                adaptiveLevel = if (mode == NoiseMode.ADAPTIVE) AdaptiveNoisePreset.DEFAULT.level else null,
            )
        }
        if (hasNotifications) {
            AirPodsForegroundService.start(
                this,
                AirPodsForegroundService.ACTION_SET_MODE,
                mode = mode,
                adaptiveLevel = if (mode == NoiseMode.ADAPTIVE) AdaptiveNoisePreset.DEFAULT.level else null,
            )
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_PROMPT_NOTIF = "prompt_notif"
    }
}
