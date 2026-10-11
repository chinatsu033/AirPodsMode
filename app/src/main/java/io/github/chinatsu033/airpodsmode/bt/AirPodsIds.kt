package io.github.chinatsu033.airpodsmode.bt

object AirPodsIds {
    const val APPLE_VENDOR_ID: Int = 0x004C

    val ALL_PRODUCT_IDS: Set<Int> =
        AirPodsModel.entries.flatMap { it.productIds }.toSet()

    fun looksLikeAirPods(name: String?, vendorId: Int?, productId: Int?): Boolean {
        if (vendorId == APPLE_VENDOR_ID && productId != null && productId in ALL_PRODUCT_IDS) return true
        if (productId != null && productId in ALL_PRODUCT_IDS) return true
        return AirPodsModel.fromName(name) != null
    }
}
