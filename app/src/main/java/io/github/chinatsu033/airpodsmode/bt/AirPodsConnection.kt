package io.github.chinatsu033.airpodsmode.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import io.github.chinatsu033.airpodsmode.aacp.AacpPackets
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
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

class AirPodsConnection(
    private val scope: CoroutineScope,
) {
    data class State(
        val connected: Boolean = false,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val model: AirPodsModel = AirPodsModel.UNKNOWN,
        val modelNumber: String? = null,
        val firmware: String? = null,
        val mode: NoiseMode? = null,
        val adaptiveLevel: Int = AdaptiveNoisePreset.DEFAULT.level,
        val conversationalAwareness: Boolean? = null,
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
    private var productIdHint: Int? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, productId: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            disconnectInternal(clearIdentity = false)
            productIdHint = productId
            val nameGuess = AirPodsModel.resolve(productId, null, device.name)
            _state.value = _state.value.copy(
                connecting = true,
                lastError = null,
                deviceName = device.name,
                deviceAddress = device.address,
                model = nameGuess,
            )
            try {
                val sock = L2capSocketFactory.open(device, AacpPackets.L2CAP_PSM)
                sock.connect()
                socket = sock
                input = sock.inputStream
                output = sock.outputStream

                writeRaw(AacpPackets.HANDSHAKE)
                writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
                writeRaw(AacpPackets.REQUEST_NOTIFICATIONS)
                writeRaw(AacpPackets.LISTENING_MODE_CONFIGS_ALL)

                _state.value = _state.value.copy(connected = true, connecting = false, lastError = null)
                startReader()
                true
            } catch (t: Throwable) {
                Log.w(TAG, "connect failed", t)
                disconnectInternal(clearIdentity = false)
                _state.value = _state.value.copy(
                    connected = false,
                    connecting = false,
                    lastError = t.message ?: t.javaClass.simpleName,
                )
                false
            }
        }

    suspend fun setMode(mode: NoiseMode, adaptiveLevel: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (!_state.value.connected) {
                _state.value = _state.value.copy(lastError = "Not connected")
                return@withContext false
            }
            val model = _state.value.model
            if (mode == NoiseMode.ANC && !model.supportsAnc) return@withContext false
            if (mode == NoiseMode.TRANSPARENCY && !model.supportsTransparency) return@withContext false
            if (mode == NoiseMode.ADAPTIVE && !model.allowAdaptiveCommand()) return@withContext false
            try {
                if (mode == NoiseMode.ADAPTIVE) {
                    writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
                    writeRaw(AacpPackets.LISTENING_MODE_CONFIGS_ALL)
                }
                writeRaw(AacpPackets.setListeningMode(mode))
                if (mode == NoiseMode.ADAPTIVE && model.showAdaptiveStrengthInUi()) {
                    val clamped = (adaptiveLevel ?: _state.value.adaptiveLevel).coerceIn(0, 100)
                    writeRaw(AacpPackets.setAdaptiveNoiseLevel(clamped))
                    _state.value = _state.value.copy(mode = mode, adaptiveLevel = clamped, lastError = null)
                } else {
                    _state.value = _state.value.copy(mode = mode, lastError = null)
                }
                true
            } catch (t: Throwable) {
                Log.w(TAG, "setMode failed", t)
                _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName)
                false
            }
        }

    suspend fun setAdaptiveLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.connected || !_state.value.model.showAdaptiveStrengthInUi()) {
            return@withContext false
        }
        try {
            val clamped = level.coerceIn(0, 100)
            if (_state.value.mode != NoiseMode.ADAPTIVE) {
                writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
                writeRaw(AacpPackets.LISTENING_MODE_CONFIGS_ALL)
                writeRaw(AacpPackets.setListeningMode(NoiseMode.ADAPTIVE))
            }
            writeRaw(AacpPackets.setAdaptiveNoiseLevel(clamped))
            _state.value = _state.value.copy(
                mode = NoiseMode.ADAPTIVE,
                adaptiveLevel = clamped,
                lastError = null,
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setAdaptiveLevel failed", t)
            _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName)
            false
        }
    }

    suspend fun setConversationalAwareness(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!_state.value.connected || !_state.value.model.showConversationalAwarenessInUi()) {
            return@withContext false
        }
        try {
            writeRaw(AacpPackets.FEATURE_ENABLE_PRO2)
            writeRaw(AacpPackets.setConversationalAwareness(enabled))
            _state.value = _state.value.copy(conversationalAwareness = enabled, lastError = null)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setCA failed", t)
            _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName)
            false
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) { disconnectInternal(clearIdentity = false) }
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(2048)
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
                AacpPackets.parse(
                    buf.copyOf(n),
                    onMode = { mode -> _state.value = _state.value.copy(mode = mode) },
                    onBattery = { bat -> _state.value = _state.value.copy(battery = bat) },
                    onConversationalAwareness = { en ->
                        _state.value = _state.value.copy(conversationalAwareness = en)
                    },
                    onAdaptiveLevel = { level ->
                        _state.value = _state.value.copy(adaptiveLevel = level.coerceIn(0, 100))
                    },
                    onDeviceInfo = { info ->
                        val model = AirPodsModel.resolve(
                            productIdHint,
                            info.modelNumber,
                            info.advertisingName ?: _state.value.deviceName,
                        )
                        _state.value = _state.value.copy(
                            model = model,
                            modelNumber = info.modelNumber,
                            firmware = info.firmware,
                            deviceName = info.advertisingName ?: _state.value.deviceName,
                        )
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

    private fun disconnectInternal(clearIdentity: Boolean) {
        readerJob?.cancel()
        readerJob = null
        try { input?.close() } catch (_: Throwable) {}
        try { output?.close() } catch (_: Throwable) {}
        try { socket?.close() } catch (_: Throwable) {}
        input = null
        output = null
        socket = null
        _state.value = if (clearIdentity) {
            State()
        } else {
            _state.value.copy(connected = false, connecting = false)
        }
    }

    companion object {
        private const val TAG = "AirPodsConnection"
    }
}
