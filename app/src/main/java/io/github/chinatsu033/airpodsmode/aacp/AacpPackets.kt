package io.github.chinatsu033.airpodsmode.aacp

/**
 * AACP (Apple Accessory Communication Protocol) packet builders and parsers.
 *
 * Protocol bytes are reimplemented from publicly documented AACP definitions
 * (LibrePods AAP Definitions / control_commands docs). No LibrePods source code
 * is copied; LibrePods itself is GPL-3.0.
 *
 * Transport: classic Bluetooth L2CAP PSM 0x1001 (4097).
 */
object AacpPackets {
    const val L2CAP_PSM: Int = 0x1001

    /** Required before AirPods accept any other AACP traffic. */
    val HANDSHAKE: ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /**
     * Unlocks Adaptive (and conversational awareness while audio plays) on
     * AirPods Pro 2 / Pro 3 / AirPods 4 ANC. Without this, mode 0x04 may be
     * rejected and the buds reply with another mode.
     */
    val FEATURE_ENABLE_PRO2: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xFF.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /** Subscribe to battery / listening-mode / ear-detection notifications. */
    val REQUEST_NOTIFICATIONS: ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
    )

    /**
     * ListeningModeConfigs bitmask: Off|ANC|Transparency|Adaptive = 0x0F.
     * Ensures Adaptive stays available in the stem long-press rotation.
     */
    val LISTENING_MODE_CONFIGS_ALL: ByteArray = controlCommand(
        id = 0x1A,
        data1 = 0x0F,
    )

    fun setListeningMode(mode: NoiseMode): ByteArray =
        controlCommand(id = 0x0D, data1 = mode.wireValue.toInt() and 0xFF)

    /** Control command: header + opcode 0x0009 LE + id + data1 + three zero pads. */
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

    /**
     * Parse an inbound AACP frame. Returns true if handled.
     */
    fun parse(
        packet: ByteArray,
        onMode: (NoiseMode) -> Unit,
        onBattery: (BatteryStatus) -> Unit,
    ): Boolean {
        if (packet.size < 6) return false
        // Expect 04 00 04 00
        if (packet[0] != 0x04.toByte() || packet[2] != 0x04.toByte()) return false
        val opcode = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        when (opcode) {
            0x0009 -> {
                if (packet.size < 8) return false
                val id = packet[6].toInt() and 0xFF
                if (id == 0x0D) {
                    NoiseMode.fromWire(packet[7])?.let(onMode)
                    return true
                }
            }
            0x0004 -> {
                // Battery notification
                if (packet.size < 7) return false
                val count = packet[6].toInt() and 0xFF
                var offset = 7
                var left: Int? = null
                var right: Int? = null
                var case: Int? = null
                repeat(count) {
                    if (offset + 4 >= packet.size) return@repeat
                    val component = packet[offset].toInt() and 0xFF
                    val level = packet[offset + 2].toInt() and 0xFF
                    when (component) {
                        0x04 -> left = level.coerceIn(0, 100)
                        0x02 -> right = level.coerceIn(0, 100)
                        0x08 -> case = level.coerceIn(0, 100)
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
