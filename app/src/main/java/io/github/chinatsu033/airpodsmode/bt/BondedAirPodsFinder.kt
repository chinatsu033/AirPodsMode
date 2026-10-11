package io.github.chinatsu033.airpodsmode.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

object BondedAirPodsFinder {
    @SuppressLint("MissingPermission")
    fun find(context: Context): List<BluetoothDevice> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        return bonded.filter { device ->
            AirPodsIds.looksLikeAirPods(device.name, vendorId = null, productId = null)
        }.sortedBy { it.name ?: it.address }
    }

    @SuppressLint("MissingPermission")
    fun preferred(context: Context): BluetoothDevice? {
        val list = find(context)
        return list.firstOrNull { d ->
            try {
                val m = BluetoothDevice::class.java.getMethod("isConnected")
                m.invoke(d) as? Boolean == true
            } catch (_: Throwable) {
                false
            }
        } ?: list.firstOrNull()
    }
}
