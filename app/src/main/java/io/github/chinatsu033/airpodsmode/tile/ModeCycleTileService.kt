package io.github.chinatsu033.airpodsmode.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService
import io.github.chinatsu033.airpodsmode.ui.QsControlPanelActivity

/**
 * Compact QS tile (Android cannot freely resize 3P tiles like stock Dual SIM).
 * Tap opens the full-width SIM-bar liquid-glass control panel.
 */
class ModeCycleTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        // Ensure service is alive, then open full SIM-style panel.
        AirPodsForegroundService.start(this)
        val panel = Intent(this, QsControlPanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, panel,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(panel)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val app = application as? AirPodsModeApp
        val st = app?.connection?.state?.value
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_cycle)
        tile.label = getString(R.string.tile_cycle_label)
        val modeText = when (st?.mode) {
            NoiseMode.ANC -> getString(R.string.mode_anc)
            NoiseMode.TRANSPARENCY -> getString(R.string.mode_transparency)
            NoiseMode.ADAPTIVE -> getString(R.string.mode_adaptive)
            NoiseMode.OFF -> getString(R.string.mode_off)
            null -> if (st?.connected == true) getString(R.string.qs_bar_connected)
            else getString(R.string.mode_unknown)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = modeText
        }
        tile.state = if (st?.connected == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = getString(R.string.tile_cycle_cd, modeText)
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, ModeCycleTileService::class.java))
            } catch (_: Throwable) {
            }
            AncTileService.requestUpdate(context)
            TransparencyTileService.requestUpdate(context)
            AdaptiveTileService.requestUpdate(context)
        }
    }
}
