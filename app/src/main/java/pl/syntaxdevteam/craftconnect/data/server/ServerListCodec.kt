package pl.syntaxdevteam.craftconnect.data.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

internal object ServerListCodec {
    fun encode(servers: List<ServerProfile>): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeInt(servers.size)
                servers.forEach { server ->
                    output.writeUTF(server.id)
                    output.writeUTF(server.name)
                    output.writeUTF(server.address)
                    output.writeBoolean(server.favorite)
                }
            }
            buffer.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(encoded: String): List<ServerProfile> = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported server-list format" }
            val count = input.readInt()
            require(count in 0..MAX_SERVERS) { "Invalid server count" }
            List(count) {
                ServerProfile(
                    id = input.readUTF(),
                    name = input.readUTF(),
                    address = input.readUTF(),
                    online = false,
                    playersOnline = 0,
                    playersMax = 0,
                    pingMs = null,
                    favorite = input.readBoolean(),
                )
            }
        }
    }.getOrDefault(emptyList())

    private const val FORMAT_VERSION = 1
    private const val MAX_SERVERS = 1_000
}
