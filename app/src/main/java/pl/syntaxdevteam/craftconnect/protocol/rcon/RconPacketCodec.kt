package pl.syntaxdevteam.craftconnect.protocol.rcon

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal data class RconPacket(
    val requestId: Int,
    val type: Int,
    val body: ByteArray,
)

/** Source RCON framing: little-endian size/id/type followed by body + two NUL bytes. */
internal object RconPacketCodec {
    const val TYPE_RESPONSE_VALUE = 0
    const val TYPE_COMMAND = 2
    const val TYPE_AUTH_RESPONSE = 2
    const val TYPE_AUTH = 3

    private const val MIN_PACKET_PAYLOAD = 10
    private const val MAX_PACKET_PAYLOAD = 64 * 1024

    fun write(output: OutputStream, requestId: Int, type: Int, body: ByteArray) {
        require(body.size <= MAX_PACKET_PAYLOAD - MIN_PACKET_PAYLOAD) { "RCON body too large" }
        val size = 4 + 4 + body.size + 2
        writeIntLE(output, size)
        writeIntLE(output, requestId)
        writeIntLE(output, type)
        output.write(body)
        output.write(0)
        output.write(0)
        output.flush()
    }

    fun read(input: InputStream): RconPacket {
        val size = readIntLE(input)
        require(size in MIN_PACKET_PAYLOAD..MAX_PACKET_PAYLOAD) { "Invalid RCON packet size: $size" }
        val payload = ByteArray(size)
        readFully(input, payload)
        val requestId = intLE(payload, 0)
        val type = intLE(payload, 4)
        require(payload[size - 2] == 0.toByte() && payload[size - 1] == 0.toByte()) {
            "Invalid RCON packet terminator"
        }
        return RconPacket(
            requestId = requestId,
            type = type,
            body = payload.copyOfRange(8, size - 2),
        )
    }

    private fun writeIntLE(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    private fun readIntLE(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if ((b0 or b1 or b2 or b3) < 0) throw EOFException("Unexpected end of RCON stream")
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun intLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException("Unexpected end of RCON stream")
            offset += read
        }
    }
}
