package pl.syntaxdevteam.craftconnect.protocol.agx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.UUID
import pl.syntaxdevteam.craftconnect.domain.integration.ServerCapability

object AuthGatewayXProtocol {
    const val CHANNEL = "authgatewayx:craftconnect"
    const val VERSION = 1
    const val MAX_FRAME_BYTES = 64 * 1024
}

enum class AuthGatewayXCapability(
    val wireId: String,
    val localCapability: ServerCapability,
) {
    PAIRING("pairing", ServerCapability.PAIRING),
    STATUS("status", ServerCapability.SERVER_STATUS),
    STATS("stats", ServerCapability.SERVER_STATS),
    CONSOLE_VIEW("console.view", ServerCapability.CONSOLE_VIEW),
    CONSOLE_EXECUTE("console.execute", ServerCapability.CONSOLE_EXECUTE),
    SERVER_INFO("server.info", ServerCapability.SERVER_INFO),
    BRANDING("branding", ServerCapability.BRANDING),
    DIAGNOSTICS("diagnostics", ServerCapability.DIAGNOSTICS);

    companion object {
        private val byWireId = entries.associateBy(AuthGatewayXCapability::wireId)
        fun fromWireId(id: String): AuthGatewayXCapability? = byWireId[id]
    }
}

data class AuthGatewayXFrame(
    val requestId: UUID,
    val message: AuthGatewayXMessage,
)

sealed interface AuthGatewayXMessage {
    data class ClientHello(val appVersion: String, val deviceId: String?) : AuthGatewayXMessage
    data class ServerHello(
        val serverId: String,
        val serverName: String,
        val supported: Set<AuthGatewayXCapability>,
    ) : AuthGatewayXMessage
    data class Capabilities(val granted: Set<AuthGatewayXCapability>) : AuthGatewayXMessage
    data class PairingBegin(val deviceId: String, val publicKey: ByteArray) : AuthGatewayXMessage
    data class PairingChallenge(
        val challengeId: UUID,
        val serverId: String,
        val nonce: ByteArray,
        val expiresAt: Instant,
    ) : AuthGatewayXMessage
    data class PairingConfirm(val challengeId: UUID, val signature: ByteArray) : AuthGatewayXMessage
    data class PairingResult(val paired: Boolean, val errorCode: String?) : AuthGatewayXMessage
    data class Error(val code: String) : AuthGatewayXMessage
}

class AuthGatewayXProtocolException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Byte-compatible client implementation of AuthGatewayX CraftConnect protocol v1. */
object AuthGatewayXWireProtocol {
    private const val MAGIC = 0x41475843
    private const val CLIENT_HELLO = 1
    private const val SERVER_HELLO = 2
    private const val CAPABILITIES = 3
    private const val PAIRING_BEGIN = 10
    private const val PAIRING_CHALLENGE = 11
    private const val PAIRING_CONFIRM = 12
    private const val PAIRING_RESULT = 13
    private const val ERROR = 127
    private const val MAX_STRING_BYTES = 4 * 1024
    private const val MAX_BINARY_BYTES = 16 * 1024
    private const val MAX_CAPABILITIES = 64

    fun encode(frame: AuthGatewayXFrame): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeByte(AuthGatewayXProtocol.VERSION)
            output.writeByte(typeOf(frame.message))
            output.writeUuid(frame.requestId)
            when (val message = frame.message) {
                is AuthGatewayXMessage.ClientHello -> {
                    output.writeString(message.appVersion)
                    output.writeNullableString(message.deviceId)
                }
                is AuthGatewayXMessage.ServerHello -> {
                    output.writeString(message.serverId)
                    output.writeString(message.serverName)
                    output.writeCapabilities(message.supported)
                }
                is AuthGatewayXMessage.Capabilities -> output.writeCapabilities(message.granted)
                is AuthGatewayXMessage.PairingBegin -> {
                    output.writeString(message.deviceId)
                    output.writeBinary(message.publicKey)
                }
                is AuthGatewayXMessage.PairingChallenge -> {
                    output.writeUuid(message.challengeId)
                    output.writeString(message.serverId)
                    output.writeBinary(message.nonce)
                    output.writeLong(message.expiresAt.toEpochMilli())
                }
                is AuthGatewayXMessage.PairingConfirm -> {
                    output.writeUuid(message.challengeId)
                    output.writeBinary(message.signature)
                }
                is AuthGatewayXMessage.PairingResult -> {
                    output.writeBoolean(message.paired)
                    output.writeNullableString(message.errorCode)
                }
                is AuthGatewayXMessage.Error -> output.writeString(message.code)
            }
        }
        return buffer.toByteArray().also {
            if (it.size > AuthGatewayXProtocol.MAX_FRAME_BYTES) {
                throw AuthGatewayXProtocolException("AuthGatewayX frame exceeds maximum size")
            }
        }
    }

    fun decode(bytes: ByteArray): AuthGatewayXFrame {
        if (bytes.size > AuthGatewayXProtocol.MAX_FRAME_BYTES) {
            throw AuthGatewayXProtocolException("AuthGatewayX frame exceeds maximum size")
        }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) throw AuthGatewayXProtocolException("Invalid AuthGatewayX frame magic")
                val version = input.readUnsignedByte()
                if (version != AuthGatewayXProtocol.VERSION) {
                    throw AuthGatewayXProtocolException("Unsupported AuthGatewayX protocol version: $version")
                }
                val type = input.readUnsignedByte()
                val requestId = input.readUuid()
                val message = when (type) {
                    CLIENT_HELLO -> AuthGatewayXMessage.ClientHello(input.readString(), input.readNullableString())
                    SERVER_HELLO -> AuthGatewayXMessage.ServerHello(
                        input.readString(), input.readString(), input.readCapabilities(),
                    )
                    CAPABILITIES -> AuthGatewayXMessage.Capabilities(input.readCapabilities())
                    PAIRING_BEGIN -> AuthGatewayXMessage.PairingBegin(input.readString(), input.readBinary())
                    PAIRING_CHALLENGE -> AuthGatewayXMessage.PairingChallenge(
                        input.readUuid(), input.readString(), input.readBinary(), Instant.ofEpochMilli(input.readLong()),
                    )
                    PAIRING_CONFIRM -> AuthGatewayXMessage.PairingConfirm(input.readUuid(), input.readBinary())
                    PAIRING_RESULT -> AuthGatewayXMessage.PairingResult(input.readBoolean(), input.readNullableString())
                    ERROR -> AuthGatewayXMessage.Error(input.readString())
                    else -> throw AuthGatewayXProtocolException("Unknown AuthGatewayX message type: $type")
                }
                if (input.available() != 0) throw AuthGatewayXProtocolException("Trailing AuthGatewayX frame data")
                AuthGatewayXFrame(requestId, message)
            }
        } catch (failure: AuthGatewayXProtocolException) {
            throw failure
        } catch (failure: Exception) {
            throw AuthGatewayXProtocolException("Malformed AuthGatewayX frame", failure)
        }
    }

    private fun typeOf(message: AuthGatewayXMessage): Int = when (message) {
        is AuthGatewayXMessage.ClientHello -> CLIENT_HELLO
        is AuthGatewayXMessage.ServerHello -> SERVER_HELLO
        is AuthGatewayXMessage.Capabilities -> CAPABILITIES
        is AuthGatewayXMessage.PairingBegin -> PAIRING_BEGIN
        is AuthGatewayXMessage.PairingChallenge -> PAIRING_CHALLENGE
        is AuthGatewayXMessage.PairingConfirm -> PAIRING_CONFIRM
        is AuthGatewayXMessage.PairingResult -> PAIRING_RESULT
        is AuthGatewayXMessage.Error -> ERROR
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_STRING_BYTES) throw AuthGatewayXProtocolException("AuthGatewayX string too long")
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        if (size !in 0..MAX_STRING_BYTES) throw AuthGatewayXProtocolException("Invalid AuthGatewayX string size")
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null

    private fun DataOutputStream.writeBinary(value: ByteArray) {
        if (value.size > MAX_BINARY_BYTES) throw AuthGatewayXProtocolException("AuthGatewayX binary field too long")
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readBinary(): ByteArray {
        val size = readInt()
        if (size !in 0..MAX_BINARY_BYTES) throw AuthGatewayXProtocolException("Invalid AuthGatewayX binary size")
        return ByteArray(size).also { readFully(it) }
    }

    private fun DataOutputStream.writeUuid(value: UUID) {
        writeLong(value.mostSignificantBits)
        writeLong(value.leastSignificantBits)
    }

    private fun DataInputStream.readUuid(): UUID = UUID(readLong(), readLong())

    private fun DataOutputStream.writeCapabilities(capabilities: Set<AuthGatewayXCapability>) {
        if (capabilities.size > MAX_CAPABILITIES) throw AuthGatewayXProtocolException("Too many AuthGatewayX capabilities")
        writeInt(capabilities.size)
        capabilities.sortedBy(AuthGatewayXCapability::wireId).forEach { writeString(it.wireId) }
    }

    private fun DataInputStream.readCapabilities(): Set<AuthGatewayXCapability> {
        val count = readInt()
        if (count !in 0..MAX_CAPABILITIES) throw AuthGatewayXProtocolException("Invalid capability count")
        return buildSet {
            repeat(count) {
                AuthGatewayXCapability.fromWireId(readString())?.let { add(it) }
            }
        }
    }
}
