package io.github.chinatsu033.airpodsmode.bt

/**
 * Apple Bluetooth vendor ID and known AirPods / Beats product IDs that speak AACP.
 * Pro 3 = 0x2027 (Device1,8231). Detection also falls back to bonded name matching.
 */
object AirPodsIds {
    const val APPLE_VENDOR_ID: Int = 0x004C

    /** Product IDs known to support listening-mode AACP (including Adaptive where applicable). */
    val PRODUCT_IDS: Set<Int> = setOf(
        0x200E, // AirPods Pro 1
        0x2013, // AirPods 3
        0x2014, // AirPods Pro 2 Lightning
        0x201B, // AirPods 4 ANC
        0x201F, // AirPods Max USB-C
        0x2024, // AirPods Pro 2 USB-C
        0x2027, // AirPods Pro 3
        0x200A, // AirPods Max Lightning
    )

    private val NAME_HINTS = listOf(
        "airpods",
        "airpod",
    )

    fun looksLikeAirPods(name: String?, vendorId: Int?, productId: Int?): Boolean {
        if (vendorId == APPLE_VENDOR_ID && productId != null && productId in PRODUCT_IDS) return true
        val n = name?.lowercase().orEmpty()
        return NAME_HINTS.any { n.contains(it) }
    }
}
