package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.ProtocolCodecException

internal class OnlineLoginEncryption(input: DataInputStream) {
    private val serverId = input.readProtocolString(20)
    private val publicKeyBytes = input.boundedBytes(4096)
    private val challenge = input.boundedBytes(1024)
    val shouldAuthenticate = input.readBoolean()
    private val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
    private val secret: SecretKey = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    val serverHash: String = BigInteger(MessageDigest.getInstance("SHA-1").run {
        update(serverId.toByteArray(Charsets.ISO_8859_1)); update(secret.encoded); digest(publicKeyBytes)
    }).toString(16)
    fun encryptedSecret(): ByteArray = encrypt(secret.encoded)
    fun encryptedChallenge(): ByteArray = encrypt(challenge)
    private fun encrypt(bytes: ByteArray) = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.ENCRYPT_MODE, publicKey); doFinal(bytes)
    }
    fun streamCipher(mode: Int): Cipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
        init(mode, secret, IvParameterSpec(secret.encoded))
    }
}

private fun DataInputStream.boundedBytes(max: Int): ByteArray {
    val length = readVarInt()
    if (length !in 1..max) throw ProtocolCodecException("invalid_encryption_request")
    return ByteArray(length).also { readFully(it) }
}
