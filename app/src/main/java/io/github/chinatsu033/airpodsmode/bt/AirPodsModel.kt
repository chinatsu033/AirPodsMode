package io.github.chinatsu033.airpodsmode.bt

/**
 * Known AirPods / Max models and which AACP listening features they support.
 * Product IDs from public Bluetooth PID tables (Apple Wiki / LibrePods docs) — reimplemented.
 *
 * IMPORTANT: Bluetooth classic name for Pro 2 / Pro 3 is often just "AirPods Pro"
 * (no generation). Never map that bare name to PRO_1 — that hid Adaptive on Pro 3.
 */
enum class AirPodsModel(
    val displayName: String,
    val productIds: Set<Int>,
    val supportsAnc: Boolean,
    val supportsTransparency: Boolean,
    val supportsAdaptive: Boolean,
    /** Adaptive Audio Noise strength (control 0x2E). */
    val supportsAdaptiveStrength: Boolean,
    val supportsConversationalAwareness: Boolean,
) {
    PRO_1(
        displayName = "AirPods Pro",
        productIds = setOf(0x200E),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = false,
        supportsAdaptiveStrength = false,
        supportsConversationalAwareness = false,
    ),
    PRO_2(
        displayName = "AirPods Pro 2",
        // 0x2014 USB-C / Lightning variants variously reported; 0x2024 USB-C
        productIds = setOf(0x2014, 0x2024),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = true,
        supportsAdaptiveStrength = true,
        supportsConversationalAwareness = true,
    ),
    PRO_3(
        displayName = "AirPods Pro 3",
        // Community / LibrePods PID tables; also accept nearby unknowns via name.
        productIds = setOf(0x2027, 0x2031, 0x2032),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = true,
        supportsAdaptiveStrength = true,
        supportsConversationalAwareness = true,
    ),
    AIRPODS_4_ANC(
        displayName = "AirPods 4 ANC",
        productIds = setOf(0x201B),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = true,
        supportsAdaptiveStrength = true,
        supportsConversationalAwareness = true,
    ),
    MAX_1(
        displayName = "AirPods Max",
        productIds = setOf(0x200A, 0x201F),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = false,
        supportsAdaptiveStrength = false,
        supportsConversationalAwareness = false,
    ),
    MAX_2(
        displayName = "AirPods Max 2",
        productIds = setOf(0x202D),
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = true,
        supportsAdaptiveStrength = true,
        supportsConversationalAwareness = true,
    ),
    AIRPODS_3(
        displayName = "AirPods 3",
        productIds = setOf(0x2013),
        supportsAnc = false,
        supportsTransparency = true,
        supportsAdaptive = false,
        supportsAdaptiveStrength = false,
        supportsConversationalAwareness = false,
    ),
    UNKNOWN(
        displayName = "AirPods",
        productIds = emptySet(),
        // Optimistic defaults so Pro-class buds still work when PID unknown.
        supportsAnc = true,
        supportsTransparency = true,
        supportsAdaptive = true,
        supportsAdaptiveStrength = true,
        supportsConversationalAwareness = true,
    );

    /**
     * UI: show Adaptive unless we *positively* know the hardware lacks it
     * (AirPods 3 stem, original Max, or Pro 1 identified by PID only).
     */
    fun showAdaptiveInUi(): Boolean = when (this) {
        AIRPODS_3, MAX_1 -> false
        PRO_1 -> false // only when PID/model string confirmed Pro 1
        else -> true
    }

    fun showAdaptiveStrengthInUi(): Boolean =
        supportsAdaptiveStrength || this == UNKNOWN || this == PRO_2 || this == PRO_3 || this == AIRPODS_4_ANC || this == MAX_2

    fun showConversationalAwarenessInUi(): Boolean =
        supportsConversationalAwareness || this == UNKNOWN || this == PRO_2 || this == PRO_3 || this == AIRPODS_4_ANC || this == MAX_2

    /** Allow sending Adaptive AACP even if supportsAdaptive is false (misdetect recovery). */
    fun allowAdaptiveCommand(): Boolean = when (this) {
        AIRPODS_3, MAX_1 -> false
        else -> true // includes PRO_1 name-misdetect recovery + UNKNOWN
    }

    companion object {
        fun fromProductId(pid: Int?): AirPodsModel? {
            if (pid == null) return null
            return entries.firstOrNull { pid in it.productIds }
        }

        fun fromName(name: String?): AirPodsModel? {
            val n = name?.lowercase().orEmpty()
            if (n.isBlank()) return null
            return when {
                n.contains("pro 3") || n.contains("pro3") -> PRO_3
                n.contains("pro 2") || n.contains("pro2") -> PRO_2
                // Explicit gen-1 only — NEVER bare "AirPods Pro" (that is Pro 2/3 on modern devices)
                n.contains("pro 1") || n.contains("pro1") || n.contains("pro (1st") -> PRO_1
                n.contains("max 2") || n.contains("max2") -> MAX_2
                n.contains("max") -> MAX_1
                n.contains("airpods 4") || n.contains("airpod 4") -> AIRPODS_4_ANC
                n.contains("airpods 3") || n.contains("airpod 3") -> AIRPODS_3
                // Bare "AirPods Pro" / "xxx的AirPods Pro" → UNKNOWN (Adaptive ON), not PRO_1
                n.contains("airpods pro") || (n.contains("pro") && n.contains("airpod")) -> UNKNOWN
                n.contains("airpods") || n.contains("airpod") -> UNKNOWN
                else -> null
            }
        }

        /**
         * Resolve from product id, AACP model string (e.g. A3048 / A3064), then Bluetooth name.
         */
        fun resolve(productId: Int?, modelString: String?, bluetoothName: String?): AirPodsModel {
            fromProductId(productId)?.let { return it }
            val m = modelString?.uppercase().orEmpty()
            when {
                m.startsWith("A3063") || m.startsWith("A3064") || m.startsWith("A3065") ||
                    m.startsWith("A3122") || m.startsWith("A3184") || m.startsWith("A3185") ||
                    m.startsWith("A3186") -> return PRO_3
                m.startsWith("A2698") || m.startsWith("A2699") || m.startsWith("A2700") ||
                    m.startsWith("A2931") || m.startsWith("A3047") || m.startsWith("A3048") ||
                    m.startsWith("A2968") -> return PRO_2
                m.startsWith("A2083") || m.startsWith("A2084") || m.startsWith("A2190") -> return PRO_1
                m.startsWith("A3055") || m.startsWith("A3056") || m.startsWith("A3057") ||
                    m.startsWith("A3059") -> return AIRPODS_4_ANC
                m.startsWith("A2096") -> return MAX_1
                m.startsWith("A3454") -> return MAX_2
                m.startsWith("A2564") || m.startsWith("A2565") || m.startsWith("A2566") -> return AIRPODS_3
            }
            return fromName(bluetoothName) ?: UNKNOWN
        }
    }
}
