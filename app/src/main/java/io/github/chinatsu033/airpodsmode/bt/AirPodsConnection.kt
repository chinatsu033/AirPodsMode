package io.github.chinatsu033.airpodsmode.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import io.github.chinatsu033.airpodsmode.aacp.AacpPackets
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Holds one AACP L2CAP session to a bonded AirPods device.
 */
class AirPodsConnection(
    private val scope: CoroutineScope,
) {
    data class State(
        val connected: Boolean = false,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val mode: NoiseMode? = null,
        val battery: AacpPackets.BatteryStatus? = null,
        val lastError: String? = null,
        val connecting: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val writeMutex = Mutex()
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = _state.value.copy(
            connecting = true,
            lastError = null,
            deviceName = device.name,
            deviceAddress = device.address,
        )
        try {
            val sock = L2capSocketFactory.open(device, AacpPackets.L2CAP_PSM)
            sock.connect()
            socket = sock
            input = sock.inputStream
            output = sock.outputStream

            // Handshake → feature unlock (Adaptive) → notifications → enable all listening modes.
            writeRaw(AacpPackets.HANDSHAKE)
            writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
            writeRaw(AacpPackets.REQUEST_NOTIFICATIONS)
            writeRaw(AacpPackets.LISTENING_MODE_CONFIGS_ALL)

            _state.value = _state.value.copy(connected = true, connecting = false, lastError = null)
            startReader()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "connect failed", t)
            disconnectInternal()
            _state.value = _state.value.copy(
                connected = false,
                connecting = false,
                lastError = t.message ?: t.javaClass.simpleName,
            )
            false
        }
    }

    suspend fun setMode(mode: NoiseMode): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.connected) {
            _state.value = _state.value.copy(lastError = "Not connected")
            return@withContext false
        }
        // Re-send Adaptive unlock before Adaptive itself (Pro 2/3 requirement).
        try {
            if (mode == NoiseMode.ADAPTIVE) {
                writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
                writeRaw(AacpPackets.LISTENING_MODE_CONFIGS_ALL)
            }
            writeRaw(AacpPackets.setListeningMode(mode))
            // Optimistic UI update; confirmation arrives via notification packet.
            _state.value = _state.value.copy(mode = mode, lastError = null)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setMode failed", t)
            _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName)
            false
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) { disconnectInternal() }
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            val stream = input ?: return@launch
            while (isActive) {
                val n = try {
                    stream.read(buf)
                } catch (_: Throwable) {
                    -1
                }
                if (n <= 0) {
                    _state.value = _state.value.copy(
                        connected = false,
                        lastError = _state.value.lastError ?: "Disconnected",
                    )
                    break
                }
                val packet = buf.copyOf(n)
                AacpPackets.parse(
                    packet,
                    onMode = { mode ->
                        _state.value = _state.value.copy(mode = mode)
                    },
                    onBattery = { bat ->
                        _state.value = _state.value.copy(battery = bat)
                    },
                )
            }
        }
    }

    private suspend fun writeRaw(bytes: ByteArray) {
        writeMutex.withLock {
            val out = output ?: error("No output stream")
            out.write(bytes)
            out.flush()
        }
    }

    private fun disconnectInternal() {
        readerJob?.cancel()
        readerJob = null
        try {
            input?.close()
        } catch (_: Throwable) {
        }
        try {
            output?.close()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        input = null
        output = null
        socket = null
        _state.value = _state.value.copy(connected = false, connecting = false)
    }

    companion object {
        private const val TAG = "AirPodsConnection"
    }
}
