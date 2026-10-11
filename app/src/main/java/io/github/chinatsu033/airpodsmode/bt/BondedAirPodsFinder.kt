package io.github.chinatsu033.airpodsmode.bt

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object BondedAirPodsFinder {
    private const val TAG = "AirPodsFinder"

    @SuppressLint("MissingPermission")
    fun find(context: Context): List<BluetoothDevice> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (!ok) {
                Log.i(TAG, "no BLUETOOTH_CONNECT")
                return emptyList()
            }
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: try {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            } catch (_: Throwable) {
                null
            }
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (t: SecurityException) {
            Log.w(TAG, "bondedDevices denied", t)
            emptyList()
        }

        val byName = bonded.filter { device ->
            val name = safeName(device)
            val alias = safeAlias(device)
            AirPodsIds.looksLikeAirPods(name, null, null) ||
                AirPodsIds.looksLikeAirPods(alias, null, null)
        }
        if (byName.isNotEmpty()) {
            return byName.sortedWith(
                compareByDescending<BluetoothDevice> { isConnected(it) }
                    .thenBy { safeName(it) ?: it.address },
            )
        }

        // Fallback: exactly one connected audio headset (OEM may hide AirPods name).
        val connectedAudio = bonded.filter { isConnected(it) && isAudioHeadset(it) }
        return if (connectedAudio.size == 1) connectedAudio else emptyList()
    }

    @SuppressLint("MissingPermission")
    fun preferred(context: Context): BluetoothDevice? {
        val list = find(context)
        return list.firstOrNull { isConnected(it) } ?: list.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun describe(context: Context): String = try {
        find(context).joinToString { d ->
            val n = safeName(d) ?: safeAlias(d) ?: "?"
            val c = if (isConnected(d)) "connected" else "bonded"
            "$n [$c]"
        }.ifBlank { "(none)" }
    } catch (_: Throwable) {
        "(error)"
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun safeAlias(device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            device.alias
        } catch (_: Throwable) {
            null
        }
    }

    private fun isConnected(device: BluetoothDevice): Boolean = try {
        val m = BluetoothDevice::class.java.getMethod("isConnected")
        m.invoke(device) as? Boolean == true
    } catch (_: Throwable) {
        false
    }

    private fun isAudioHeadset(device: BluetoothDevice): Boolean {
        val bc = device.bluetoothClass ?: return false
        if (bc.majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO) return false
        return when (bc.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            -> true
            else -> true // still AV major — accept for single-device fallback
        }
    }
}
