package pl.syntaxdevteam.craftconnect.protocol.legacy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.protocol.JoinVisibilityCommands
import pl.syntaxdevteam.craftconnect.protocol.ConnectedSession
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnection
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException

/** Minimal Minecraft Java 1.8.x (protocol 47) offline-mode client. */
class LegacyOfflineMinecraftConnection(
    private val connectTimeoutMillis: Int = 15_000,
) : MinecraftConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val visibilityCommands = JoinVisibilityCommands(scope, ::sendCommand)
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
        val endpoint = ServerEndpoint.parse(server.address)
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
            sendPacket(packet {
                writeVarInt(LOGIN_START_PACKET)
                writeProtocolString(username)
            })
            val session = awaitLoginSuccess()
            connectedSocket.soTimeout = 0
            sendClientIdentity()
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

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeResources()
    }

    override suspend fun sendChat(message: String) = withContext(Dispatchers.IO) {
        require(message.length <= 100) { "Minecraft 1.8 chat is limited to 100 characters" }
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CHAT_PACKET)
            writeProtocolString(message)
        })
    }

    override suspend fun sendCommand(command: String) {
        sendChat(if (command.startsWith('/')) command else "/$command")
    }

    private fun sendHandshake(endpoint: ServerEndpoint) {
        sendPacket(packet {
            writeVarInt(HANDSHAKE_PACKET)
            writeVarInt(PROTOCOL_VERSION)
            writeProtocolString(endpoint.host)
            writeShort(endpoint.port)
            writeVarInt(LOGIN_STATE)
        })
    }

    private fun awaitLoginSuccess(): ConnectedSession {
        while (open.get()) {
            val packetInput = readPacket(requireInput(), compressionThreshold)
            when (val packetId = packetInput.readVarInt()) {
                LOGIN_DISCONNECT_PACKET -> {
                    val reason = packetInput.readProtocolString(MAX_JSON_LENGTH).minecraftText()
                    throw MinecraftConnectionException.Authentication("login_rejected", reason)
                }
                LOGIN_SUCCESS_PACKET -> return ConnectedSession(
                    protocolVersion = PROTOCOL_VERSION,
                    uuid = packetInput.readProtocolString(64),
                    username = packetInput.readProtocolString(16),
                )
                SET_COMPRESSION_PACKET -> compressionThreshold = packetInput.readVarInt().also {
                    if (it < 0) throw MinecraftConnectionException.Protocol("invalid_compression_threshold")
                }
                else -> throw MinecraftConnectionException.Protocol("unexpected_login_packet_$packetId")
            }
        }
        throw MinecraftConnectionException.Network("connection_closed_during_login")
    }

    private fun playLoop() {
        try {
            while (open.get()) {
                val packetInput = readPacket(requireInput(), compressionThreshold)
                when (packetInput.readVarInt()) {
                    CLIENTBOUND_JOIN_GAME_PACKET -> {
                        packetInput.readInt()
                        visibilityCommands.onGameMode(packetInput.readUnsignedByte() and 0x07)
                    }
                    CLIENTBOUND_GAME_STATE_PACKET -> {
                        val reason = packetInput.readUnsignedByte()
                        val value = packetInput.readFloat()
                        if (reason == 3) visibilityCommands.onGameMode(value.toInt())
                    }
                    CLIENTBOUND_KEEP_ALIVE_PACKET -> respondToKeepAlive(packetInput.readVarInt())
                    CLIENTBOUND_POSITION_PACKET -> respondToPosition(packetInput)
                    CLIENTBOUND_DISCONNECT_PACKET -> {
                        packetInput.readProtocolString(MAX_JSON_LENGTH)
                        closeResources()
                    }
                }
            }
        } catch (_: Exception) {
            closeResources()
        }
    }

    private fun respondToKeepAlive(id: Int) {
        sendPacket(packet {
            writeVarInt(SERVERBOUND_KEEP_ALIVE_PACKET)
            writeVarInt(id)
        })
    }

    private fun sendClientIdentity() {
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CLIENT_SETTINGS_PACKET)
            writeProtocolString("en_US")
            writeByte(2)
            writeByte(0)
            writeBoolean(true)
            writeByte(0x7F)
        })
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CUSTOM_PAYLOAD_PACKET)
            writeProtocolString("MC|Brand")
            writeProtocolString("CraftConnect")
        })
    }

    private fun respondToPosition(input: DataInputStream) {
        val receivedX = input.readDouble()
        val receivedY = input.readDouble()
        val receivedZ = input.readDouble()
        val receivedYaw = input.readFloat()
        val receivedPitch = input.readFloat()
        val flags = input.readUnsignedByte()
        x = if (flags and RELATIVE_X != 0) x + receivedX else receivedX
        y = if (flags and RELATIVE_Y != 0) y + receivedY else receivedY
        z = if (flags and RELATIVE_Z != 0) z + receivedZ else receivedZ
        yaw = if (flags and RELATIVE_YAW != 0) yaw + receivedYaw else receivedYaw
        pitch = if (flags and RELATIVE_PITCH != 0) pitch + receivedPitch else receivedPitch
        sendPacket(packet {
            writeVarInt(SERVERBOUND_POSITION_LOOK_PACKET)
            writeDouble(x)
            writeDouble(y)
            writeDouble(z)
            writeFloat(yaw)
            writeFloat(pitch)
            writeBoolean(true)
        })
        visibilityCommands.onWorldReady()
    }

    private fun sendPacket(payload: ByteArray) {
        check(open.get()) { "Connection is not open" }
        synchronized(writeLock) {
            requireOutput().apply {
                write(frame(payload, compressionThreshold))
                flush()
            }
        }
    }

    private fun closeResources() {
        if (!open.getAndSet(false)) return
        visibilityCommands.reset()
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
        if (!USERNAME.matches(username)) {
            throw MinecraftConnectionException.Authentication("invalid_offline_username")
        }
    }

    private data class ServerEndpoint(val host: String, val port: Int) {
        companion object {
            fun parse(address: String): ServerEndpoint {
                val value = address.trim()
                if (value.startsWith("[") && value.contains("]")) {
                    val closingBracket = value.indexOf(']')
                    val host = value.substring(1, closingBracket)
                    val port = value.substring(closingBracket + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_PORT
                    return ServerEndpoint(host, port)
                }
                val separator = value.lastIndexOf(':')
                val explicitPort = separator > 0 && value.indexOf(':') == separator
                val host = if (explicitPort) value.substring(0, separator) else value
                val port = if (explicitPort) value.substring(separator + 1).toIntOrNull() else DEFAULT_PORT
                require(host.isNotBlank() && port != null && port in 1..65_535) { "Invalid server address" }
                return ServerEndpoint(host, port)
            }
        }
    }

    private companion object {
        const val PROTOCOL_VERSION = 47
        const val DEFAULT_PORT = 25_565
        const val MAX_JSON_LENGTH = 262_144
        const val HANDSHAKE_PACKET = 0x00
        const val LOGIN_STATE = 2
        const val LOGIN_START_PACKET = 0x00
        const val LOGIN_DISCONNECT_PACKET = 0x00
        const val LOGIN_SUCCESS_PACKET = 0x02
        const val SET_COMPRESSION_PACKET = 0x03
        const val CLIENTBOUND_JOIN_GAME_PACKET = 0x01
        const val CLIENTBOUND_GAME_STATE_PACKET = 0x2B
        const val CLIENTBOUND_KEEP_ALIVE_PACKET = 0x00
        const val CLIENTBOUND_POSITION_PACKET = 0x08
        const val CLIENTBOUND_DISCONNECT_PACKET = 0x40
        const val SERVERBOUND_KEEP_ALIVE_PACKET = 0x00
        const val SERVERBOUND_CHAT_PACKET = 0x01
        const val SERVERBOUND_POSITION_LOOK_PACKET = 0x06
        const val SERVERBOUND_CLIENT_SETTINGS_PACKET = 0x15
        const val SERVERBOUND_CUSTOM_PAYLOAD_PACKET = 0x17
        const val RELATIVE_X = 0x01
        const val RELATIVE_Y = 0x02
        const val RELATIVE_Z = 0x04
        const val RELATIVE_YAW = 0x08
        const val RELATIVE_PITCH = 0x10
        val USERNAME = Regex("[A-Za-z0-9_]{3,16}")
    }
}

private fun String.minecraftText(): String {
    val text = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(this)?.groupValues?.get(1)
        ?: return take(512)
    return text
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .take(512)
}

