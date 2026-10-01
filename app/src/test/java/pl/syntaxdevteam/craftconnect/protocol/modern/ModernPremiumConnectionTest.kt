package pl.syntaxdevteam.craftconnect.protocol.modern

import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.math.BigInteger
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity
import pl.syntaxdevteam.craftconnect.domain.model.*
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException
import pl.syntaxdevteam.craftconnect.protocol.legacy.*

class ModernPremiumConnectionTest {
    private val uuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
    private val identity = MinecraftIdentity("PremiumPlayer", uuid.toString().replace("-", ""), "never-send-to-server")

    @Test fun `encrypted login and compressed keepalive work on all supported versions`() = runBlocking {
        for (version in MinecraftVersion.entries) verify(version)
    }

    private suspend fun verify(version: MinecraftVersion) = coroutineScope {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val challenge = byteArrayOf(10, 20, 30, 40)
        var joinedHash: String? = null
        val ready = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        ServerSocket(0).use { listener ->
            val peer = async(Dispatchers.IO) {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val rawInput = socket.getInputStream(); val rawOutput = socket.getOutputStream()
                    readPacket(rawInput, null)
                    val start = readPacket(rawInput, null)
                    assertEquals(0, start.readVarInt()); assertEquals(identity.username, start.readProtocolString())
                    assertEquals(uuid, UUID(start.readLong(), start.readLong()))
                    rawOutput.write(frame(packet {
                        writeVarInt(1); writeProtocolString("")
                        writeVarInt(pair.public.encoded.size); write(pair.public.encoded)
                        writeVarInt(challenge.size); write(challenge); writeBoolean(true)
                    }, null)); rawOutput.flush()
                    val response = readPacket(rawInput, null)
                    assertEquals(1, response.readVarInt())
                    fun decrypt(): ByteArray {
                        val bytes = ByteArray(response.readVarInt()).also { response.readFully(it) }
                        return Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, pair.private); doFinal(bytes) }
                    }
                    val secret = decrypt()
                    assertArrayEquals(challenge, decrypt())
                    assertEquals(BigInteger(MessageDigest.getInstance("SHA-1").run {
                        update(secret); digest(pair.public.encoded)
                    }).toString(16), joinedHash)
                    fun cipher(mode: Int) = Cipher.getInstance("AES/CFB8/NoPadding").apply {
                        init(mode, SecretKeySpec(secret, "AES"), IvParameterSpec(secret))
                    }
                    val input = CipherInputStream(rawInput, cipher(Cipher.DECRYPT_MODE))
                    val output = CipherOutputStream(rawOutput, cipher(Cipher.ENCRYPT_MODE))
                    output.write(frame(packet { writeVarInt(3); writeVarInt(1) }, null))
                    output.write(frame(packet {
                        writeVarInt(2); writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits)
                        writeProtocolString(identity.username); writeVarInt(0)
                        if (version.protocol >= 776) { writeLong(0); writeLong(0) }
                    }, 1)); output.flush()
                    assertEquals(3, readPacket(input, 1).readVarInt())
                    assertEquals(0, readPacket(input, 1).readVarInt())
                    assertEquals(2, readPacket(input, 1).readVarInt())
                    output.write(frame(packet { writeVarInt(3) }, 1)); output.flush()
                    assertEquals(3, readPacket(input, 1).readVarInt())
                    output.write(frame(packet { writeVarInt(if (version == MinecraftVersion.JAVA_26_3) 0x2D else 0x2C); writeLong(54321) }, 1)); output.flush()
                    val pong = readPacket(input, 1)
                    assertEquals(0x1C, pong.readVarInt()); assertEquals(54321L, pong.readLong())
                    ready.complete(Unit)
                    done.await()
                }
            }
            val connection = ModernOfflineMinecraftConnection(joinSession = { account, hash ->
                assertSame(identity, account); joinedHash = hash
            })
            try {
                val session = connection.connect(server(listener.localPort, version), identity)
                assertEquals(uuid.toString(), session.uuid)
                withTimeout(5000) { ready.await() }
            } finally { done.complete(Unit); connection.disconnect() }
            peer.await()
        }
    }

    @Test fun `premium profile cannot silently fall back to offline login`() = runBlocking {
        ServerSocket(0).use { listener ->
            val peer = async(Dispatchers.IO) {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    readPacket(socket.getInputStream(), null); readPacket(socket.getInputStream(), null)
                    socket.getOutputStream().write(frame(packet {
                        writeVarInt(2); writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits)
                        writeProtocolString(identity.username); writeVarInt(0)
                    }, null))
                    socket.getOutputStream().flush()
                }
            }
            try { ModernOfflineMinecraftConnection().connect(server(listener.localPort, MinecraftVersion.JAVA_26_1), identity); fail() }
            catch (failure: MinecraftConnectionException.Authentication) { assertEquals("premium_server_identity_mismatch", failure.diagnosticCode) }
            peer.await()
        }
    }

    private fun server(port: Int, version: MinecraftVersion) = ServerProfile("test", "test", "127.0.0.1:$port", true, 0, 20, 0, minecraftVersion = version)
}
