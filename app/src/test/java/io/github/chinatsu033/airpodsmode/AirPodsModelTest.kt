package io.github.chinatsu033.airpodsmode

import io.github.chinatsu033.airpodsmode.bt.AirPodsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsModelTest {
    @Test
    fun productIds() {
        assertEquals(AirPodsModel.PRO_3, AirPodsModel.fromProductId(0x2027))
        assertEquals(AirPodsModel.PRO_2, AirPodsModel.fromProductId(0x2024))
        assertEquals(AirPodsModel.AIRPODS_4_ANC, AirPodsModel.fromProductId(0x201B))
        assertEquals(AirPodsModel.MAX_2, AirPodsModel.fromProductId(0x202D))
    }

    @Test
    fun pro1_hidesAdaptive() {
        assertFalse(AirPodsModel.PRO_1.supportsAdaptive)
        assertFalse(AirPodsModel.PRO_1.supportsConversationalAwareness)
        assertTrue(AirPodsModel.PRO_1.supportsAnc)
    }

    @Test
    fun resolveModelString() {
        assertEquals(AirPodsModel.PRO_3, AirPodsModel.resolve(null, "A3064", null))
        assertEquals(AirPodsModel.PRO_2, AirPodsModel.resolve(null, "A3048", "AirPods Pro"))
    }
}
