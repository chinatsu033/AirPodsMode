package io.github.chinatsu033.airpodsmode.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.util.AppPreferences
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastLocationScreen(
    prefs: AppPreferences,
    hasLocationPermission: Boolean,
    hasNotifications: Boolean,
    refreshKey: Int,
    onBack: () -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onClear: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRefresh: () -> Unit,
) {
    @Suppress("UNUSED_PARAMETER")
    val _rk = refreshKey
    val has = prefs.hasLastLocation()
    val lat = prefs.lastLat
    val lng = prefs.lastLng

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.last_location_title_zh_friendly),
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
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.last_location_refresh),
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (has) {
                GlassCard(modifier = Modifier.fillMaxWidth().height(220.dp), corner = 22.dp) {
                    OsmMapPreview(lat = lat, lng = lng, modifier = Modifier.fillMaxSize())
                }

                GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.padding(6.dp))
                            Text(
                                prefs.lastLocationDeviceName ?: "AirPods",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            stringResource(R.string.last_location_coords, lat, lng),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        prefs.lastLocationAddress?.let { addr ->
                            Text(
                                addr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            )
                        }
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(prefs.lastLocationTimeMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassCard(
                                modifier = Modifier.weight(1f).height(44.dp),
                                selected = true,
                                corner = 14.dp,
                                onClick = { onOpenMap(lat, lng) },
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Outlined.Map, null, tint = MaterialTheme.colorScheme.onBackground)
                                    Spacer(Modifier.padding(4.dp))
                                    Text(
                                        stringResource(R.string.last_location_open_map),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            GlassCard(
                                modifier = Modifier.weight(1f).height(44.dp),
                                corner = 14.dp,
                                onClick = onClear,
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onBackground)
                                    Spacer(Modifier.padding(4.dp))
                                    Text(
                                        stringResource(R.string.last_location_clear),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MyLocation, null, tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.padding(6.dp))
                            Text(
                                stringResource(R.string.last_location_empty_title),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            stringResource(R.string.last_location_how_to),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.last_location_requirements),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    RequirementRow(
                        ok = hasNotifications,
                        label = stringResource(R.string.last_location_req_notif),
                        actionLabel = stringResource(R.string.action_grant_notifications),
                        onAction = onRequestNotifications,
                    )
                    RequirementRow(
                        ok = hasLocationPermission,
                        label = stringResource(R.string.last_location_req_loc),
                        actionLabel = stringResource(R.string.last_location_grant),
                        onAction = onRequestLocationPermission,
                    )
                    Text(
                        stringResource(R.string.last_location_req_fgs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RequirementRow(
    ok: Boolean,
    label: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            (if (ok) "✓ " else "○ ") + label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (ok) 0.9f else 0.7f),
        )
        if (!ok) {
            GlassCard(
                modifier = Modifier.height(36.dp),
                selected = true,
                corner = 12.dp,
                onClick = onAction,
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OsmMapPreview(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val html = remember(lat, lng) {
        val delta = 0.008
        val left = lng - delta
        val right = lng + delta
        val bottom = lat - delta
        val top = lat + delta
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
        <style>html,body,iframe{margin:0;padding:0;width:100%;height:100%;border:0;background:#1a1a1a}</style>
        </head><body>
        <iframe
          src="https://www.openstreetmap.org/export/embed.html?bbox=$left%2C$bottom%2C$right%2C$top&amp;layer=mapnik&amp;marker=$lat%2C$lng"
          loading="lazy"></iframe>
        </body></html>
        """.trimIndent()
    }
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(22.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0x00000000)
            }
        },
        update = { it.loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null) },
    )
}
