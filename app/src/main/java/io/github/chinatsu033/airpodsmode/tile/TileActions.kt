package io.github.chinatsu033.airpodsmode.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.MainActivity
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.ui.QsControlPanelActivity

object TileActions {
    fun openControlPanel(tileService: TileService) {
        val connected = try {
            (tileService.application as? AirPodsModeApp)?.connection?.state?.value?.connected == true
        } catch (_: Throwable) {
            false
        }
        if (!connected) {
            try {
                Toast.makeText(
                    tileService.applicationContext,
                    tileService.getString(R.string.tile_not_connected_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Throwable) {
            }
        }
        val panel = Intent(tileService, QsControlPanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchActivity(tileService, panel)
    }

    fun openApp(tileService: TileService) {
        val main = Intent(tileService, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchActivity(tileService, main)
    }

    private fun launchActivity(tileService: TileService, intent: Intent) {
        val launch = {
            try {
                val pi = PendingIntent.getActivity(
                    tileService, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    tileService.startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    tileService.startActivityAndCollapse(intent)
                }
            } catch (t: Throwable) {
                try {
                    tileService.startActivity(intent)
                } catch (_: Throwable) {
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tileService.unlockAndRun { launch() }
            } else {
                launch()
            }
        } catch (_: Throwable) {
            launch()
        }
    }
}
