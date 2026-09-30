package pl.syntaxdevteam.craftconnect.protocol.legacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

internal fun InputStream.readVarInt(): Int {
    var result = 0
    var position = 0
    while (position < 32) {
        val value = read()
        if (value == -1) throw EOFException("Connection closed while reading VarInt")
        result = result or ((value and 0x7F) shl position)
        if (value and 0x80 == 0) return result
        position += 7
    }
    throw ProtocolCodecException("varint_too_long")
}

internal fun DataInputStream.readProtocolString(maxLength: Int = 32_767): String {
    val byteLength = readVarInt()
    if (byteLength < 0 || byteLength > maxLength * 4) {
        throw ProtocolCodecException("invalid_string_length")
    }
    val bytes = ByteArray(byteLength)
    readFully(bytes)
    return bytes.toString(StandardCharsets.UTF_8).also {
        if (it.length > maxLength) throw ProtocolCodecException("invalid_string_length")
    }
}

internal fun DataOutputStream.writeVarInt(value: Int) {
    var remaining = value
    do {
        var current = remaining and 0x7F
        remaining = remaining ushr 7
        if (remaining != 0) current = current or 0x80
        writeByte(current)
    } while (remaining != 0)
}

internal fun DataOutputStream.writeProtocolString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    writeVarInt(bytes.size)
    write(bytes)
}

internal fun packet(block: DataOutputStream.() -> Unit): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use(block)
        bytes.toByteArray()
    }

internal fun frame(payload: ByteArray, compressionThreshold: Int?): ByteArray {
    val body = if (compressionThreshold == null) {
        payload
    } else if (payload.size < compressionThreshold) {
        packet {
            writeVarInt(0)
            write(payload)
        }
    } else {
        val deflater = Deflater().apply { setInput(payload); finish() }
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (!deflater.finished()) compressed.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        packet {
            writeVarInt(payload.size)
            write(compressed.toByteArray())
        }
    }
    return packet {
        writeVarInt(body.size)
        write(body)
    }
}

internal fun readPacket(input: InputStream, compressionThreshold: Int?): DataInputStream {
    val packetLength = input.readVarInt()
    if (packetLength !in 1..MAX_PACKET_SIZE) throw ProtocolCodecException("invalid_packet_length")
    val framed = ByteArray(packetLength)
    DataInputStream(input).readFully(framed)
    if (compressionThreshold == null) return DataInputStream(ByteArrayInputStream(framed))

    val compressedInput = ByteArrayInputStream(framed)
    val uncompressedLength = compressedInput.readVarInt()
    if (uncompressedLength == 0) return DataInputStream(compressedInput)
    if (uncompressedLength !in compressionThreshold..MAX_PACKET_SIZE) {
        throw ProtocolCodecException("invalid_uncompressed_length")
    }
    val inflater = Inflater()
    return try {
        inflater.setInput(compressedInput.readBytes())
        val output = ByteArray(uncompressedLength)
        val actualLength = inflater.inflate(output)
        if (actualLength != uncompressedLength || !inflater.finished()) {
            throw ProtocolCodecException("invalid_compressed_packet")
        }
        DataInputStream(ByteArrayInputStream(output))
    } finally {
        inflater.end()
    }
}

internal class ProtocolCodecException(val code: String) : Exception(code)

private const val MAX_PACKET_SIZE = 2 * 1024 * 1024
