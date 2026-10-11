package io.github.chinatsu033.airpodsmode.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.bt.AirPodsConnection
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService
import io.github.chinatsu033.airpodsmode.ui.theme.AirPodsModeTheme
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import io.github.chinatsu033.airpodsmode.util.WallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Full-width SIM-selector-style control bar launched from QS tiles / notification.
 * Third-party Android QS tiles cannot freely resize like stock Dual SIM;
 * this panel is the honest full-size equivalent.
 */
class QsControlPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AirPodsModeApp
        val prefs = AppPreferences(this)
        val hasNotif = io.github.chinatsu033.airpodsmode.util.PermissionHelper.hasNotifications(this)
        val hasBt = io.github.chinatsu033.airpodsmode.util.PermissionHelper.hasBluetoothConnect(this)
        // Only start FGS when notifications are granted — otherwise it fails on Android 13+.
        if (hasNotif && hasBt) {
            try { AirPodsForegroundService.start(this) } catch (_: Throwable) {}
        }
        setContent {
            AirPodsModeTheme {
                QsSimBarPanel(
                    stateFlow = app.connection.state,
                    prefs = prefs,
                    needsNotification = !hasNotif,
                    onDismiss = { finish() },
                    onSelectMode = { mode, level ->
                        lifecycleScope.launch {
                            if (!app.connection.state.value.connected) {
                                connectBestEffort(app, hasNotif)
                            }
                            if (level != null) {
                                app.connection.setAdaptiveLevel(level)
                            } else {
                                app.connection.setMode(
                                    mode,
                                    adaptiveLevel = if (mode == NoiseMode.ADAPTIVE) {
                                        AdaptiveNoisePreset.DEFAULT.level
                                    } else null,
                                )
                            }
                        }
                        if (hasNotif) {
                            if (level != null) {
                                AirPodsForegroundService.start(
                                    this,
                                    AirPodsForegroundService.ACTION_SET_ADAPTIVE_LEVEL,
                                    adaptiveLevel = level,
                                )
                            } else {
                                AirPodsForegroundService.start(
                                    this,
                                    AirPodsForegroundService.ACTION_SET_MODE,
                                    mode = mode,
                                    adaptiveLevel = if (mode == NoiseMode.ADAPTIVE) {
                                        AdaptiveNoisePreset.DEFAULT.level
                                    } else null,
                                )
                            }
                        }
                    },
                    onConnect = {
                        lifecycleScope.launch { connectBestEffort(app, hasNotif) }
                    },
                    onOpenMainForPermission = {
                        startActivity(
                            android.content.Intent(this, io.github.chinatsu033.airpodsmode.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(io.github.chinatsu033.airpodsmode.MainActivity.EXTRA_PROMPT_NOTIF, true),
                        )
                        finish()
                    },
                )
            }
        }
    }

    private suspend fun connectBestEffort(app: AirPodsModeApp, hasNotif: Boolean) {
        if (hasNotif) {
            try { AirPodsForegroundService.start(this) } catch (_: Throwable) {}
            return
        }
        val device = io.github.chinatsu033.airpodsmode.bt.BondedAirPodsFinder.preferred(this) ?: return
        app.connection.connect(device)
    }
}

@Composable
private fun QsSimBarPanel(
    stateFlow: StateFlow<AirPodsConnection.State>,
    prefs: AppPreferences,
    needsNotification: Boolean,
    onDismiss: () -> Unit,
    onSelectMode: (NoiseMode, Int?) -> Unit,
    onOpenMainForPermission: () -> Unit,
    onConnect: () -> Unit,
) {
    val connectionState by stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf<Bitmap?>(null) }
    var adaptiveChooser by remember { mutableStateOf(false) }
    val model = connectionState.model
    val dark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        wallpaper = withContext(Dispatchers.IO) {
            WallpaperLoader.softBlur(WallpaperLoader.loadForPrefs(context, prefs))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        wallpaper?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SimGlassBar {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (connectionState.connected) {
                                stringResource(R.string.qs_bar_connected)
                            } else {
                                stringResource(R.string.qs_bar_disconnected)
                            },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = connectionState.deviceName ?: model.displayName,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!connectionState.connected) {
                        Text(
                            text = stringResource(R.string.action_connect),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onConnect() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            if (needsNotification) {
                SimGlassBar {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMainForPermission() }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.banner_notif_title),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = stringResource(R.string.qs_notif_prompt),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            val segments = buildList {
                if (model.supportsTransparency) add(NoiseMode.TRANSPARENCY)
                if (model.supportsAdaptive) add(NoiseMode.ADAPTIVE)
                if (model.supportsAnc) add(NoiseMode.ANC)
            }
            if (segments.isNotEmpty()) {
                SimGlassBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        segments.forEach { mode ->
                            val selected = connectionState.mode == mode
                            val label = when (mode) {
                                NoiseMode.TRANSPARENCY -> stringResource(R.string.mode_transparency)
                                NoiseMode.ADAPTIVE -> stringResource(R.string.mode_adaptive)
                                NoiseMode.ANC -> stringResource(R.string.mode_anc)
                                else -> mode.name
                            }
                            val shape = RoundedCornerShape(18.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(
                                        if (selected) Color.White.copy(alpha = if (dark) 0.28f else 0.40f)
                                        else Color.Transparent,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(
                                            bounded = true,
                                            color = Color.White.copy(alpha = 0.35f),
                                        ),
                                    ) {
                                        if (mode == NoiseMode.ADAPTIVE && model.supportsAdaptiveStrength) {
                                            if (connectionState.mode == NoiseMode.ADAPTIVE) {
                                                adaptiveChooser = !adaptiveChooser
                                            } else {
                                                onSelectMode(NoiseMode.ADAPTIVE, AdaptiveNoisePreset.DEFAULT.level)
                                                adaptiveChooser = true
                                            }
                                        } else {
                                            adaptiveChooser = false
                                            onSelectMode(mode, null)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = adaptiveChooser && model.supportsAdaptiveStrength,
                enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.94f),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val preset = AdaptiveNoisePreset.nearest(connectionState.adaptiveLevel)
                SimGlassBar {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.qs_adaptive_chooser_title),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        AdaptiveChoiceRow(
                            title = stringResource(R.string.adaptive_more_transparency),
                            selected = preset == AdaptiveNoisePreset.MORE_TRANSPARENCY,
                            onClick = { onSelectMode(NoiseMode.ADAPTIVE, 0) },
                        )
                        AdaptiveChoiceRow(
                            title = stringResource(R.string.adaptive_default),
                            selected = preset == AdaptiveNoisePreset.DEFAULT,
                            onClick = { onSelectMode(NoiseMode.ADAPTIVE, 50) },
                        )
                        AdaptiveChoiceRow(
                            title = stringResource(R.string.adaptive_more_anc),
                            selected = preset == AdaptiveNoisePreset.MORE_ANC,
                            onClick = { onSelectMode(NoiseMode.ADAPTIVE, 100) },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.qs_resize_note),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AdaptiveChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = Color.White, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SimGlassBar(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.22f else 0.34f),
                        Color.White.copy(alpha = if (dark) 0.12f else 0.20f),
                    ),
                ),
                shape,
            )
            .border(
                width = 1.1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.18f)),
                ),
                shape = shape,
            ),
    ) {
        content()
    }
}
