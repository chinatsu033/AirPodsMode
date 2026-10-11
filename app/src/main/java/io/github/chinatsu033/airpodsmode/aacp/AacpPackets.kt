package io.github.chinatsu033.airpodsmode.aacp

/**
 * AACP packet builders/parsers reimplemented from public AACP docs
 * (LibrePods AAP Definitions / control_commands). No GPL source copied.
 * Transport: classic Bluetooth L2CAP PSM 0x1001.
 */
object AacpPackets {
    const val L2CAP_PSM: Int = 0x1001

    val HANDSHAKE: ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /** Unlocks Adaptive + conversational awareness while audio plays (Pro 2/3 / AirPods 4 ANC). */
    val FEATURE_ENABLE_PRO2: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xFF.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    val REQUEST_NOTIFICATIONS: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
    )

    /** Off|ANC|Transparency|Adaptive */
    val LISTENING_MODE_CONFIGS_ALL: ByteArray = controlCommand(id = 0x1A, data1 = 0x0F)

    fun setListeningMode(mode: NoiseMode): ByteArray =
        controlCommand(id = 0x0D, data1 = mode.wireValue.toInt() and 0xFF)

    /**
     * Adaptive Audio Noise strength (control id 0x2E). 0 = more transparency,
     * 100 = more ANC. Only effective while listening mode is Adaptive.
     */
    fun setAdaptiveNoiseLevel(level: Int): ByteArray =
        controlCommand(id = 0x2E, data1 = level.coerceIn(0, 100))

    /** Conversational Awareness: 0x01 on, 0x02 off (control id 0x28). */
    fun setConversationalAwareness(enabled: Boolean): ByteArray =
        controlCommand(id = 0x28, data1 = if (enabled) 0x01 else 0x02)

    fun controlCommand(id: Int, data1: Int, data2: Int = 0): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x09, 0x00,
        (id and 0xFF).toByte(),
        (data1 and 0xFF).toByte(),
        (data2 and 0xFF).toByte(),
        0x00, 0x00,
    )

    data class BatteryStatus(
        val leftPercent: Int? = null,
        val rightPercent: Int? = null,
        val casePercent: Int? = null,
    )

    fun parse(
        packet: ByteArray,
        onMode: (NoiseMode) -> Unit,
        onBattery: (BatteryStatus) -> Unit,
        onConversationalAwareness: (Boolean) -> Unit = {},
        onAdaptiveLevel: (Int) -> Unit = {},
    ): Boolean {
        if (packet.size < 6) return false
        if (packet[0] != 0x04.toByte() || packet[2] != 0x04.toByte()) return false
        val opcode = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        when (opcode) {
            0x0009 -> {
                if (packet.size < 8) return false
                when (packet[6].toInt() and 0xFF) {
                    0x0D -> {
                        NoiseMode.fromWire(packet[7])?.let(onMode)
                        return true
                    }
                    0x28 -> {
                        when (packet[7].toInt() and 0xFF) {
                            0x01 -> onConversationalAwareness(true)
                            0x02 -> onConversationalAwareness(false)
                        }
                        return true
                    }
                    0x2E -> {
                        onAdaptiveLevel(packet[7].toInt() and 0xFF)
                        return true
                    }
                }
            }
            0x0004 -> {
                if (packet.size < 7) return false
                val count = packet[6].toInt() and 0xFF
                var offset = 7
                var left: Int? = null
                var right: Int? = null
                var case: Int? = null
                repeat(count) {
                    if (offset + 4 >= packet.size) return@repeat
                    when (packet[offset].toInt() and 0xFF) {
                        0x04 -> left = (packet[offset + 2].toInt() and 0xFF).coerceIn(0, 100)
                        0x02 -> right = (packet[offset + 2].toInt() and 0xFF).coerceIn(0, 100)
                        0x08 -> case = (packet[offset + 2].toInt() and 0xFF).coerceIn(0, 100)
                    }
                    offset += 5
                }
                onBattery(BatteryStatus(left, right, case))
                return true
            }
        }
        return false
    }
}

/** Presets for the Adaptive 3-way panel (iOS-like). */
enum class AdaptiveNoisePreset(val level: Int) {
    MORE_TRANSPARENCY(0),
    DEFAULT(50),
    MORE_ANC(100);

    companion object {
        fun nearest(level: Int?): AdaptiveNoisePreset {
            if (level == null) return DEFAULT
            return entries.minBy { kotlin.math.abs(it.level - level) }
        }
    }
}
