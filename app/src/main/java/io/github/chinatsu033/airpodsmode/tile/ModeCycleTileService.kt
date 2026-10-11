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

class ModeCycleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        // Open panel first — do NOT start FGS here (was crashing without location/BT perms).
        TileActions.openControlPanel(this)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        try {
            val st = (application as? AirPodsModeApp)?.connection?.state?.value
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
        } catch (_: Throwable) {
        }
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
