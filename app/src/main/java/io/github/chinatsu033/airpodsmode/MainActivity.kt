package io.github.chinatsu033.airpodsmode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService
import io.github.chinatsu033.airpodsmode.ui.AirPodsModeAppScreen
import io.github.chinatsu033.airpodsmode.ui.theme.AirPodsModeTheme
import io.github.chinatsu033.airpodsmode.util.SpatialAudioHelper

class MainActivity : ComponentActivity() {
    private var permissionsReady by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsReady = hasNeededPermissions()
        // Wallpaper permission is optional — never block connect on it.
        requestWallpaperPermissionIfNeeded()
        if (permissionsReady) {
            AirPodsForegroundService.start(this)
        }
    }

    private val wallpaperPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // No-op: UI reloads wallpaper via composition; denial uses gradient fallback.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionsReady = hasNeededPermissions()
        if (permissionsReady) {
            AirPodsForegroundService.start(this)
            requestWallpaperPermissionIfNeeded()
        } else {
            permissionLauncher.launch(neededPermissions())
        }
        val app = application as AirPodsModeApp
        setContent {
            AirPodsModeTheme {
                AirPodsModeAppScreen(
                    connection = app.connection,
                    permissionsReady = permissionsReady,
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
                    onOpenSoundSettings = {
                        val intent = SpatialAudioHelper.resolveSoundSettingsIntent(this)
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.spatial_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onTrySpatializer = { enable ->
                        SpatialAudioHelper.tryEnableSystemSpatializer(this, enable)
                    },
                )
            }
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

    private fun requestWallpaperPermissionIfNeeded() {
        val perm = when {
            Build.VERSION.SDK_INT >= 33 -> Manifest.permission.READ_MEDIA_IMAGES
            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            wallpaperPermissionLauncher.launch(perm)
        }
    }

    private fun hasNeededPermissions(): Boolean =
        neededPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
