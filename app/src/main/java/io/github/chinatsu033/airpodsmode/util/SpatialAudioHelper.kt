package io.github.chinatsu033.airpodsmode.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Spatial Audio on AirPods requires Apple's HRTF / head-tracking pipeline.
 * On Samsung we can at best open system 360 Audio / sound settings — never fake success.
 */
object SpatialAudioHelper {
    private const val TAG = "SpatialAudio"

    data class Status(
        val canOpenSystemSettings: Boolean,
        val spatializerAvailable: Boolean,
        val spatializerEnabled: Boolean?,
        val explanationResHint: Explanation,
    )

    enum class Explanation {
        /** Device reports a Spatializer — may work for media, not Apple Spatial Audio. */
        SYSTEM_SPATIALIZER,
        /** No Spatializer; offer Samsung/system sound settings deep link. */
        OPEN_SOUND_SETTINGS,
        /** Nothing useful available. */
        UNAVAILABLE,
    }

    fun probe(context: Context): Status {
        var available = false
        var enabled: Boolean? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val am = context.getSystemService(AudioManager::class.java)
                val spatializer = am?.spatializer
                if (spatializer != null) {
                    available = spatializer.isAvailable
                    enabled = try {
                        spatializer.isEnabled
                    } catch (_: Throwable) {
                        null
                    }
                }
            } catch (t: Throwable) {
                Log.i(TAG, "spatializer probe failed", t)
            }
        }
        val canOpen = resolveSoundSettingsIntent(context) != null
        val explanation = when {
            available -> Explanation.SYSTEM_SPATIALIZER
            canOpen -> Explanation.OPEN_SOUND_SETTINGS
            else -> Explanation.UNAVAILABLE
        }
        return Status(canOpen, available, enabled, explanation)
    }

    fun resolveSoundSettingsIntent(context: Context): Intent? {
        val candidates = listOf(
            // Samsung 360 Audio / immersive sound (best-effort deep links)
            Intent().setClassName(
                "com.android.settings",
                "com.samsung.android.settings.asbase.audio.SecVolumeSettings",
            ),
            Intent("com.samsung.accessibility.intent.action.SPATIAL_AUDIO_SETTING"),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        val pm = context.packageManager
        return candidates.firstOrNull { intent ->
            intent.resolveActivity(pm) != null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Try enabling Android Spatializer if present. Returns true only if API accepted the call. */
    fun tryEnableSystemSpatializer(context: Context, enable: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val spatializer = context.getSystemService(AudioManager::class.java)?.spatializer
                ?: return false
            if (!spatializer.isAvailable) return false
            // setEnabled may require privileged permission on some OEMs — catch quietly.
            val method = spatializer.javaClass.methods.firstOrNull {
                it.name == "setEnabled" && it.parameterTypes.size == 1
            }
            if (method != null) {
                method.invoke(spatializer, enable)
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.i(TAG, "setEnabled failed (expected on many devices)", t)
            false
        }
    }
}
