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

abstract class BaseModeTileService : TileService() {
    protected abstract val targetMode: NoiseMode
    protected abstract val labelRes: Int
    protected abstract val iconRes: Int

    override fun onStartListening() = refresh()

    override fun onClick() {
        AirPodsForegroundService.start(
            this,
            AirPodsForegroundService.ACTION_SET_MODE,
            targetMode,
        )
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.subtitle = getString(labelRes)
            }
            it.updateTile()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val mode = (application as? AirPodsModeApp)?.connection?.state?.value?.mode
        val connected = (application as? AirPodsModeApp)?.connection?.state?.value?.connected == true
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.label = getString(labelRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (connected && mode == targetMode) {
                getString(R.string.tile_active)
            } else {
                getString(R.string.tile_tap_to_set)
            }
        }
        tile.state = when {
            connected && mode == targetMode -> Tile.STATE_ACTIVE
            connected -> Tile.STATE_INACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    protected fun requestSelf(context: Context, clazz: Class<out TileService>) {
        try {
            requestListeningState(context, ComponentName(context, clazz))
        } catch (_: Throwable) {
        }
    }
}

class AncTileService : BaseModeTileService() {
    override val targetMode = NoiseMode.ANC
    override val labelRes = R.string.mode_anc
    override val iconRes = R.drawable.ic_qs_anc
    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, AncTileService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}

class TransparencyTileService : BaseModeTileService() {
    override val targetMode = NoiseMode.TRANSPARENCY
    override val labelRes = R.string.mode_transparency
    override val iconRes = R.drawable.ic_qs_transparency
    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, TransparencyTileService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}

class AdaptiveTileService : BaseModeTileService() {
    override val targetMode = NoiseMode.ADAPTIVE
    override val labelRes = R.string.mode_adaptive
    override val iconRes = R.drawable.ic_qs_adaptive
    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, AdaptiveTileService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}
