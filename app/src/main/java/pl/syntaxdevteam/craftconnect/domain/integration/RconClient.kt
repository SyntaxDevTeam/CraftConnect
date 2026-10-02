package pl.syntaxdevteam.craftconnect.domain.integration

/** Result of one Console Lite command. RCON exposes command output, not a live log stream. */
data class RconCommandResult(
    val output: String,
)

sealed class RconException(
    val code: String,
    cause: Throwable? = null,
) : Exception(code, cause) {
    class NotConfigured : RconException("rcon_not_configured")
    class CredentialMissing : RconException("rcon_credential_missing")
    class AuthenticationRejected : RconException("rcon_authentication_rejected")
    class Network(cause: Throwable) : RconException("rcon_network_error", cause)
    class Protocol(cause: Throwable? = null) : RconException("rcon_protocol_error", cause)
}

/** Transport boundary implemented by the Source RCON adapter. */
fun interface RconCommandClient {
    suspend fun execute(
        configuration: RconConfiguration,
        password: CharArray,
        command: String,
    ): RconCommandResult
}

/**
 * Resolves the encrypted credential only for the duration of one RCON request and
 * wipes the returned character buffer afterwards.
 */
class RconCommandService(
    private val credentials: RconCredentialStore,
    private val client: RconCommandClient,
) {
    suspend fun execute(configuration: RconConfiguration, command: String): RconCommandResult {
        if (!configuration.isConfigured) throw RconException.NotConfigured()
        val credentialId = configuration.credentialId ?: throw RconException.CredentialMissing()
        val password = credentials.load(credentialId) ?: throw RconException.CredentialMissing()
        return try {
            client.execute(configuration, password, command)
        } finally {
            password.fill('\u0000')
        }
    }
}
