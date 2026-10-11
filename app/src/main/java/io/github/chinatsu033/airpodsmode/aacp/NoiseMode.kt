package io.github.chinatsu033.airpodsmode.aacp

/**
 * AirPods listening / noise-control modes (AACP control id 0x0D).
 * Values come from publicly documented AACP packet captures.
 */
enum class NoiseMode(val wireValue: Byte) {
    OFF(0x01),
    ANC(0x02),
    TRANSPARENCY(0x03),
    ADAPTIVE(0x04);

    companion object {
        fun fromWire(value: Byte): NoiseMode? = entries.firstOrNull { it.wireValue == value }

        /** Cycle used by the Quick Settings tile: ANC → Transparency → Adaptive → ANC. */
        fun cycleNext(current: NoiseMode?): NoiseMode = when (current) {
            ANC -> TRANSPARENCY
            TRANSPARENCY -> ADAPTIVE
            ADAPTIVE -> ANC
            OFF, null -> ADAPTIVE
        }
    }
}
