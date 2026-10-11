package io.github.chinatsu033.airpodsmode.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class BackgroundMode { SYSTEM_WALLPAPER, CUSTOM_IMAGE, SOLID }

    var backgroundMode: BackgroundMode
        get() = BackgroundMode.entries.getOrElse(prefs.getInt(KEY_BG_MODE, BackgroundMode.SOLID.ordinal)) { BackgroundMode.SOLID }
        set(value) = prefs.edit().putInt(KEY_BG_MODE, value.ordinal).apply()

    var customBackgroundUri: Uri?
        get() = prefs.getString(KEY_BG_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_BG_URI, value?.toString()).apply()

    var solidColorArgb: Int
        get() = prefs.getInt(KEY_SOLID, 0xFF2A3D34.toInt())
        set(value) = prefs.edit().putInt(KEY_SOLID, value).apply()

    var lastLat: Double
        get() = Double.fromBits(prefs.getLong(KEY_LAT, Double.NaN.toRawBits()))
        set(value) = prefs.edit().putLong(KEY_LAT, value.toRawBits()).apply()

    var lastLng: Double
        get() = Double.fromBits(prefs.getLong(KEY_LNG, Double.NaN.toRawBits()))
        set(value) = prefs.edit().putLong(KEY_LNG, value.toRawBits()).apply()

    var lastLocationTimeMs: Long
        get() = prefs.getLong(KEY_LOC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LOC_TIME, value).apply()

    var lastLocationAddress: String?
        get() = prefs.getString(KEY_LOC_ADDR, null)
        set(value) = prefs.edit().putString(KEY_LOC_ADDR, value).apply()

    var lastLocationDeviceName: String?
        get() = prefs.getString(KEY_LOC_NAME, null)
        set(value) = prefs.edit().putString(KEY_LOC_NAME, value).apply()

    fun hasLastLocation(): Boolean =
        lastLocationTimeMs > 0L && !lastLat.isNaN() && !lastLng.isNaN()

    fun clearLastLocation() {
        prefs.edit()
            .remove(KEY_LAT)
            .remove(KEY_LNG)
            .remove(KEY_LOC_TIME)
            .remove(KEY_LOC_ADDR)
            .remove(KEY_LOC_NAME)
            .apply()
    }

    companion object {
        private const val PREFS = "airpods_mode_prefs"
        private const val KEY_BG_MODE = "bg_mode"
        private const val KEY_BG_URI = "bg_uri"
        private const val KEY_SOLID = "bg_solid"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LNG = "last_lng"
        private const val KEY_LOC_TIME = "last_loc_time"
        private const val KEY_LOC_ADDR = "last_loc_addr"
        private const val KEY_LOC_NAME = "last_loc_name"
    }
}
