package io.github.chinatsu033.airpodsmode.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.chinatsu033.airpodsmode.AirPodsModeApp
import io.github.chinatsu033.airpodsmode.MainActivity
import io.github.chinatsu033.airpodsmode.R
import io.github.chinatsu033.airpodsmode.ui.QsControlPanelActivity
import io.github.chinatsu033.airpodsmode.util.PermissionHelper

object TileActions {
    fun openControlPanel(tileService: TileService) {
        val ctx = tileService.applicationContext
        val connected = try {
            (tileService.application as? AirPodsModeApp)?.connection?.state?.value?.connected == true
        } catch (_: Throwable) {
            false
        }
        val hasNotif = PermissionHelper.hasNotifications(ctx)
        val hasBt = PermissionHelper.hasBluetoothConnect(ctx)

        when {
            !hasBt -> {
                toast(tileService, R.string.toast_bt_required)
                openMainWithPrompt(tileService)
            }
            !hasNotif -> {
                toast(tileService, R.string.toast_notif_for_qs)
                // Still open panel for foreground mode switch; panel shows permission banner.
                val panel = Intent(tileService, QsControlPanelActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchActivity(tileService, panel)
            }
            !connected -> {
                toast(tileService, R.string.tile_not_connected_toast)
                val panel = Intent(tileService, QsControlPanelActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchActivity(tileService, panel)
            }
            else -> {
                val panel = Intent(tileService, QsControlPanelActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchActivity(tileService, panel)
            }
        }
    }

    fun openApp(tileService: TileService) {
        openMainWithPrompt(tileService, prompt = !PermissionHelper.hasNotifications(tileService))
    }

    private fun openMainWithPrompt(tileService: TileService, prompt: Boolean = true) {
        val main = Intent(tileService, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PROMPT_NOTIF, prompt)
        launchActivity(tileService, main)
    }

    private fun toast(tileService: TileService, res: Int) {
        try {
            Toast.makeText(tileService.applicationContext, tileService.getString(res), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
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
            } catch (_: Throwable) {
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
