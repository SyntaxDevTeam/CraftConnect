package pl.syntaxdevteam.craftconnect.data.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import pl.syntaxdevteam.craftconnect.domain.integration.RconConfiguration
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
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
                    output.writeUTF(server.minecraftVersion.name)
                    val rcon = server.rcon
                    output.writeBoolean(rcon != null)
                    if (rcon != null) {
                        output.writeBoolean(rcon.enabled)
                        output.writeUTF(rcon.host)
                        output.writeInt(rcon.port)
                        output.writeBoolean(rcon.credentialId != null)
                        rcon.credentialId?.let(output::writeUTF)
                    }
                }
            }
            buffer.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(encoded: String): List<ServerProfile> = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version in 1..FORMAT_VERSION) { "Unsupported server-list format" }
            val count = input.readInt()
            require(count in 0..MAX_SERVERS) { "Invalid server count" }
            List(count) {
                val id = input.readUTF()
                val name = input.readUTF()
                val address = input.readUTF()
                val favorite = input.readBoolean()
                val minecraftVersion = if (version >= 2) {
                    MinecraftVersion.fromKey(input.readUTF())
                } else {
                    MinecraftVersion.JAVA_26_1
                }
                val rcon = if (version >= 3 && input.readBoolean()) {
                    val enabled = input.readBoolean()
                    val host = input.readUTF()
                    val port = input.readInt()
                    val credentialId = if (input.readBoolean()) input.readUTF() else null
                    RconConfiguration(enabled, host, port, credentialId)
                } else {
                    null
                }
                ServerProfile(
                    id = id,
                    name = name,
                    address = address,
                    online = false,
                    playersOnline = 0,
                    playersMax = 0,
                    pingMs = null,
                    favorite = favorite,
                    minecraftVersion = minecraftVersion,
                    rcon = rcon,
                )
            }
        }
    }.getOrDefault(emptyList())

    private const val FORMAT_VERSION = 3
    private const val MAX_SERVERS = 1_000
}
