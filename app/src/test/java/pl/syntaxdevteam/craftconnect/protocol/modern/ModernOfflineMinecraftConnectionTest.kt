package pl.syntaxdevteam.craftconnect.protocol.modern

import java.net.ServerSocket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.protocol.legacy.frame
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.readPacket
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ModernOfflineMinecraftConnectionTest {
    @Test
    fun completesLoginConfigurationAndPlayKeepAlive() = runBlocking {
        ServerSocket(0).use { serverSocket ->
            val server = async(Dispatchers.IO) {
                serverSocket.accept().use { client ->
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    readPacket(input, null).also {
                        assertEquals(0, it.readVarInt())
                        assertEquals(775, it.readVarInt())
                        it.readProtocolString()
                        it.readUnsignedShort()
                        assertEquals(2, it.readVarInt())
                    }
                    val login = readPacket(input, null)
                    assertEquals(0, login.readVarInt())
                    assertEquals("OfflineUser", login.readProtocolString())
                    val uuid = UUID(login.readLong(), login.readLong())

                    output.write(frame(packet {
                        writeVarInt(2)
                        writeLong(uuid.mostSignificantBits)
                        writeLong(uuid.leastSignificantBits)
                        writeProtocolString("OfflineUser")
                        writeVarInt(0)
                    }, null))
                    output.flush()
                    assertEquals(3, readPacket(input, null).readVarInt())
                    assertEquals(0, readPacket(input, null).readVarInt())

                    output.write(frame(packet {
                        writeVarInt(0x0E)
                        writeVarInt(1)
                        writeProtocolString("minecraft")
                        writeProtocolString("core")
                        writeProtocolString("26.1")
                    }, null))
                    output.flush()
                    val knownPacks = readPacket(input, null)
                    assertEquals(7, knownPacks.readVarInt())
                    assertEquals(1, knownPacks.readVarInt())
                    assertEquals("minecraft", knownPacks.readProtocolString())
                    assertEquals("core", knownPacks.readProtocolString())
                    assertEquals("26.1", knownPacks.readProtocolString())

                    output.write(frame(packet { writeVarInt(3) }, null))
                    output.flush()
                    assertEquals(3, readPacket(input, null).readVarInt())

                    output.write(frame(packet { writeVarInt(0x2C); writeLong(987_654_321L) }, null))
                    output.flush()
                    val keepAlive = readPacket(input, null)
                    assertEquals(0x1C, keepAlive.readVarInt())
                    assertEquals(987_654_321L, keepAlive.readLong())
                }
            }
            val connection = ModernOfflineMinecraftConnection(2_000)
            val profile = ServerProfile(
                "test", "Test", "127.0.0.1:${serverSocket.localPort}", false, 0, 0, null,
            )

            val session = connection.connect(profile, "OfflineUser")

            assertEquals(775, session.protocolVersion)
            assertEquals("OfflineUser", session.username)
            withTimeout(2_000) { server.await() }
            connection.disconnect()
        }
    }
}
