package pl.syntaxdevteam.craftconnect.protocol.modern

import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
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
                    client.soTimeout = 2_000
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

                    output.write(frame(packet {
                        writeVarInt(0x79); writeByte(8); writeUTF("Welcome before UI"); writeBoolean(false)
                    }, null))
                    output.write(frame(packet { writeVarInt(0x2C); writeLong(987_654_321L) }, null))
                    output.flush()
                    val keepAlive = readPacket(input, null)
                    assertEquals(0x1C, keepAlive.readVarInt())
                    assertEquals(987_654_321L, keepAlive.readLong())
                    // No visibility command is sent at protocol login. Only after
                    // world readiness and an API-backed bridge confirmation.
                    output.write(frame(packet {
                        writeVarInt(0x48)
                        writeVarInt(42)
                        writeDouble(1.0); writeDouble(64.0); writeDouble(2.0)
                        repeat(3) { writeDouble(0.0) }
                        writeFloat(0f); writeFloat(0f)
                        writeInt(0)
                    }, null))
                    output.flush()
                    assertEquals(0, readPacket(input, null).readVarInt())
                    assertEquals(0x1F, readPacket(input, null).readVarInt())
                    val registration = readPacket(input, null)
                    assertEquals(0x16, registration.readVarInt())
                    assertEquals("minecraft:register", registration.readProtocolString())
                    val subscription = readPacket(input, null)
                    assertEquals(0x16, subscription.readVarInt())
                    assertEquals(AuthenticationBridgeProtocol.CHANNEL, subscription.readProtocolString())
                    val nonce = AuthenticationBridgeProtocol.subscriptionNonce(
                        ByteArray(subscription.available()).also(subscription::readFully))
                    output.write(frame(packet {
                        writeVarInt(0x18)
                        writeProtocolString(AuthenticationBridgeProtocol.CHANNEL)
                        write(AuthenticationBridgeProtocol.authenticated(nonce, setOf("nLogin")))
                    }, null))
                    output.flush()
                    val command = readPacket(input, null)
                    assertEquals(0x07, command.readVarInt())
                    assertEquals("gamemode spectator", command.readProtocolString())
                    output.write(frame(packet { writeVarInt(0x26); writeByte(3); writeFloat(3f) }, null))
                    output.flush()

                }
            }
            val connection = ModernOfflineMinecraftConnection(2_000)
            val profile = ServerProfile(
                "test", "Test", "127.0.0.1:${serverSocket.localPort}", false, 0, 0, null,
            )

            val session = connection.connect(profile, "OfflineUser")

            assertEquals(775, session.protocolVersion)
            assertEquals("OfflineUser", session.username)
            withTimeout(5_000) { server.await() }
            assertEquals("Welcome before UI", connection.chatMessages.value.first().content)
            connection.disconnect()
        }
    }
}

