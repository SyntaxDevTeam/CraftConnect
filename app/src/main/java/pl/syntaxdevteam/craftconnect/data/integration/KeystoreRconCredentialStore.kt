package pl.syntaxdevteam.craftconnect.data.integration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.CharBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.integration.RconCredentialStore

/** Device-bound RCON credentials encrypted with an AES key kept in Android Keystore. */
class KeystoreRconCredentialStore(context: Context) : RconCredentialStore {
    private val directory = File(context.noBackupFilesDir, "rcon_credentials").apply { mkdirs() }
    private val lock = Any()
    private val random = SecureRandom()

    override suspend fun save(serverId: String, password: CharArray): String = withContext(Dispatchers.IO) {
        require(serverId.isNotBlank())
        require(password.isNotEmpty())
        synchronized(lock) {
            val id = newCredentialId()
            val clear = encodeUtf8(password)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
                val encrypted = cipher.iv + cipher.doFinal(clear)
                val target = file(id)
                val stream = target.startWrite()
                try {
                    stream.write(encrypted)
                    target.finishWrite(stream)
                } catch (failure: Throwable) {
                    target.failWrite(stream)
                    throw failure
                }
                id
            } finally {
                clear.fill(0)
            }
        }
    }

    override suspend fun load(credentialId: String): CharArray? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val target = file(credentialId)
            if (!target.baseFile.exists()) return@synchronized null
            val encrypted = target.readFully()
            require(encrypted.size in MIN_ENCRYPTED_SIZE..MAX_ENCRYPTED_SIZE)
            val clear = try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, encrypted.copyOfRange(0, IV_SIZE)),
                )
                cipher.updateAAD(credentialId.toByteArray(Charsets.UTF_8))
                cipher.doFinal(encrypted.copyOfRange(IV_SIZE, encrypted.size))
            } finally {
                encrypted.fill(0)
            }
            try {
                val chars = Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(clear))
                CharArray(chars.remaining()).also { chars.get(it) }
            } finally {
                clear.fill(0)
            }
        }
    }

    override suspend fun delete(credentialId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { file(credentialId).delete() }
    }

    private fun file(id: String): AtomicFile {
        require(ID_PATTERN.matches(id)) { "Invalid RCON credential id" }
        return AtomicFile(File(directory, id.removePrefix(PREFIX)))
    }

    private fun newCredentialId(): String {
        repeat(8) {
            val randomBytes = ByteArray(16).also(random::nextBytes)
            val suffix = randomBytes.joinToString(separator = "") { "%02x".format(it) }
            randomBytes.fill(0)
            val id = PREFIX + suffix
            if (!file(id).baseFile.exists()) return id
        }
        error("Unable to allocate RCON credential id")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    private companion object {
        const val PREFIX = "rcon:"
        const val KEY_ALIAS = "craftconnect_rcon_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val MIN_ENCRYPTED_SIZE = IV_SIZE + 16 + 1
        const val MAX_ENCRYPTED_SIZE = 16 * 1024
        val ID_PATTERN = Regex("rcon:[a-f0-9]{32}")
    }
}
