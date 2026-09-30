package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.protocol.ConnectedSession
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnection
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException
import pl.syntaxdevteam.craftconnect.protocol.legacy.ProtocolCodecException
import pl.syntaxdevteam.craftconnect.protocol.legacy.frame
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.readPacket
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

/** Headless offline-mode adapter for Minecraft Java 26.1 (protocol 775). */
class ModernOfflineMinecraftConnection(
    private val connectTimeoutMillis: Int = 15_000,
) : MinecraftConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    private val open = AtomicBoolean(false)
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var compressionThreshold: Int? = null
    private var readerJob: Job? = null
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    private var yaw = 0f
    private var pitch = 0f

    override suspend fun connect(server: ServerProfile, username: String): ConnectedSession = withContext(Dispatchers.IO) {
        validateUsername(username)
        val endpoint = Endpoint.parse(server.address)
        try {
            val connectedSocket = Socket().apply {
                tcpNoDelay = true
                soTimeout = connectTimeoutMillis
                connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMillis)
            }
            socket = connectedSocket
            input = BufferedInputStream(connectedSocket.getInputStream())
            output = BufferedOutputStream(connectedSocket.getOutputStream())
            open.set(true)

            sendHandshake(endpoint)
            sendLoginStart(username)
            val session = awaitLoginSuccess()
            sendPacket(packet { writeVarInt(LOGIN_ACKNOWLEDGED) })
            sendClientSettings(CONFIGURATION_CLIENT_SETTINGS)
            awaitConfiguration()
            connectedSocket.soTimeout = 0
            readerJob = scope.launch { playLoop() }
            session
        } catch (failure: MinecraftConnectionException) {
            closeResources()
            throw failure
        } catch (failure: ProtocolCodecException) {
            closeResources()
            throw MinecraftConnectionException.Protocol(failure.code, failure)
        } catch (failure: Exception) {
            closeResources()
            throw MinecraftConnectionException.Network("connection_failed", failure)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { closeResources() }

    override suspend fun sendChat(message: String) = withContext(Dispatchers.IO) {
        require(message.length <= 256) { "Chat is limited to 256 characters" }
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CHAT)
            writeProtocolString(message)
            writeLong(System.currentTimeMillis())
            writeLong(0L)
            writeBoolean(false)
            writeVarInt(0)
            write(byteArrayOf(0, 0, 0))
            writeByte(0)
        })
    }

    override suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        sendPacket(packet {
            writeVarInt(SERVERBOUND_COMMAND)
            writeProtocolString(command.removePrefix("/"))
        })
    }

    private fun sendHandshake(endpoint: Endpoint) = sendPacket(packet {
        writeVarInt(HANDSHAKE)
        writeVarInt(PROTOCOL_VERSION)
        writeProtocolString(endpoint.host)
        writeShort(endpoint.port)
        writeVarInt(LOGIN_STATE)
    })

    private fun sendLoginStart(username: String) = sendPacket(packet {
        writeVarInt(LOGIN_START)
        writeProtocolString(username)
        writeUuid(offlineUuid(username))
    })

    private fun awaitLoginSuccess(): ConnectedSession {
        while (open.get()) {
            val packetInput = readPacket(requireInput(), compressionThreshold)
            when (val packetId = packetInput.readVarInt()) {
                LOGIN_DISCONNECT -> throw MinecraftConnectionException.Authentication(
                    "login_rejected",
                    packetInput.readProtocolString(MAX_TEXT_LENGTH).minecraftText(),
                )
                LOGIN_SUCCESS -> {
                    val uuid = packetInput.readUuid()
                    val username = packetInput.readProtocolString(16)
                    repeat(packetInput.readVarInt()) {
                        packetInput.readProtocolString()
                        packetInput.readProtocolString()
                        if (packetInput.readBoolean()) packetInput.readProtocolString()
                    }
                    return ConnectedSession(PROTOCOL_VERSION, username, uuid.toString())
                }
                SET_COMPRESSION -> compressionThreshold = packetInput.readVarInt().also {
                    if (it < 0) throw MinecraftConnectionException.Protocol("invalid_compression_threshold")
                }
                LOGIN_PLUGIN_REQUEST -> rejectLoginPlugin(packetInput)
                else -> throw MinecraftConnectionException.Protocol("unexpected_login_packet_$packetId")
            }
        }
        throw MinecraftConnectionException.Network("connection_closed_during_login")
    }

    private fun rejectLoginPlugin(input: DataInputStream) {
        val messageId = input.readVarInt()
        sendPacket(packet {
            writeVarInt(LOGIN_PLUGIN_RESPONSE)
            writeVarInt(messageId)
            writeBoolean(false)
        })
    }

    private fun awaitConfiguration() {
        while (open.get()) {
            val packetInput = readPacket(requireInput(), compressionThreshold)
            when (packetInput.readVarInt()) {
                CONFIGURATION_DISCONNECT -> throw MinecraftConnectionException.Authentication(
                    "configuration_rejected",
                    "Server disconnected during configuration",
                )
                CONFIGURATION_FINISH -> {
                    sendPacket(packet { writeVarInt(SERVERBOUND_CONFIGURATION_FINISH) })
                    return
                }
                CONFIGURATION_KEEP_ALIVE -> respondLong(SERVERBOUND_CONFIGURATION_KEEP_ALIVE, packetInput.readLong())
                CONFIGURATION_PING -> respondInt(SERVERBOUND_CONFIGURATION_PONG, packetInput.readInt())
                CONFIGURATION_KNOWN_PACKS -> respondKnownPacks(packetInput)
                CONFIGURATION_CODE_OF_CONDUCT -> sendPacket(packet { writeVarInt(SERVERBOUND_ACCEPT_CODE_OF_CONDUCT) })
            }
        }
        throw MinecraftConnectionException.Network("connection_closed_during_configuration")
    }

    private fun respondKnownPacks(input: DataInputStream) {
        val packs = List(input.readVarInt()) {
            Triple(input.readProtocolString(), input.readProtocolString(), input.readProtocolString())
        }
        sendPacket(packet {
            writeVarInt(SERVERBOUND_KNOWN_PACKS)
            writeVarInt(packs.size)
            packs.forEach { (namespace, id, version) ->
                writeProtocolString(namespace)
                writeProtocolString(id)
                writeProtocolString(version)
            }
        })
    }

    private fun playLoop() {
        try {
            while (open.get()) {
                val packetInput = readPacket(requireInput(), compressionThreshold)
                when (packetInput.readVarInt()) {
                    CLIENTBOUND_KEEP_ALIVE -> respondLong(SERVERBOUND_KEEP_ALIVE, packetInput.readLong())
                    CLIENTBOUND_POSITION -> acknowledgePosition(packetInput)
                    CLIENTBOUND_DISCONNECT -> closeResources()
                }
            }
        } catch (_: Exception) {
            closeResources()
        }
    }

    private fun acknowledgePosition(input: DataInputStream) {
        val teleportId = input.readVarInt()
        val receivedX = input.readDouble()
        val receivedY = input.readDouble()
        val receivedZ = input.readDouble()
        input.readDouble()
        input.readDouble()
        input.readDouble()
        val receivedYaw = input.readFloat()
        val receivedPitch = input.readFloat()
        val flags = input.readInt()
        x = if (flags and RELATIVE_X != 0) x + receivedX else receivedX
        y = if (flags and RELATIVE_Y != 0) y + receivedY else receivedY
        z = if (flags and RELATIVE_Z != 0) z + receivedZ else receivedZ
        yaw = if (flags and RELATIVE_YAW != 0) yaw + receivedYaw else receivedYaw
        pitch = if (flags and RELATIVE_PITCH != 0) pitch + receivedPitch else receivedPitch
        sendPacket(packet { writeVarInt(SERVERBOUND_TELEPORT_CONFIRM); writeVarInt(teleportId) })
        sendPacket(packet {
            writeVarInt(SERVERBOUND_POSITION_LOOK)
            writeDouble(x); writeDouble(y); writeDouble(z)
            writeFloat(yaw); writeFloat(pitch)
            writeByte(1)
        })
    }

    private fun sendClientSettings(packetId: Int) = sendPacket(packet {
        writeVarInt(packetId)
        writeProtocolString("en_US")
        writeByte(2)
        writeVarInt(0)
        writeBoolean(true)
        writeByte(0x7F)
        writeVarInt(1)
        writeBoolean(false)
        writeBoolean(true)
        writeVarInt(2)
    })

    private fun respondLong(packetId: Int, value: Long) = sendPacket(packet { writeVarInt(packetId); writeLong(value) })
    private fun respondInt(packetId: Int, value: Int) = sendPacket(packet { writeVarInt(packetId); writeInt(value) })

    private fun sendPacket(payload: ByteArray) {
        check(open.get()) { "Connection is not open" }
        synchronized(writeLock) {
            requireOutput().apply { write(frame(payload, compressionThreshold)); flush() }
        }
    }

    private fun closeResources() {
        if (!open.getAndSet(false)) return
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        compressionThreshold = null
        x = 0.0
        y = 0.0
        z = 0.0
        yaw = 0f
        pitch = 0f
    }

    private fun requireInput() = checkNotNull(input) { "Connection input is unavailable" }
    private fun requireOutput() = checkNotNull(output) { "Connection output is unavailable" }

    private fun validateUsername(username: String) {
        if (!USERNAME.matches(username)) throw MinecraftConnectionException.Authentication("invalid_offline_username")
    }

    private data class Endpoint(val host: String, val port: Int) {
        companion object {
            fun parse(address: String): Endpoint {
                val value = address.trim()
                val separator = value.lastIndexOf(':')
                val explicitPort = separator > 0 && value.indexOf(':') == separator
                val host = if (explicitPort) value.substring(0, separator) else value
                val port = if (explicitPort) value.substring(separator + 1).toIntOrNull() else DEFAULT_PORT
                require(host.isNotBlank() && port != null && port in 1..65_535) { "Invalid server address" }
                return Endpoint(host, port)
            }
        }
    }

    private companion object {
        const val PROTOCOL_VERSION = 775
        const val DEFAULT_PORT = 25_565
        const val MAX_TEXT_LENGTH = 262_144
        const val HANDSHAKE = 0x00
        const val LOGIN_STATE = 2
        const val LOGIN_START = 0x00
        const val LOGIN_DISCONNECT = 0x00
        const val LOGIN_SUCCESS = 0x02
        const val SET_COMPRESSION = 0x03
        const val LOGIN_PLUGIN_REQUEST = 0x04
        const val LOGIN_PLUGIN_RESPONSE = 0x02
        const val LOGIN_ACKNOWLEDGED = 0x03
        const val CONFIGURATION_CLIENT_SETTINGS = 0x00
        const val CONFIGURATION_DISCONNECT = 0x02
        const val CONFIGURATION_FINISH = 0x03
        const val CONFIGURATION_KEEP_ALIVE = 0x04
        const val CONFIGURATION_PING = 0x05
        const val CONFIGURATION_KNOWN_PACKS = 0x0E
        const val CONFIGURATION_CODE_OF_CONDUCT = 0x13
        const val SERVERBOUND_CONFIGURATION_FINISH = 0x03
        const val SERVERBOUND_CONFIGURATION_KEEP_ALIVE = 0x04
        const val SERVERBOUND_CONFIGURATION_PONG = 0x05
        const val SERVERBOUND_KNOWN_PACKS = 0x07
        const val SERVERBOUND_ACCEPT_CODE_OF_CONDUCT = 0x09
        const val CLIENTBOUND_KEEP_ALIVE = 0x2C
        const val CLIENTBOUND_DISCONNECT = 0x20
        const val CLIENTBOUND_POSITION = 0x48
        const val SERVERBOUND_TELEPORT_CONFIRM = 0x00
        const val SERVERBOUND_COMMAND = 0x07
        const val SERVERBOUND_CHAT = 0x09
        const val SERVERBOUND_KEEP_ALIVE = 0x1C
        const val SERVERBOUND_POSITION_LOOK = 0x1F
        const val RELATIVE_X = 0x01
        const val RELATIVE_Y = 0x02
        const val RELATIVE_Z = 0x04
        const val RELATIVE_YAW = 0x08
        const val RELATIVE_PITCH = 0x10
        val USERNAME = Regex("[A-Za-z0-9_]{3,16}")
    }
}

private fun offlineUuid(username: String): UUID =
    UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))

private fun DataInputStream.readUuid(): UUID = UUID(readLong(), readLong())
private fun java.io.DataOutputStream.writeUuid(uuid: UUID) { writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits) }

private fun String.minecraftText(): String =
    (Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(this)?.groupValues?.get(1) ?: take(512))
        .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").take(512)
