package io.github.chinatsu033.airpodsmode

import io.github.chinatsu033.airpodsmode.aacp.AacpPackets
import io.github.chinatsu033.airpodsmode.aacp.NoiseMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AacpPacketsTest {
    @Test
    fun setListeningMode_adaptivePacket() {
        val pkt = AacpPackets.setListeningMode(NoiseMode.ADAPTIVE)
        assertArrayEquals(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x04, 0x00, 0x00, 0x00),
            pkt,
        )
    }

    @Test
    fun setListeningMode_ancAndTransparency() {
        assertEquals(0x02.toByte(), AacpPackets.setListeningMode(NoiseMode.ANC)[7])
        assertEquals(0x03.toByte(), AacpPackets.setListeningMode(NoiseMode.TRANSPARENCY)[7])
    }

    @Test
    fun featureEnable_unlocksAdaptive() {
        assertEquals(0x4D.toByte(), AacpPackets.FEATURE_ENABLE_PRO2[4])
        assertEquals(0xFF.toByte(), AacpPackets.FEATURE_ENABLE_PRO2[6])
    }

    @Test
    fun parseNoiseModeNotification() {
        var seen: NoiseMode? = null
        val ok = AacpPackets.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x04, 0x00, 0x00, 0x00),
            onMode = { seen = it },
            onBattery = {},
        )
        assertTrue(ok)
        assertEquals(NoiseMode.ADAPTIVE, seen)
    }

    @Test
    fun parseBatteryNotification() {
        var bat: AacpPackets.BatteryStatus? = null
        val packet = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x04, 0x00, 0x03,
            0x04, 0x01, 0x64, 0x02, 0x01,
            0x02, 0x01, 0x63, 0x01, 0x01,
            0x08, 0x01, 0x11, 0x02, 0x01,
        )
        val ok = AacpPackets.parse(packet, onMode = {}, onBattery = { bat = it })
        assertTrue(ok)
        assertEquals(100, bat?.leftPercent)
        assertEquals(99, bat?.rightPercent)
        assertEquals(17, bat?.casePercent)
    }

    @Test
    fun cycleOrder_endsOnAdaptiveThenAnc() {
        assertEquals(NoiseMode.TRANSPARENCY, NoiseMode.cycleNext(NoiseMode.ANC))
        assertEquals(NoiseMode.ADAPTIVE, NoiseMode.cycleNext(NoiseMode.TRANSPARENCY))
        assertEquals(NoiseMode.ANC, NoiseMode.cycleNext(NoiseMode.ADAPTIVE))
        assertEquals(NoiseMode.ADAPTIVE, NoiseMode.cycleNext(null))
    }
}
