package pl.syntaxdevteam.craftconnect.protocol.rcon

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RconPacketCodecTest {
    @Test
    fun roundTripUsesLittleEndianSourceFraming() {
        val output = ByteArrayOutputStream()
        val body = "list".toByteArray(Charsets.UTF_8)

        RconPacketCodec.write(output, 0x01020304, RconPacketCodec.TYPE_COMMAND, body)
        val bytes = output.toByteArray()

        assertEquals(14, bytes[0].toInt() and 0xff)
        assertEquals(0, bytes[1].toInt() and 0xff)
        assertEquals(0, bytes[2].toInt() and 0xff)
        assertEquals(0, bytes[3].toInt() and 0xff)

        val packet = RconPacketCodec.read(ByteArrayInputStream(bytes))
        assertEquals(0x01020304, packet.requestId)
        assertEquals(RconPacketCodec.TYPE_COMMAND, packet.type)
        assertArrayEquals(body, packet.body)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingDoubleNullTerminator() {
        val output = ByteArrayOutputStream()
        RconPacketCodec.write(output, 1, RconPacketCodec.TYPE_COMMAND, byteArrayOf(1, 2, 3))
        val bytes = output.toByteArray()
        bytes[bytes.lastIndex] = 1

        RconPacketCodec.read(ByteArrayInputStream(bytes))
    }
}
