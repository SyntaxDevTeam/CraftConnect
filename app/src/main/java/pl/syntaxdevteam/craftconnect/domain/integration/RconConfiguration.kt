package pl.syntaxdevteam.craftconnect.domain.integration

/**
 * Non-secret RCON configuration stored with a server profile.
 *
 * The password itself must live in a secure credential store backed by Android
 * Keystore. [credentialId] is only an opaque reference to that secret.
 */
data class RconConfiguration(
    val enabled: Boolean = false,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val credentialId: String? = null,
) {
    init {
        require(port in 1..65535) { "RCON port must be in range 1..65535" }
    }

    val isConfigured: Boolean
        get() = enabled && host.isNotBlank() && !credentialId.isNullOrBlank()

    companion object {
        const val DEFAULT_PORT: Int = 25575
    }
}

/**
 * Boundary for secret persistence. Android-specific Keystore implementation
 * belongs in the data layer; domain code only handles opaque credential IDs.
 */
interface RconCredentialStore {
    suspend fun save(serverId: String, password: CharArray): String
    suspend fun load(credentialId: String): CharArray?
    suspend fun delete(credentialId: String)
}
