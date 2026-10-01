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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent
import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
import pl.syntaxdevteam.craftconnect.protocol.JoinVisibilityCommands
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
    private val addressResolver = MinecraftServerAddressResolver()
    private val chatHistory = pl.syntaxdevteam.craftconnect.protocol.chat.ChatHistory()
    override val chatMessages = chatHistory.messages
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val visibilityCommands = JoinVisibilityCommands(scope, ::sendCommand, sendAuthenticationRequest = ::sendAuthenticationSubscription)
    private val writeLock = Any()
    private val open = AtomicBoolean(false)
    private val mutableDialogEvents = MutableSharedFlow<ServerDialogEvent>(replay = 1)
    override val dialogEvents = mutableDialogEvents.asSharedFlow()
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
        chatHistory.clear()
        validateUsername(username)
        val endpoint = addressResolver.resolve(server.address)
        try {
            val connectedSocket = openSocket(endpoint)
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

    private fun openSocket(endpoint: MinecraftServerEndpoint): Socket {
        val remoteAddresses = endpoint.connectionAddresses
            .map { InetSocketAddress(it, endpoint.connectionPort) }
            .ifEmpty { listOf(InetSocketAddress(endpoint.connectionHost, endpoint.connectionPort)) }
        var lastFailure: Exception? = null
        remoteAddresses.forEach { remoteAddress ->
            val candidate = Socket()
            try {
                candidate.tcpNoDelay = true
                candidate.soTimeout = connectTimeoutMillis
                candidate.connect(remoteAddress, connectTimeoutMillis)
                return candidate
            } catch (failure: Exception) {
                runCatching { candidate.close() }
                lastFailure = failure
            }
        }
        throw checkNotNull(lastFailure) { "No server addresses available" }
    }

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

    private suspend fun sendAuthenticationSubscription(payload: ByteArray) = withContext(Dispatchers.IO) {
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CUSTOM_PAYLOAD)
            writeProtocolString("minecraft:register")
            write(AuthenticationBridgeProtocol.CHANNEL.toByteArray(Charsets.UTF_8))
        })
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CUSTOM_PAYLOAD)
            writeProtocolString(AuthenticationBridgeProtocol.CHANNEL)
            write(payload)
        })
    }

    override suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        sendPacket(packet {
            writeVarInt(SERVERBOUND_COMMAND)
            writeProtocolString(command.removePrefix("/"))
        })
    }

    override suspend fun submitDialog(actionId: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        require(RESOURCE_LOCATION.matches(actionId)) { "Invalid dialog action identifier" }
        require(values.size <= 32) { "Too many dialog fields" }
        values.forEach { (key, value) ->
            require(key.length <= 128 && value.length <= 4_096) { "Dialog field is too long" }
        }
        sendPacket(packet {
            writeVarInt(SERVERBOUND_CUSTOM_CLICK_ACTION)
            writeProtocolString(actionId)
            write(encodeLengthPrefixedStringCompound(values))
        })
        mutableDialogEvents.emit(ServerDialogEvent.Clear)
    }

    private fun sendHandshake(endpoint: MinecraftServerEndpoint) = sendPacket(packet {
        writeVarInt(HANDSHAKE)
        writeVarInt(PROTOCOL_VERSION)
        writeProtocolString(endpoint.handshakeHost)
        writeShort(endpoint.handshakePort)
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
                    0x79 -> runCatching { packetInput.readSystemChat() }.getOrNull()?.let(chatHistory::append)
                    0x21 -> runCatching { packetInput.readProfilelessChat() }.getOrNull()?.let(chatHistory::append)
                    0x41 -> {
                        val chat = runCatching { packetInput.readPlayerChat() }.getOrNull()
                        if (chat != null) {
                            chatHistory.append(chat.text)
                            if (chat.signed) sendPacket(packet {
                                writeVarInt(0x06)
                                writeVarInt(1)
                            })
                        }
                    }
                    CLIENTBOUND_CUSTOM_PAYLOAD -> {
                        val channel = packetInput.readProtocolString()
                        if (channel == AuthenticationBridgeProtocol.CHANNEL) {
                            val size = packetInput.available()
                            if (size <= 256) {
                                val payload = ByteArray(size).also(packetInput::readFully)
                                visibilityCommands.onBridgeMessage(payload)
                            }
                        }
                    }
                    CLIENTBOUND_JOIN_GAME -> readInitialGameMode(packetInput)
                    CLIENTBOUND_GAME_STATE -> {
                        val reason = packetInput.readUnsignedByte()
                        val value = packetInput.readFloat()
                        if (reason == 3) visibilityCommands.onGameMode(value.toInt())
                    }
                    CLIENTBOUND_KEEP_ALIVE -> respondLong(SERVERBOUND_KEEP_ALIVE, packetInput.readLong())
                    CLIENTBOUND_POSITION -> acknowledgePosition(packetInput)
                    CLIENTBOUND_DISCONNECT -> closeResources()
                    CLIENTBOUND_CLEAR_DIALOG -> mutableDialogEvents.tryEmit(ServerDialogEvent.Clear)
                    CLIENTBOUND_SHOW_DIALOG -> runCatching { packetInput.readServerDialog() }
                        .getOrNull()
                        ?.let { mutableDialogEvents.tryEmit(ServerDialogEvent.Show(it)) }
                }
            }
        } catch (_: Exception) {
            closeResources()
        }
    }

    private fun readInitialGameMode(input: DataInputStream) {
        input.readInt()
        input.readBoolean()
        val worlds = input.readVarInt()
        require(worlds in 0..1_024) { "Invalid world count" }
        repeat(worlds) { input.readProtocolString() }
        repeat(3) { input.readVarInt() }
        repeat(3) { input.readBoolean() }
        input.readVarInt()
        input.readProtocolString()
        input.readLong()
        visibilityCommands.onGameMode(input.readUnsignedByte())
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
        visibilityCommands.onWorldReady()
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
        mutableDialogEvents.tryEmit(ServerDialogEvent.Clear)
    }

    private fun requireInput() = checkNotNull(input) { "Connection input is unavailable" }
    private fun requireOutput() = checkNotNull(output) { "Connection output is unavailable" }

    private fun validateUsername(username: String) {
        if (!USERNAME.matches(username)) throw MinecraftConnectionException.Authentication("invalid_offline_username")
    }

    private companion object {
        const val PROTOCOL_VERSION = 775
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
        const val CLIENTBOUND_JOIN_GAME = 0x31
        const val CLIENTBOUND_CUSTOM_PAYLOAD = 0x18
        const val SERVERBOUND_CUSTOM_PAYLOAD = 0x16
        const val CLIENTBOUND_GAME_STATE = 0x26
        const val CLIENTBOUND_KEEP_ALIVE = 0x2C
        const val CLIENTBOUND_DISCONNECT = 0x20
        const val CLIENTBOUND_POSITION = 0x48
        const val CLIENTBOUND_CLEAR_DIALOG = 0x8B
        const val CLIENTBOUND_SHOW_DIALOG = 0x8C
        const val SERVERBOUND_TELEPORT_CONFIRM = 0x00
        const val SERVERBOUND_COMMAND = 0x07
        const val SERVERBOUND_CHAT = 0x09
        const val SERVERBOUND_KEEP_ALIVE = 0x1C
        const val SERVERBOUND_POSITION_LOOK = 0x1F
        const val SERVERBOUND_CUSTOM_CLICK_ACTION = 0x44
        const val RELATIVE_X = 0x01
        const val RELATIVE_Y = 0x02
        const val RELATIVE_Z = 0x04
        const val RELATIVE_YAW = 0x08
        const val RELATIVE_PITCH = 0x10
        val USERNAME = Regex("[A-Za-z0-9_]{3,16}")
        val RESOURCE_LOCATION = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    }
}

private fun offlineUuid(username: String): UUID =
    UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))

private fun DataInputStream.readUuid(): UUID = UUID(readLong(), readLong())
private fun java.io.DataOutputStream.writeUuid(uuid: UUID) { writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits) }

private fun String.minecraftText(): String =
    (Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(this)?.groupValues?.get(1) ?: take(512))
        .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").take(512)
