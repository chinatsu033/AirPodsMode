package io.github.chinatsu033.airpodsmode

import io.github.chinatsu033.airpodsmode.aacp.AacpPackets
import io.github.chinatsu033.airpodsmode.aacp.AdaptiveNoisePreset
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
    fun adaptiveNoiseLevel_presets() {
        assertArrayEquals(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, 0x00, 0x00, 0x00, 0x00),
            AacpPackets.setAdaptiveNoiseLevel(0),
        )
        assertEquals(0x32.toByte(), AacpPackets.setAdaptiveNoiseLevel(50)[7])
        assertEquals(0x64.toByte(), AacpPackets.setAdaptiveNoiseLevel(100)[7])
        assertEquals(0x64.toByte(), AacpPackets.setAdaptiveNoiseLevel(999)[7])
    }

    @Test
    fun conversationalAwareness_onOff() {
        assertEquals(0x01.toByte(), AacpPackets.setConversationalAwareness(true)[7])
        assertEquals(0x02.toByte(), AacpPackets.setConversationalAwareness(false)[7])
        assertEquals(0x28.toByte(), AacpPackets.setConversationalAwareness(true)[6])
    }

    @Test
    fun parseCaAndAdaptiveLevel() {
        var ca: Boolean? = null
        var level: Int? = null
        AacpPackets.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x28, 0x01, 0x00, 0x00, 0x00),
            onMode = {},
            onBattery = {},
            onConversationalAwareness = { ca = it },
        )
        AacpPackets.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, 0x32, 0x00, 0x00, 0x00),
            onMode = {},
            onBattery = {},
            onAdaptiveLevel = { level = it },
        )
        assertEquals(true, ca)
        assertEquals(50, level)
    }

    @Test
    fun presetNearest() {
        assertEquals(AdaptiveNoisePreset.MORE_TRANSPARENCY, AdaptiveNoisePreset.nearest(5))
        assertEquals(AdaptiveNoisePreset.DEFAULT, AdaptiveNoisePreset.nearest(50))
        assertEquals(AdaptiveNoisePreset.MORE_ANC, AdaptiveNoisePreset.nearest(90))
    }

    @Test
    fun cycleOrder() {
        assertEquals(NoiseMode.TRANSPARENCY, NoiseMode.cycleNext(NoiseMode.ANC))
        assertEquals(NoiseMode.ADAPTIVE, NoiseMode.cycleNext(NoiseMode.TRANSPARENCY))
        assertEquals(NoiseMode.ANC, NoiseMode.cycleNext(NoiseMode.ADAPTIVE))
    }
}
