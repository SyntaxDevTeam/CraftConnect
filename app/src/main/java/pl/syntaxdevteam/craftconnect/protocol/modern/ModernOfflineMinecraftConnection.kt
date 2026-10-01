package pl.syntaxdevteam.craftconnect.protocol.modern

import pl.syntaxdevteam.craftconnect.domain.auth.AuthenticationException
import pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity
import pl.syntaxdevteam.craftconnect.protocol.chat.ChatHistory

import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import java.io.ByteArrayInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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

/** Headless offline/online-mode adapter for Minecraft Java 26.1–26.3. */
class ModernOfflineMinecraftConnection(
    private val connectTimeoutMillis: Int = 15_000,
    private val joinSession: suspend (MinecraftIdentity, String) -> Unit = { _, _ ->
        throw MinecraftConnectionException.Authentication("premium_not_configured")
    },
) : MinecraftConnection {
    private var version = MinecraftVersion.JAVA_26_1
    private var connectionStage = "resolve"
    private val packetTrace = PacketTrace()
    private val commonRequests = CommonServerRequests(::sendPacket)
    private val addressResolver = MinecraftServerAddressResolver()
    private val chatTypes = ServerChatTypes()
    private val playerList = ServerPlayerList()
    override val players = playerList.players
    private val chatHistory = ChatHistory()
    override val chatMessages = chatHistory.messages
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val visibilityCommands = JoinVisibilityCommands(scope, ::sendCommand, sendAuthenticationRequest = ::sendAuthenticationSubscription)
    private val writeLock = Any()
    private val open = AtomicBoolean(false)
    private val mutableDialogEvents = MutableSharedFlow<ServerDialogEvent>(replay = 1)
    override val dialogEvents = mutableDialogEvents.asSharedFlow()
    private val mutableConnectionFailures = MutableStateFlow<MinecraftConnectionException?>(null)
    override val connectionFailures = mutableConnectionFailures.filterNotNull()
    private var socket: Socket? = null
    private var input: java.io.InputStream? = null
    private var output: java.io.OutputStream? = null
    private var compressionThreshold: Int? = null
    private var readerJob: Job? = null
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    private var yaw = 0f
    private var pitch = 0f

    override suspend fun connect(server: ServerProfile, username: String): ConnectedSession = connect(server, username, null)

    override suspend fun connect(server: ServerProfile, identity: MinecraftIdentity): ConnectedSession =
        connect(server, identity.username, identity)

    private suspend fun connect(server: ServerProfile, username: String, identity: MinecraftIdentity?): ConnectedSession = withContext(Dispatchers.IO) {
        mutableConnectionFailures.value = null
        version = server.minecraftVersion
        connectionStage = "resolve"
        packetTrace.clear()
        commonRequests.clear()
        chatHistory.clear()
        chatTypes.clear()
        playerList.clear()
        validateUsername(username)
        try {
            val endpoint = addressResolver.resolve(server.address)
            connectionStage = "tcp"
            val connectedSocket = openSocket(endpoint)
            socket = connectedSocket
            input = BufferedInputStream(connectedSocket.getInputStream())
            output = BufferedOutputStream(connectedSocket.getOutputStream())
            open.set(true)

            connectionStage = "login"
            sendHandshake(endpoint)
            sendLoginStart(username, identity?.uuid)
            val session = awaitLoginSuccess(identity)
            sendPacket(packet { writeVarInt(LOGIN_ACKNOWLEDGED) })
            connectionStage = "configuration"
            sendClientSettings(CONFIGURATION_CLIENT_SETTINGS)
            sendClientBrand()
            awaitConfiguration()
            connectionStage = "play"
            connectedSocket.soTimeout = 0
            readerJob = scope.launch { playLoop() }
            session
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            closeResources()
            throw cancelled
        } catch (failure: AuthenticationException) {
            closeResources()
            throw MinecraftConnectionException.Authentication("premium_${failure.problem.name.lowercase()}")
        } catch (failure: MinecraftConnectionException) {
            closeResources()
            throw failure
        } catch (failure: ProtocolCodecException) {
            closeResources()
            throw MinecraftConnectionException.Protocol(failure.code + "_${connectionStage}_p${version.protocol}" + packetTrace.suffix(), failure)
        } catch (failure: Exception) {
            closeResources()
            throw MinecraftConnectionException.Network("connection_${connectionStage}_failed_p${version.protocol}" + packetTrace.suffix(), failure)
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
        writeVarInt(version.protocol)
        writeProtocolString(endpoint.handshakeHost)
        writeShort(endpoint.handshakePort)
        writeVarInt(LOGIN_STATE)
    })

    private fun sendLoginStart(username: String, profileId: String?) = sendPacket(packet {
        writeVarInt(LOGIN_START)
        writeProtocolString(username)
        writeUuid(profileId?.let { uuidFromProfile(it) } ?: offlineUuid(username))
    })

    private suspend fun awaitLoginSuccess(identity: MinecraftIdentity?): ConnectedSession {
        var encrypted = false
        var authenticated = false
        while (open.get()) {
            val packetInput = readPacket(requireInput(), compressionThreshold)
            when (val packetId = packetInput.readVarInt().also { packetTrace.record("i", it) }) {
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
                    if (version.protocol >= 776) packetInput.readUuid() // Login session ID
                    if (identity != null && (!authenticated || uuid != uuidFromProfile(identity.uuid) || username != identity.username)) {
                        throw MinecraftConnectionException.Authentication("premium_server_identity_mismatch")
                    }
                    return ConnectedSession(version.protocol, username, uuid.toString())
                }
                SET_COMPRESSION -> compressionThreshold = packetInput.readVarInt().also {
                    if (it < 0) throw MinecraftConnectionException.Protocol("invalid_compression_threshold")
                }
                LOGIN_PLUGIN_REQUEST -> rejectLoginPlugin(packetInput)
                0x05 -> commonRequests.replyCookie(packetInput, 0x04)
                0x01 -> {
                    if (encrypted) throw MinecraftConnectionException.Protocol("duplicate_encryption_request")
                    val account = identity ?: throw MinecraftConnectionException.Authentication("online_mode_requires_microsoft")
                    val encryption = OnlineLoginEncryption(packetInput)
                    if (!encryption.shouldAuthenticate) throw MinecraftConnectionException.Authentication("premium_requires_online_server")
                    joinSession(account, encryption.serverHash)
                    val secret = encryption.encryptedSecret()
                    val challenge = encryption.encryptedChallenge()
                    sendPacket(packet {
                        writeVarInt(0x01)
                        writeVarInt(secret.size); write(secret)
                        writeVarInt(challenge.size); write(challenge)
                    })
                    // Wrap the existing buffered input: it may already contain encrypted bytes.
                    input = javax.crypto.CipherInputStream(requireInput(), encryption.streamCipher(javax.crypto.Cipher.DECRYPT_MODE))
                    output = javax.crypto.CipherOutputStream(requireOutput(), encryption.streamCipher(javax.crypto.Cipher.ENCRYPT_MODE))
                    encrypted = true
                    authenticated = true
                }
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
            when (packetInput.readVarInt().also { packetTrace.record("i", it) }
                .let { if (version == MinecraftVersion.JAVA_26_3 && it >= 10) it - 1 else it }) {
                0x00 -> commonRequests.replyCookie(packetInput, 0x01)
                0x09 -> commonRequests.replyResourcePack(packetInput, 0x06)
                0x0A -> commonRequests.storeCookie(packetInput)
                0x07 -> chatTypes.readRegistry(packetInput)
                CONFIGURATION_DISCONNECT -> throw MinecraftConnectionException.Authentication(
                    "configuration_rejected",
                    packetInput.readAnonymousNbt().chatText().take(512),
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
        val count = input.readVarInt()
        require(count in 0..1_024) { "Invalid known pack count" }
        repeat(count) {
            input.readProtocolString(); input.readProtocolString(); input.readProtocolString()
        }
        sendPacket(packet {
            writeVarInt(SERVERBOUND_KNOWN_PACKS)
            // This client has no bundled vanilla registries: request explicit registry data.
            writeVarInt(0)
        })
    }

    private fun playLoop() {
        try {
            while (open.get()) {
                val packetInput = readPacket(requireInput(), compressionThreshold)
                when (packetInput.readVarInt().also { packetTrace.record("i", it) }) {
                    0x15 -> commonRequests.replyCookie(packetInput, 0x15)
                    resourcePackId -> commonRequests.replyResourcePack(packetInput, 0x32)
                    storeCookieId -> commonRequests.storeCookie(packetInput)
                    systemChatId -> runCatching { packetInput.readSystemChat(formatted = true) }.getOrNull()?.let(chatHistory::append)
                    0x21 -> runCatching { packetInput.readProfilelessChat(chatTypes, formatted = true) }.getOrNull()?.let(chatHistory::append)
                    playerInfoUpdateId -> playerList.update(packetInput)
                    playerInfoRemoveId -> playerList.remove(packetInput)
                    playerChatId -> {
                        val chat = runCatching { packetInput.readPlayerChat(chatTypes, formatted = true) }.getOrNull()
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
                    joinGameId -> readInitialGameMode(packetInput)
                    gameStateId -> {
                        val reason = packetInput.readUnsignedByte()
                        val value = packetInput.readFloat()
                        if (reason == 3) visibilityCommands.onGameMode(value.toInt())
                    }
                    keepAliveId -> respondLong(SERVERBOUND_KEEP_ALIVE, packetInput.readLong())
                    positionId -> acknowledgePosition(packetInput)
                    CLIENTBOUND_DISCONNECT -> closeResources(
                        MinecraftConnectionException.Authentication(
                            "play_disconnected_p${version.protocol}" + packetTrace.suffix(),
                            packetInput.readAnonymousNbt().chatText().take(512),
                        ),
                    )
                    clearDialogId -> mutableDialogEvents.tryEmit(ServerDialogEvent.Clear)
                    showDialogId -> runCatching { packetInput.readServerDialog() }
                        .getOrNull()
                        ?.let { mutableDialogEvents.tryEmit(ServerDialogEvent.Show(it)) }
                }
            }
        } catch (failure: Exception) {
            if (open.get()) closeResources(
                if (failure is IOException) MinecraftConnectionException.Network(
                    "connection_play_closed_p${version.protocol}" + packetTrace.suffix(), failure,
                ) else MinecraftConnectionException.Protocol(
                    "connection_play_failed_p${version.protocol}" + packetTrace.suffix(), failure,
                ),
            )
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
        visibilityCommands.onGameMode(if (version == MinecraftVersion.JAVA_26_3) input.readVarInt() else input.readUnsignedByte())
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
        sendPacket(packet {
            writeVarInt(SERVERBOUND_TELEPORT_CONFIRM); writeVarInt(teleportId)
            if (version == MinecraftVersion.JAVA_26_3) {
                writeDouble(x); writeDouble(y); writeDouble(z)
                writeFloat(yaw); writeFloat(pitch)
            }
        })
        sendPacket(packet {
            writeVarInt(SERVERBOUND_POSITION_LOOK)
            writeDouble(x); writeDouble(y); writeDouble(z)
            writeFloat(yaw); writeFloat(pitch)
            writeByte(1)
        })
        visibilityCommands.onWorldReady()
    }

    private fun sendClientBrand() = sendPacket(packet {
        writeVarInt(0x02) // configuration custom payload
        writeProtocolString("minecraft:brand")
        writeProtocolString("CraftConnect")
    })

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
        synchronized(writeLock) {
            val stream = output
            if (!open.get() || stream == null) {
                throw (mutableConnectionFailures.value
                    ?: MinecraftConnectionException.Network("connection_closed_before_send"))
            }
            try {
                packetTrace.record("o", ByteArrayInputStream(payload).readVarInt())
                stream.write(frame(payload, compressionThreshold))
                stream.flush()
            } catch (failure: IOException) {
                throw MinecraftConnectionException.Network("connection_write_failed", failure)
            }
        }
    }

    private fun closeResources(failure: MinecraftConnectionException? = null) {
        if (!open.getAndSet(false)) return
        failure?.let { mutableConnectionFailures.value = it }
        playerList.clear()
        visibilityCommands.reset()
        commonRequests.clear()
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

    private val playerInfoUpdateId get() = if (version == MinecraftVersion.JAVA_26_3) 0x47 else 0x46
    private val playerInfoRemoveId get() = if (version == MinecraftVersion.JAVA_26_3) 0x46 else 0x45
    private val resourcePackId get() = if (version == MinecraftVersion.JAVA_26_3) 0x52 else 0x50
    private val storeCookieId get() = if (version == MinecraftVersion.JAVA_26_3) 0x7A else 0x77
    private val joinGameId get() = if (version == MinecraftVersion.JAVA_26_3) 0x32 else 0x31
    private val gameStateId get() = if (version == MinecraftVersion.JAVA_26_3) 0x27 else 0x26
    private val keepAliveId get() = if (version == MinecraftVersion.JAVA_26_3) 0x2D else 0x2C
    private val positionId get() = if (version == MinecraftVersion.JAVA_26_3) 0x49 else 0x48
    private val playerChatId get() = if (version == MinecraftVersion.JAVA_26_3) 0x42 else 0x41
    private val systemChatId get() = if (version == MinecraftVersion.JAVA_26_3) 0x7C else 0x79
    private val clearDialogId get() = if (version == MinecraftVersion.JAVA_26_3) 0x8E else 0x8B
    private val showDialogId get() = if (version == MinecraftVersion.JAVA_26_3) 0x8F else 0x8C

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




private fun uuidFromProfile(id: String): UUID {
    val compact = id.replace("-", "")
    require(Regex("[a-fA-F0-9]{32}").matches(compact))
    return UUID.fromString("${compact.substring(0, 8)}-${compact.substring(8, 12)}-${compact.substring(12, 16)}-${compact.substring(16, 20)}-${compact.substring(20)}")
}
