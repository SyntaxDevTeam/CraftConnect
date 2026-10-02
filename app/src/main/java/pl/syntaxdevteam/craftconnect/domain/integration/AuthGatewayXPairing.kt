package pl.syntaxdevteam.craftconnect.domain.integration

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.UUID

data class CraftConnectDeviceIdentity(
    val deviceId: String,
    val publicKey: ByteArray,
)

/** Android implementation keeps the private key non-exportable in Android Keystore. */
interface CraftConnectDeviceKeyStore {
    suspend fun identity(): CraftConnectDeviceIdentity
    suspend fun sign(payload: ByteArray): ByteArray
}

data class AuthGatewayXPairingChallenge(
    val challengeId: UUID,
    val serverId: String,
    val playerUuid: UUID,
    val deviceId: String,
    val nonce: ByteArray,
    val expiresAt: Instant,
)

/** Must remain byte-identical to AuthGatewayX `CraftConnectPairingSignaturePayload` v1. */
object AuthGatewayXPairingSignaturePayload {
    private const val DOMAIN = "AGX-CRAFTCONNECT-PAIR-V1"

    fun encode(challenge: AuthGatewayXPairingChallenge): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUtf8(DOMAIN)
                output.writeUtf8(challenge.serverId)
                output.writeLong(challenge.challengeId.mostSignificantBits)
                output.writeLong(challenge.challengeId.leastSignificantBits)
                output.writeLong(challenge.playerUuid.mostSignificantBits)
                output.writeLong(challenge.playerUuid.leastSignificantBits)
                output.writeUtf8(challenge.deviceId)
                require(challenge.nonce.size in 16..256)
                output.writeInt(challenge.nonce.size)
                output.write(challenge.nonce)
                output.writeLong(challenge.expiresAt.toEpochMilli())
            }
            buffer.toByteArray()
        }

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 4 * 1024)
        writeInt(bytes.size)
        write(bytes)
    }
}
