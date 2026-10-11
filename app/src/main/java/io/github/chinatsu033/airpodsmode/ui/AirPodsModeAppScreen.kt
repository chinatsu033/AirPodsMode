package io.github.chinatsu033.airpodsmode.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.bt.AirPodsConnection
import io.github.chinatsu033.airpodsmode.util.SpatialAudioHelper
import io.github.chinatsu033.airpodsmode.util.WallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AirPodsModeAppScreen(
    connection: AirPodsConnection,
    permissionsReady: Boolean,
    onRequestPermissions: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSetMode: (NoiseMode) -> Unit,
    onSetAdaptiveLevel: (Int) -> Unit,
    onSetConversationalAwareness: (Boolean) -> Unit,
    onOpenSoundSettings: () -> Unit,
    onTrySpatializer: (Boolean) -> Boolean,
) {
    val state by connection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf<Bitmap?>(null) }
    var adaptiveExpanded by remember { mutableStateOf(false) }
    val spatialStatus = remember { SpatialAudioHelper.probe(context) }

    LaunchedEffect(Unit) {
        wallpaper = withContext(Dispatchers.IO) {
            WallpaperLoader.softBlur(WallpaperLoader.load(context))
        }
    }

    // Collapse adaptive panel when leaving Adaptive mode.
    LaunchedEffect(state.mode) {
        if (state.mode != NoiseMode.ADAPTIVE) adaptiveExpanded = false
    }

    Box(Modifier.fillMaxSize()) {
        // Wallpaper / fallback backdrop
        val bmp = wallpaper
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            )
        }
        // Soft scrim for readability
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusGlass(state, permissionsReady)

                if (!permissionsReady) {
                    GlassActionButton(
                        text = stringResource(R.string.action_grant_permissions),
                        onClick = onRequestPermissions,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassActionButton(
                            text = if (state.connected) {
                                stringResource(R.string.action_reconnect)
                            } else {
                                stringResource(R.string.action_connect)
                            },
                            onClick = onConnect,
                            modifier = Modifier.weight(1f),
                            enabled = !state.connecting,
                        )
                        GlassActionButton(
                            text = stringResource(R.string.action_disconnect),
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f),
                            enabled = state.connected || state.connecting,
                            outlined = true,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.section_modes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                ModeGlassButton(
                    title = stringResource(R.string.mode_anc),
                    subtitle = stringResource(R.string.mode_anc_desc),
                    selected = state.mode == NoiseMode.ANC,
                    enabled = state.connected,
                    onClick = {
                        adaptiveExpanded = false
                        onSetMode(NoiseMode.ANC)
                    },
                )
                ModeGlassButton(
                    title = stringResource(R.string.mode_transparency),
                    subtitle = stringResource(R.string.mode_transparency_desc),
                    selected = state.mode == NoiseMode.TRANSPARENCY,
                    enabled = state.connected,
                    onClick = {
                        adaptiveExpanded = false
                        onSetMode(NoiseMode.TRANSPARENCY)
                    },
                )

                AdaptiveModeSection(
                    selected = state.mode == NoiseMode.ADAPTIVE,
                    enabled = state.connected,
                    expanded = adaptiveExpanded,
                    adaptiveLevel = state.adaptiveLevel,
                    onPrimaryClick = {
                        if (state.mode != NoiseMode.ADAPTIVE) {
                            onSetMode(NoiseMode.ADAPTIVE)
                            // First tap: enter Adaptive at mid, keep panel collapsed.
                            adaptiveExpanded = false
                        } else {
                            adaptiveExpanded = !adaptiveExpanded
                        }
                    },
                    onLongPress = {
                        if (state.mode != NoiseMode.ADAPTIVE) {
                            onSetMode(NoiseMode.ADAPTIVE)
                        }
                        adaptiveExpanded = true
                    },
                    onToggleExpand = {
                        if (state.mode != NoiseMode.ADAPTIVE) {
                            onSetMode(NoiseMode.ADAPTIVE)
                        }
                        adaptiveExpanded = !adaptiveExpanded
                    },
                    onPickLevel = { level ->
                        onSetAdaptiveLevel(level)
                    },
                )

                ConversationalAwarenessCard(
                    enabled = state.connected,
                    checked = state.conversationalAwareness == true,
                    onCheckedChange = onSetConversationalAwareness,
                )

                SpatialAudioCard(
                    status = spatialStatus,
                    onOpenSettings = onOpenSoundSettings,
                    onTryToggle = onTrySpatializer,
                )

                HintGlass()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun StatusGlass(state: AirPodsConnection.State, permissionsReady: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth(), selected = state.connected) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (state.connected) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = state.deviceName ?: stringResource(R.string.status_no_device_short),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when {
                            !permissionsReady -> stringResource(R.string.status_need_permission)
                            state.connecting -> stringResource(R.string.status_connecting)
                            state.connected -> stringResource(R.string.status_connected)
                            state.lastError != null -> stringResource(R.string.status_error_short)
                            else -> stringResource(R.string.status_disconnected)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
            state.battery?.let { bat ->
                val parts = buildList {
                    bat.leftPercent?.let { add(stringResource(R.string.battery_left, it)) }
                    bat.rightPercent?.let { add(stringResource(R.string.battery_right, it)) }
                    bat.casePercent?.let { add(stringResource(R.string.battery_case, it)) }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
            state.lastError?.takeIf { !state.connected }?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ModeGlassButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(enabled = enabled, onClick = onClick),
        selected = selected,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                )
            }
            if (selected) {
                Text(
                    text = stringResource(R.string.tile_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AdaptiveModeSection(
    selected: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    adaptiveLevel: Int,
    onPrimaryClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleExpand: () -> Unit,
    onPickLevel: (Int) -> Unit,
) {
    val preset = AdaptiveNoisePreset.nearest(adaptiveLevel)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onPrimaryClick,
                    onLongClick = onLongPress,
                ),
            selected = selected,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mode_adaptive),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (selected) {
                            stringResource(R.string.mode_adaptive_level_hint, adaptiveLevel)
                        } else {
                            stringResource(R.string.mode_adaptive_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.action_expand_adaptive),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(enabled = enabled, onClick = onToggleExpand),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && selected,
            enter = fadeIn(tween(180)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.75f)),
            exit = fadeOut(tween(140)) + shrinkVertically() + scaleOut(targetScale = 0.94f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveLevelCard(
                    title = stringResource(R.string.adaptive_more_transparency),
                    subtitle = stringResource(R.string.adaptive_more_transparency_desc),
                    selected = preset == AdaptiveNoisePreset.MORE_TRANSPARENCY,
                    enabled = enabled,
                    delayIndex = 0,
                    onClick = { onPickLevel(AdaptiveNoisePreset.MORE_TRANSPARENCY.level) },
                )
                AdaptiveLevelCard(
                    title = stringResource(R.string.adaptive_default),
                    subtitle = stringResource(R.string.adaptive_default_desc),
                    selected = preset == AdaptiveNoisePreset.DEFAULT,
                    enabled = enabled,
                    delayIndex = 1,
                    onClick = { onPickLevel(AdaptiveNoisePreset.DEFAULT.level) },
                )
                AdaptiveLevelCard(
                    title = stringResource(R.string.adaptive_more_anc),
                    subtitle = stringResource(R.string.adaptive_more_anc_desc),
                    selected = preset == AdaptiveNoisePreset.MORE_ANC,
                    enabled = enabled,
                    delayIndex = 2,
                    onClick = { onPickLevel(AdaptiveNoisePreset.MORE_ANC.level) },
                )
                Text(
                    text = stringResource(R.string.adaptive_pro3_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AdaptiveLevelCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    delayIndex: Int,
    onClick: () -> Unit,
) {
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow - delayIndex * 80,
        ),
        label = "adaptiveCard$delayIndex",
    )
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear
                scaleX = 0.96f + 0.04f * appear
                scaleY = 0.96f + 0.04f * appear
                translationY = (1f - appear) * (8f + delayIndex * 6f)
            }
            .clickable(enabled = enabled, onClick = onClick),
        selected = selected,
        corner = 22.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ConversationalAwarenessCard(
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Hearing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ca_title),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.ca_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun SpatialAudioCard(
    status: SpatialAudioHelper.Status,
    onOpenSettings: () -> Unit,
    onTryToggle: (Boolean) -> Boolean,
) {
    var tried by remember { mutableStateOf(false) }
    var tryOk by remember { mutableStateOf<Boolean?>(null) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.SpatialAudio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.spatial_title),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when (status.explanationResHint) {
                            SpatialAudioHelper.Explanation.SYSTEM_SPATIALIZER ->
                                stringResource(R.string.spatial_system_available)
                            SpatialAudioHelper.Explanation.OPEN_SOUND_SETTINGS ->
                                stringResource(R.string.spatial_open_settings_hint)
                            SpatialAudioHelper.Explanation.UNAVAILABLE ->
                                stringResource(R.string.spatial_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            Text(
                text = stringResource(R.string.spatial_apple_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.spatializerAvailable) {
                    TextButton(onClick = {
                        tried = true
                        tryOk = onTryToggle(!(status.spatializerEnabled ?: false))
                    }) {
                        Text(stringResource(R.string.spatial_try_toggle))
                    }
                }
                if (status.canOpenSystemSettings) {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.spatial_open_settings))
                    }
                }
            }
            if (tried) {
                Text(
                    text = if (tryOk == true) {
                        stringResource(R.string.spatial_try_ok)
                    } else {
                        stringResource(R.string.spatial_try_failed)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    GlassCard(
        modifier = modifier
            .height(48.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        selected = !outlined && enabled,
        corner = 18.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = if (enabled) 1f else 0.45f,
                ),
            )
        }
    }
}

@Composable
private fun HintGlass() {
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
        Text(
            text = stringResource(R.string.hint_qs_tiles),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}
