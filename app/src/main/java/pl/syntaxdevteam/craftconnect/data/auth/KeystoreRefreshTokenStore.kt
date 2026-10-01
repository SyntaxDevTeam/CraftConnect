package pl.syntaxdevteam.craftconnect.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import pl.syntaxdevteam.craftconnect.domain.auth.*

/** Device-bound encrypted refresh tokens, excluded from cloud backup and device transfer. */
class KeystoreRefreshTokenStore(context: Context) : RefreshTokenStore {
    private val directory = File(context.noBackupFilesDir, "microsoft_accounts").apply { mkdirs() }
    private fun file(id: String): AtomicFile {
        require(Regex("microsoft:[a-f0-9]{32}").matches(id))
        return AtomicFile(File(directory, id.removePrefix("microsoft:")))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized
    override fun read(id: String): String? {
        val file = file(id)
        if (!file.baseFile.exists()) return null
        return try {
            val bytes = file.readFully()
            require(bytes.size in 29..65536)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw AuthenticationException(AuthProblem.REAUTHENTICATE) }
    }
    @Synchronized
    override fun write(id: String, token: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val target = file(id)
            val stream = target.startWrite()
            try { stream.write(bytes); target.finishWrite(stream) }
            catch (failure: Exception) { target.failWrite(stream); throw failure }
        } catch (_: Exception) { throw AuthenticationException(AuthProblem.STORAGE) }
    }
    @Synchronized
    override fun delete(id: String) { file(id).delete() }
    private companion object { const val ALIAS = "craftconnect_microsoft_refresh_v1" }
}
