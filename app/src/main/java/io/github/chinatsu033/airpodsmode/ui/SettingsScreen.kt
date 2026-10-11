package io.github.chinatsu033.airpodsmode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import io.github.chinatsu033.airpodsmode.util.BatteryOptimHelper
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    batteryIgnored: Boolean,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onUseSystemWallpaper: () -> Unit,
    onUseSolid: () -> Unit,
    onRequestBatteryOptim: () -> Unit,
    onOpenSamsungAutostart: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onClearLocation: () -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onBackgroundChanged: () -> Unit,
) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    // touch refresh to recompose after actions
    @Suppress("UNUSED_EXPRESSION")
    refresh

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_background))
            GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            text = stringResource(
                                when (prefs.backgroundMode) {
                                    AppPreferences.BackgroundMode.CUSTOM_IMAGE -> R.string.bg_mode_custom
                                    AppPreferences.BackgroundMode.SOLID -> R.string.bg_mode_solid
                                    AppPreferences.BackgroundMode.SYSTEM_WALLPAPER -> R.string.bg_mode_wallpaper
                                },
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        stringResource(R.string.bg_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            onPickImage()
                            refresh++
                            onBackgroundChanged()
                        }) { Text(stringResource(R.string.bg_pick_gallery)) }
                        TextButton(onClick = {
                            onUseSystemWallpaper()
                            refresh++
                            onBackgroundChanged()
                        }) { Text(stringResource(R.string.bg_use_wallpaper)) }
                        TextButton(onClick = {
                            onUseSolid()
                            refresh++
                            onBackgroundChanged()
                        }) { Text(stringResource(R.string.bg_use_solid)) }
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_last_location))
            GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            stringResource(R.string.last_location_title),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        stringResource(R.string.last_location_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    if (prefs.hasLastLocation()) {
                        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(prefs.lastLocationTimeMs))
                        Text(
                            prefs.lastLocationDeviceName ?: "AirPods",
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            prefs.lastLocationAddress
                                ?: "%.5f, %.5f".format(prefs.lastLat, prefs.lastLng),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        )
                        Text(
                            time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                        Row {
                            TextButton(onClick = { onOpenMap(prefs.lastLat, prefs.lastLng) }) {
                                Icon(Icons.Outlined.Map, null)
                                Spacer(Modifier.padding(4.dp))
                                Text(stringResource(R.string.last_location_open_map))
                            }
                            TextButton(onClick = {
                                onClearLocation()
                                refresh++
                            }) { Text(stringResource(R.string.last_location_clear)) }
                        }
                    } else {
                        Text(
                            stringResource(R.string.last_location_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        )
                    }
                    TextButton(onClick = onRequestLocationPermission) {
                        Text(stringResource(R.string.last_location_grant))
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_battery))
            GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatterySaver, null, tint = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            if (batteryIgnored) stringResource(R.string.battery_ignored_yes)
                            else stringResource(R.string.battery_ignored_no),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        stringResource(R.string.battery_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    TextButton(onClick = onRequestBatteryOptim) {
                        Text(stringResource(R.string.battery_request))
                    }
                    if (BatteryOptimHelper.isSamsung()) {
                        Text(
                            stringResource(R.string.samsung_autostart_guide),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        TextButton(onClick = onOpenSamsungAutostart) {
                            Text(stringResource(R.string.samsung_autostart_open))
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_about_findmy))
            GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Wallpaper, null, tint = MaterialTheme.colorScheme.onBackground)
                    Text(
                        stringResource(R.string.findmy_not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}
