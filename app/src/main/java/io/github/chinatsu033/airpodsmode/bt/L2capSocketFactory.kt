package io.github.chinatsu033.airpodsmode.bt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * Opens a classic BR/EDR L2CAP socket to PSM 0x1001.
 * Public [BluetoothDevice.createL2capChannel] is BLE-only and rejects this PSM;
 * we use the hidden [BluetoothDevice.createL2capSocket] via reflection + HiddenApiBypass.
 */
object L2capSocketFactory {
    private var exempted = false

    @Synchronized
    fun ensureHiddenApiExempt() {
        if (exempted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
        }
        exempted = true
    }

    fun open(device: BluetoothDevice, psm: Int): BluetoothSocket {
        ensureHiddenApiExempt()
        val methods = listOf("createL2capSocket", "createInsecureL2capSocket")
        var last: Throwable? = null
        for (name in methods) {
            try {
                val method: Method = BluetoothDevice::class.java.getMethod(name, Int::class.javaPrimitiveType)
                val socket = method.invoke(device, psm) as BluetoothSocket
                return socket
            } catch (t: Throwable) {
                last = t
            }
        }
        // Last-resort: construct BluetoothSocket via HiddenApiBypass (type L2CAP = 3 on many AOSP trees).
        try {
            @Suppress("UNCHECKED_CAST")
            val socket = HiddenApiBypass.newInstance(
                BluetoothSocket::class.java,
                3, // TYPE_L2CAP
                -1, // fd
                true, // auth
                true, // encrypt
                device,
                psm,
                null, // uuid
            ) as BluetoothSocket
            return socket
        } catch (t: Throwable) {
            last = t
        }
        throw IllegalStateException(
            "Cannot open classic L2CAP socket (PSM=0x${psm.toString(16)}). " +
                "One UI 9 / Android 17 is required for non-root Samsung. Cause: ${last?.message}",
            last,
        )
    }
}
