package io.github.chinatsu033.airpodsmode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    private var uiCrashFallback by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsReady = hasNeededPermissions()
        // Do not auto-start FGS — user taps Connect.
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* optional */ }

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
        } catch (_: Throwable) {
        }
        try {
            prefs.customBackgroundUri = uri
            prefs.backgroundMode = AppPreferences.BackgroundMode.CUSTOM_IMAGE
            backgroundEpoch++
        } catch (_: Throwable) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = AppPreferences(this)
        } catch (t: Throwable) {
            Log.e(TAG, "prefs init", t)
            // Last resort empty prefs via applicationContext
            prefs = AppPreferences(applicationContext)
        }
        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            Log.w(TAG, "enableEdgeToEdge", t)
        }
        permissionsReady = hasNeededPermissions()
        try {
            batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
        } catch (_: Throwable) {
            batteryIgnored = false
        }
        // Ask permissions but never block UI; never start FGS until Connect.
        if (!permissionsReady) {
            try {
                permissionLauncher.launch(neededPermissions())
            } catch (t: Throwable) {
                Log.w(TAG, "permission launch", t)
            }
        }

        val app = application as AirPodsModeApp
        try {
            setContent {
                if (uiCrashFallback) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF2A3D34)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("AirPods Mode\n(safe mode)", color = Color.White)
                    }
                    return@setContent
                }
                AirPodsModeTheme {
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
                                    startActivity(
                                        BatteryOptimHelper.requestIgnoreBatteryOptimizationsIntent(this),
                                    )
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
                                    Toast.makeText(
                                        this,
                                        getString(R.string.samsung_autostart_guide),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            onRequestLocationPermission = {
                                try {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                } catch (_: Throwable) {
                                }
                            },
                            onClearLocation = { prefs.clearLastLocation() },
                            onOpenMap = { lat, lng ->
                                try {
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
                                                Uri.parse(
                                                    "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng",
                                                ),
                                            ),
                                        )
                                    }
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
                            permissionsReady = permissionsReady,
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
                                try {
                                    permissionLauncher.launch(neededPermissions())
                                } catch (_: Throwable) {
                                }
                            },
                            onConnect = { AirPodsForegroundService.start(this) },
                            onDisconnect = {
                                AirPodsForegroundService.start(
                                    this,
                                    AirPodsForegroundService.ACTION_DISCONNECT,
                                )
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
        } catch (t: Throwable) {
            Log.e(TAG, "setContent failed", t)
            uiCrashFallback = true
            try {
                setContent {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF2A3D34)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("AirPods Mode failed to render UI.\n${t.message}", color = Color.White)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            batteryIgnored = BatteryOptimHelper.isIgnoringBatteryOptimizations(this)
            permissionsReady = hasNeededPermissions()
        } catch (_: Throwable) {
        }
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

    companion object {
        private const val TAG = "MainActivity"
    }
}
