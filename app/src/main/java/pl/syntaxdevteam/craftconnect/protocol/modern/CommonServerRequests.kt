package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import java.util.UUID
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

/** Common requests which can gate configuration completion on vanilla servers and proxies. */
internal class CommonServerRequests(private val send: (ByteArray) -> Unit) {
    private val cookies = linkedMapOf<String, ByteArray>()

    fun replyCookie(input: DataInputStream, responseId: Int) {
        val key = input.readProtocolString(32_767)
        val value = cookies[key]
        send(packet {
            writeVarInt(responseId)
            writeProtocolString(key)
            writeBoolean(value != null)
            if (value != null) { writeVarInt(value.size); write(value) }
        })
    }

    fun storeCookie(input: DataInputStream) {
        val key = input.readProtocolString(32_767)
        val length = input.readVarInt()
        require(length in 0..5_120) { "Invalid cookie size" }
        val value = ByteArray(length).also(input::readFully)
        if (key !in cookies && cookies.size >= 16) cookies.remove(cookies.keys.first())
        cookies[key] = value
    }

    fun replyResourcePack(input: DataInputStream, responseId: Int) {
        val uuid = UUID(input.readLong(), input.readLong())
        input.readProtocolString() // URL: never fetched or recorded by this headless client
        input.readProtocolString(40) // SHA-1
        input.readBoolean() // required
        if (input.readBoolean()) input.readAnonymousNbt() // optional prompt
        // There are no visual resources to apply. Complete the protocol task without
        // downloading textures/audio, as other headless console clients do.
        for (status in intArrayOf(3, 4, 0)) { // accepted, downloaded, successfully loaded
            send(packet {
                writeVarInt(responseId)
                writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits)
                writeVarInt(status)
            })
        }
    }

    fun clear() = cookies.clear()
}
