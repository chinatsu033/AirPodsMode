package io.github.chinatsu033.airpodsmode.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object LastLocationCapture {
    private const val TAG = "LastLocation"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun capture(context: Context, deviceName: String?): Boolean {
        if (!hasPermission(context)) {
            Log.i(TAG, "no location permission")
            return false
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        val location = withTimeoutOrNull(8_000L) {
            currentOrLast(lm)
        } ?: return false

        val prefs = AppPreferences(context)
        prefs.lastLat = location.latitude
        prefs.lastLng = location.longitude
        prefs.lastLocationTimeMs = System.currentTimeMillis()
        prefs.lastLocationDeviceName = deviceName
        prefs.lastLocationAddress = reverseGeocode(context, location)
        Log.i(TAG, "saved ${location.latitude},${location.longitude}")
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentOrLast(lm: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val providers = listOfNotNull(
                LocationManager.GPS_PROVIDER.takeIf { lm.isProviderEnabled(it) },
                LocationManager.NETWORK_PROVIDER.takeIf { lm.isProviderEnabled(it) },
            )
            val last = providers.mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000L) {
                cont.resume(last)
                return@suspendCancellableCoroutine
            }
            val provider = providers.firstOrNull()
            if (provider == null) {
                cont.resume(last)
                return@suspendCancellableCoroutine
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, Executors.newSingleThreadExecutor()) { loc ->
                    if (cont.isActive) cont.resume(loc ?: last)
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(
                    provider,
                    { loc -> if (cont.isActive) cont.resume(loc) },
                    null,
                )
            }
        }

    private fun reverseGeocode(context: Context, location: Location): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            list?.firstOrNull()?.getAddressLine(0)
        } catch (t: Throwable) {
            Log.i(TAG, "geocode failed", t)
            null
        }
    }
}
