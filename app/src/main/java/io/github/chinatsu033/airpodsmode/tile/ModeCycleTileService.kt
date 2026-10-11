package io.github.chinatsu033.airpodsmode.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import io.github.chinatsu033.airpodsmode.service.AirPodsForegroundService

/**
 * Single Quick Settings tile that cycles ANC → Transparency → Adaptive.
 * Subtitle / label shows the current mode when known.
 */
class ModeCycleTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        AirPodsForegroundService.start(this, AirPodsForegroundService.ACTION_CYCLE)
        // Optimistic label flip for snappier QS feedback.
        val app = application as? AirPodsModeApp
        val next = NoiseMode.cycleNext(app?.connection?.state?.value?.mode)
        qsTile?.let { tile ->
            applyModeLabel(tile, next, connected = true)
            tile.updateTile()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val app = application as? AirPodsModeApp
        val st = app?.connection?.state?.value
        applyModeLabel(tile, st?.mode, connected = st?.connected == true)
        tile.updateTile()
    }

    private fun applyModeLabel(tile: Tile, mode: NoiseMode?, connected: Boolean) {
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_cycle)
        tile.label = getString(R.string.tile_cycle_label)
        val modeText = when (mode) {
            NoiseMode.ANC -> getString(R.string.mode_anc)
            NoiseMode.TRANSPARENCY -> getString(R.string.mode_transparency)
            NoiseMode.ADAPTIVE -> getString(R.string.mode_adaptive)
            NoiseMode.OFF -> getString(R.string.mode_off)
            null -> getString(R.string.mode_unknown)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = modeText
        }
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = getString(R.string.tile_cycle_cd, modeText)
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, ModeCycleTileService::class.java),
                )
            } catch (_: Throwable) {
            }
            // Also nudge the three dedicated tiles.
            AncTileService.requestUpdate(context)
            TransparencyTileService.requestUpdate(context)
            AdaptiveTileService.requestUpdate(context)
        }
    }
}
