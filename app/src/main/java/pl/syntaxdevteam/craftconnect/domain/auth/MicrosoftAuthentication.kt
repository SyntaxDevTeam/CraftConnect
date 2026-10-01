package pl.syntaxdevteam.craftconnect.domain.auth

/** Credentials deliberately have no generated toString/equals or saved UI state. */
class MinecraftIdentity(val username: String, val uuid: String, val accessToken: String)
class MicrosoftTokens(val accessToken: String, val refreshToken: String)
class VerifiedMinecraftAccount(val identity: MinecraftIdentity, val expiresAt: Long)
class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Long,
    val intervalMillis: Long,
)

enum class AuthProblem {
    NETWORK, EXPIRED, DECLINED, CONFIGURATION, APP_APPROVAL, NO_LICENSE, NO_PROFILE,
    XBOX_ACCOUNT, REAUTHENTICATE, STORAGE, INVALID_RESPONSE,
}
class AuthenticationException(val problem: AuthProblem) : Exception(problem.name)

sealed interface MicrosoftSignInState {
    data object Idle : MicrosoftSignInState
    data object Starting : MicrosoftSignInState
    data class Waiting(val userCode: String, val verificationUri: String) : MicrosoftSignInState
    data class Success(val username: String) : MicrosoftSignInState
    data class Failed(val problem: AuthProblem) : MicrosoftSignInState
}

interface MicrosoftAuthApi {
    suspend fun begin(): DeviceAuthorization
    suspend fun awaitTokens(authorization: DeviceAuthorization): MicrosoftTokens
    suspend fun refresh(refreshToken: String): MicrosoftTokens
    suspend fun minecraft(accessToken: String): VerifiedMinecraftAccount
    suspend fun join(identity: MinecraftIdentity, serverHash: String)
}

interface RefreshTokenStore {
    fun read(id: String): String?
    fun write(id: String, token: String)
    fun delete(id: String)
}
