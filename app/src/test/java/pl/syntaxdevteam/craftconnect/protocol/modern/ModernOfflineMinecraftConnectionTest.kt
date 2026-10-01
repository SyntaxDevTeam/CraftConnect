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
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.protocol.legacy.frame
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.readPacket
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ModernOfflineMinecraftConnectionTest {
    @Test
    fun completesLoginConfigurationAndPlayKeepAlive() = verifyVersion(MinecraftVersion.JAVA_26_1)

    @Test fun connectsWith26_2() = verifyVersion(MinecraftVersion.JAVA_26_2)
    @Test fun connectsWith26_3() = verifyVersion(MinecraftVersion.JAVA_26_3)

    private fun verifyVersion(version: MinecraftVersion) = runBlocking {
        val latest = version == MinecraftVersion.JAVA_26_3
        ServerSocket(0).use { serverSocket ->
            val server = async(Dispatchers.IO) {
                serverSocket.accept().use { client ->
                    client.soTimeout = 2_000
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    readPacket(input, null).also {
                        assertEquals(0, it.readVarInt())
                        assertEquals(version.protocol, it.readVarInt())
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
                        if (version.protocol >= 776) { writeLong(0); writeLong(0) }
                    }, null))
                    output.flush()
                    assertEquals(3, readPacket(input, null).readVarInt())
                    assertEquals(0, readPacket(input, null).readVarInt())

                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x0F else 0x0E)
                        writeVarInt(1)
                        writeProtocolString("minecraft")
                        writeProtocolString("core")
                        writeProtocolString(version.label)
                    }, null))
                    output.flush()
                    val knownPacks = readPacket(input, null)
                    assertEquals(7, knownPacks.readVarInt())
                    assertEquals(1, knownPacks.readVarInt())
                    assertEquals("minecraft", knownPacks.readProtocolString())
                    assertEquals("core", knownPacks.readProtocolString())
                    assertEquals(version.label, knownPacks.readProtocolString())

                    output.write(frame(packet { writeVarInt(3) }, null))
                    output.flush()
                    assertEquals(3, readPacket(input, null).readVarInt())

                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x7C else 0x79); writeByte(8); writeUTF("Welcome before UI"); writeBoolean(false)
                    }, null))
                    output.write(frame(packet { writeVarInt(if (latest) 0x2D else 0x2C); writeLong(987_654_321L) }, null))
                    output.flush()
                    val keepAlive = readPacket(input, null)
                    assertEquals(0x1C, keepAlive.readVarInt())
                    assertEquals(987_654_321L, keepAlive.readLong())
                    // No visibility command is sent at protocol login. Only after
                    // world readiness and an API-backed bridge confirmation.
                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x49 else 0x48)
                        writeVarInt(42)
                        writeDouble(1.0); writeDouble(64.0); writeDouble(2.0)
                        repeat(3) { writeDouble(0.0) }
                        writeFloat(0f); writeFloat(0f)
                        writeInt(0)
                    }, null))
                    output.flush()
                    val confirmation = readPacket(input, null)
                    assertEquals(0, confirmation.readVarInt())
                    assertEquals(42, confirmation.readVarInt())
                    if (latest) {
                        assertEquals(1.0, confirmation.readDouble(), 0.0)
                        assertEquals(64.0, confirmation.readDouble(), 0.0)
                        assertEquals(2.0, confirmation.readDouble(), 0.0)
                        assertEquals(0f, confirmation.readFloat(), 0f)
                        assertEquals(0f, confirmation.readFloat(), 0f)
                    }
                    assertEquals(0, confirmation.available())
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
                    output.write(frame(packet { writeVarInt(if (latest) 0x27 else 0x26); writeByte(3); writeFloat(3f) }, null))
                    output.flush()

                }
            }
            val connection = ModernOfflineMinecraftConnection(2_000)
            val profile = ServerProfile(
                "test", "Test", "127.0.0.1:${serverSocket.localPort}", false, 0, 0, null, minecraftVersion = version,
            )

            val session = connection.connect(profile, "OfflineUser")

            assertEquals(version.protocol, session.protocolVersion)
            assertEquals("OfflineUser", session.username)
            withTimeout(5_000) { server.await() }
            assertEquals("Welcome before UI", connection.chatMessages.value.first().content)
            connection.disconnect()
        }
    }
}


