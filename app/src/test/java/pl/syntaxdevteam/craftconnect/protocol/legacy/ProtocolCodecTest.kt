package pl.syntaxdevteam.craftconnect.protocol.legacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun varIntRoundTripsBoundaryValues() {
        listOf(0, 1, 127, 128, 255, 2_097_151, Int.MAX_VALUE, -1).forEach { value ->
            val encoded = ByteArrayOutputStream().also { DataOutputStream(it).writeVarInt(value) }.toByteArray()

            assertEquals(value, ByteArrayInputStream(encoded).readVarInt())
        }
    }

    @Test
    fun uncompressedPacketRoundTripsWhenCompressionIsDisabled() {
        val payload = packet {
            writeVarInt(2)
            writeProtocolString("CraftConnect")
        }

        val decoded = readPacket(ByteArrayInputStream(frame(payload, null)), null)

        assertEquals(2, decoded.readVarInt())
        assertEquals("CraftConnect", decoded.readProtocolString())
    }

    @Test
    fun compressedPacketRoundTripsAboveThreshold() {
        val payload = ByteArray(4_096) { (it % 251).toByte() }

        val decoded = readPacket(ByteArrayInputStream(frame(payload, 256)), 256)

        assertArrayEquals(payload, decoded.readBytes())
    }
}
