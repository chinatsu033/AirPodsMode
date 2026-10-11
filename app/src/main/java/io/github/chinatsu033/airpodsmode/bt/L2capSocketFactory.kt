package io.github.chinatsu033.airpodsmode.bt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

object L2capSocketFactory {
    private const val TAG = "L2capSocket"
    private var exempted = false

    @Synchronized
    fun ensureHiddenApiExempt() {
        if (exempted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
            } catch (t: Throwable) {
                Log.w(TAG, "HiddenApiBypass failed (continuing)", t)
            }
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
                return method.invoke(device, psm) as BluetoothSocket
            } catch (t: Throwable) {
                last = t
            }
        }
        try {
            @Suppress("UNCHECKED_CAST")
            return HiddenApiBypass.newInstance(
                BluetoothSocket::class.java,
                3, -1, true, true, device, psm, null,
            ) as BluetoothSocket
        } catch (t: Throwable) {
            last = t
        }
        throw IllegalStateException(
            "Cannot open classic L2CAP socket (PSM=0x${psm.toString(16)}). Cause: ${last?.message}",
            last,
        )
    }
}
