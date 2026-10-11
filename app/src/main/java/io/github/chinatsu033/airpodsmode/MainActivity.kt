package io.github.chinatsu033.airpodsmode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService
import io.github.chinatsu033.airpodsmode.ui.AirPodsModeAppScreen
import io.github.chinatsu033.airpodsmode.ui.SettingsScreen
import io.github.chinatsu033.airpodsmode.ui.theme.AirPodsModeTheme
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import io.github.chinatsu033.airpodsmode.util.BatteryOptimHelper

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AppPreferences
    private var permissionsReady by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var backgroundEpoch by mutableIntStateOf(0)
    private var batteryIgnored by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsReady = hasNeededPermissions()
        if (permissionsReady) AirPodsForegroundService.start(this)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Location used on disconnect by FGS; no immediate action.
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable; still try for this session.
        }
        prefs.customBackgroundUri = uri
        prefs.backgroundMode = AppPreferences.BackgroundMode.CUSTOM_IMAGE
        backgroundEpoch++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        enableEdgeToEdge()
        permissionsReady = hasNeededPermissions()
        batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
        if (permissionsReady) AirPodsForegroundService.start(this)
        else permissionLauncher.launch(neededPermissions())

        val app = application as AirPodsModeApp
        setContent {
            AirPodsModeTheme {
                if (showSettings) {
                    SettingsScreen(
                        prefs = prefs,
                        batteryIgnored = batteryIgnored,
                        onBack = {
                            showSettings = false
                            batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
                        },
                        onPickImage = {
                            pickImageLauncher.launch(arrayOf("image/*"))
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
                                startActivity(BatteryOptimHelper.openAppBatterySettings(this))
                            }
                        },
                        onOpenSamsungAutostart = {
                            val intents = BatteryOptimHelper.samsungAutostartIntents(this)
                            val first = intents.firstOrNull()
                            if (first != null) startActivity(first)
                            else Toast.makeText(this, getString(R.string.samsung_autostart_guide), Toast.LENGTH_LONG).show()
                        },
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        onClearLocation = { prefs.clearLastLocation() },
                        onOpenMap = { lat, lng ->
                            val gmm = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:$lat,$lng?q=$lat,$lng(AirPods)"),
                            )
                            if (gmm.resolveActivity(packageManager) != null) {
                                startActivity(gmm)
                            } else {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"),
                                    ),
                                )
                            }
                        },
                        onBackgroundChanged = { backgroundEpoch++ },
                    )
                } else {
                    AirPodsModeAppScreen(
                        connection = app.connection,
                        prefs = prefs,
                        permissionsReady = permissionsReady,
                        backgroundEpoch = backgroundEpoch,
                        onOpenSettings = {
                            batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
                            showSettings = true
                        },
                        onRequestPermissions = { permissionLauncher.launch(neededPermissions()) },
                        onConnect = { AirPodsForegroundService.start(this) },
                        onDisconnect = {
                            AirPodsForegroundService.start(this, AirPodsForegroundService.ACTION_DISCONNECT)
                        },
                        onSetMode = { mode ->
                            AirPodsForegroundService.start(
                                this,
                                AirPodsForegroundService.ACTION_SET_MODE,
                                mode = mode,
                                adaptiveLevel = if (mode == NoiseMode.ADAPTIVE) {
                                    AdaptiveNoisePreset.DEFAULT.level
                                } else null,
                            )
                        },
                        onSetAdaptiveLevel = { level ->
                            AirPodsForegroundService.start(
                                this,
                                AirPodsForegroundService.ACTION_SET_ADAPTIVE_LEVEL,
                                adaptiveLevel = level,
                            )
                        },
                        onSetConversationalAwareness = { enabled ->
                            AirPodsForegroundService.start(
                                this,
                                AirPodsForegroundService.ACTION_SET_CA,
                                caEnabled = enabled,
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    private fun hasNeededPermissions(): Boolean =
        neededPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
