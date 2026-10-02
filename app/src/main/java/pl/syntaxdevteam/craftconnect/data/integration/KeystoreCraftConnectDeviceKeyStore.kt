package pl.syntaxdevteam.craftconnect.data.integration

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.integration.CraftConnectDeviceIdentity
import pl.syntaxdevteam.craftconnect.domain.integration.CraftConnectDeviceKeyStore

/** One app-install device key. The EC private key is non-exportable from Android Keystore. */
class KeystoreCraftConnectDeviceKeyStore : CraftConnectDeviceKeyStore {
    private val lock = Any()

    override suspend fun identity(): CraftConnectDeviceIdentity = withContext(Dispatchers.Default) {
        synchronized(lock) {
            ensureKeyPair()
            val store = keyStore()
            val publicKey = checkNotNull(store.getCertificate(ALIAS)?.publicKey) { "Missing CraftConnect public key" }
            val encoded = publicKey.encoded.copyOf()
            CraftConnectDeviceIdentity(
                deviceId = fingerprint(encoded),
                publicKey = encoded,
            )
        }
    }

    override suspend fun sign(payload: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        require(payload.isNotEmpty() && payload.size <= MAX_SIGNED_BYTES) { "Invalid CraftConnect pairing payload" }
        synchronized(lock) {
            ensureKeyPair()
            val privateKey = checkNotNull(keyStore().getKey(ALIAS, null)) { "Missing CraftConnect private key" }
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey as java.security.PrivateKey)
                update(payload)
                sign()
            }
        }
    }

    private fun ensureKeyPair() {
        if (keyStore().containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
        }.generateKeyPair()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun fingerprint(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return buildString(3 + 32) {
            append("cc-")
            for (index in 0 until 16) append("%02x".format(digest[index]))
        }.also { digest.fill(0) }
    }

    private companion object {
        const val ALIAS = "craftconnect_device_pairing_v1"
        const val MAX_SIGNED_BYTES = 16 * 1024
    }
}
