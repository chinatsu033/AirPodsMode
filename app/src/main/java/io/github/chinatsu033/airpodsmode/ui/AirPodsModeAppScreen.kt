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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
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
import io.github.chinatsu033.airpodsmode.bt.AirPodsModel
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import io.github.chinatsu033.airpodsmode.util.WallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AirPodsModeAppScreen(
    connection: AirPodsConnection,
    prefs: AppPreferences,
    hasBluetooth: Boolean,
    hasNotifications: Boolean,
    bondedHint: String,
    backgroundEpoch: Int,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSetMode: (NoiseMode) -> Unit,
    onSetAdaptiveLevel: (Int) -> Unit,
    onSetConversationalAwareness: (Boolean) -> Unit,
    onRefreshDevices: () -> Unit,
) {
    val state by connection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf<Bitmap?>(null) }
    var adaptiveExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(backgroundEpoch) {
        wallpaper = withContext(Dispatchers.IO) {
            WallpaperLoader.softBlur(WallpaperLoader.loadForPrefs(context, prefs))
        }
    }

    LaunchedEffect(state.mode) {
        if (state.mode != NoiseMode.ADAPTIVE) adaptiveExpanded = false
    }

    val model = state.model

    Box(Modifier.fillMaxSize()) {
        val bmp = wallpaper
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF2A3D34)))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))

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
                                text = state.model.displayName.takeIf { state.connected || state.deviceName != null }
                                    ?: stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onBackground,
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
                StatusGlass(state, hasBluetooth, hasNotifications)

                if (!hasNotifications) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.banner_notif_title),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                stringResource(R.string.banner_notif_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassCard(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    selected = true,
                                    corner = 14.dp,
                                    onClick = onRequestPermissions,
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_grant_notifications), color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                                GlassCard(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    corner = 14.dp,
                                    onClick = onOpenAppSettings,
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_open_app_settings), color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!hasBluetooth) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        selected = true,
                        corner = 18.dp,
                        onClick = onRequestPermissions,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.action_grant_bluetooth), color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(
                        modifier = Modifier.weight(1f).height(48.dp),
                        selected = true,
                        corner = 18.dp,
                        onClick = onConnect,
                        enabled = hasBluetooth && !state.connecting,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.connected) stringResource(R.string.action_reconnect)
                                else stringResource(R.string.action_connect),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    GlassCard(
                        modifier = Modifier.weight(1f).height(48.dp),
                        corner = 18.dp,
                        onClick = onDisconnect,
                        enabled = state.connected || state.connecting,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.action_disconnect),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = if (state.connected || state.connecting) 1f else 0.45f,
                                ),
                            )
                        }
                    }
                }

                if (bondedHint.isNotBlank()) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp, onClick = onRefreshDevices) {
                        Text(
                            bondedHint,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.section_modes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (model.supportsAnc) {
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
                }
                if (model.supportsTransparency) {
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
                }
                if (model.supportsAdaptive) {
                    AdaptiveModeSection(
                        selected = state.mode == NoiseMode.ADAPTIVE,
                        enabled = state.connected,
                        expanded = adaptiveExpanded && model.supportsAdaptiveStrength,
                        showStrength = model.supportsAdaptiveStrength,
                        adaptiveLevel = state.adaptiveLevel,
                        onPrimaryClick = {
                            if (state.mode != NoiseMode.ADAPTIVE) {
                                onSetMode(NoiseMode.ADAPTIVE)
                                adaptiveExpanded = false
                            } else if (model.supportsAdaptiveStrength) {
                                adaptiveExpanded = !adaptiveExpanded
                            }
                        },
                        onLongPress = {
                            if (state.mode != NoiseMode.ADAPTIVE) onSetMode(NoiseMode.ADAPTIVE)
                            if (model.supportsAdaptiveStrength) adaptiveExpanded = true
                        },
                        onToggleExpand = {
                            if (state.mode != NoiseMode.ADAPTIVE) onSetMode(NoiseMode.ADAPTIVE)
                            if (model.supportsAdaptiveStrength) adaptiveExpanded = !adaptiveExpanded
                        },
                        onPickLevel = onSetAdaptiveLevel,
                    )
                }

                if (model.supportsConversationalAwareness) {
                    ConversationalAwarenessCard(
                        enabled = state.connected,
                        checked = state.conversationalAwareness == true,
                        onCheckedChange = onSetConversationalAwareness,
                    )
                }

                HintGlass()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun StatusGlass(
    state: AirPodsConnection.State,
    hasBluetooth: Boolean,
    hasNotifications: Boolean,
) {
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
                    val detail = buildString {
                        append(
                            when {
                                !hasBluetooth -> stringResource(R.string.status_need_bluetooth)
                                state.connecting -> stringResource(R.string.status_connecting)
                                state.connected -> {
                                    if (hasNotifications) stringResource(R.string.status_connected)
                                    else stringResource(R.string.status_connected_foreground)
                                }
                                state.lastError != null -> stringResource(R.string.status_error_short)
                                else -> stringResource(R.string.status_disconnected)
                            },
                        )
                        if (state.model != AirPodsModel.UNKNOWN || state.modelNumber != null) {
                            append(" · ")
                            append(state.model.displayName)
                            state.modelNumber?.let { append(" ($it)") }
                        }
                    }
                    Text(
                        text = detail,
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
                Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
        modifier = Modifier.fillMaxWidth().height(88.dp),
        selected = selected,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.Headphones, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
            }
            if (selected) {
                Text(stringResource(R.string.tile_active), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
    showStrength: Boolean,
    adaptiveLevel: Int,
    onPrimaryClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleExpand: () -> Unit,
    onPickLevel: (Int) -> Unit,
) {
    val preset = AdaptiveNoisePreset.nearest(adaptiveLevel)
    val shape = RoundedCornerShape(GlassCorner)
    val interaction = remember { MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth().height(88.dp), selected = selected) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = ripple(bounded = true),
                        onClick = onPrimaryClick,
                        onLongClick = onLongPress,
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Headphones, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mode_adaptive), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = when {
                            selected && showStrength -> stringResource(R.string.mode_adaptive_level_hint, adaptiveLevel)
                            else -> stringResource(R.string.mode_adaptive_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )
                }
                if (showStrength) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(R.string.action_expand_adaptive),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && selected && showStrength,
            enter = fadeIn(tween(180)) + expandVertically(
                spring(stiffness = Spring.StiffnessMediumLow),
                clip = true,
            ) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.78f)),
            exit = fadeOut(tween(140)) + shrinkVertically(clip = true) + scaleOut(targetScale = 0.96f),
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdaptiveLevelCard(stringResource(R.string.adaptive_more_transparency), stringResource(R.string.adaptive_more_transparency_desc), preset == AdaptiveNoisePreset.MORE_TRANSPARENCY, enabled, 0) {
                    onPickLevel(0)
                }
                AdaptiveLevelCard(stringResource(R.string.adaptive_default), stringResource(R.string.adaptive_default_desc), preset == AdaptiveNoisePreset.DEFAULT, enabled, 1) {
                    onPickLevel(50)
                }
                AdaptiveLevelCard(stringResource(R.string.adaptive_more_anc), stringResource(R.string.adaptive_more_anc_desc), preset == AdaptiveNoisePreset.MORE_ANC, enabled, 2) {
                    onPickLevel(100)
                }
                Text(
                    stringResource(R.string.adaptive_pro3_note),
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
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow - delayIndex * 80),
        label = "lvl$delayIndex",
    )
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear
                scaleX = 0.96f + 0.04f * appear
                scaleY = 0.96f + 0.04f * appear
                translationY = (1f - appear) * (8f + delayIndex * 6f)
            },
        selected = selected,
        corner = 22.dp,
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Hearing, null, tint = MaterialTheme.colorScheme.onBackground)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ca_title), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.ca_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
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
