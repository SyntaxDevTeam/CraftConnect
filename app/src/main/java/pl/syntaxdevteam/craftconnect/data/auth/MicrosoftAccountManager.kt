package pl.syntaxdevteam.craftconnect.data.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.syntaxdevteam.craftconnect.domain.account.AccountRepository
import pl.syntaxdevteam.craftconnect.domain.auth.*
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.domain.model.AccountType

/** Process-scoped authorization survives rotation; UI never receives access/refresh tokens. */
class MicrosoftAccountManager(
    private val accounts: AccountRepository,
    private val api: MicrosoftAuthApi,
    private val tokens: RefreshTokenStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow<MicrosoftSignInState>(MicrosoftSignInState.Idle)
    val state = mutableState.asStateFlow()
    private var signIn: Job? = null
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, VerifiedMinecraftAccount>()

    fun start() {
        if (signIn?.isActive == true) return
        mutableState.value = MicrosoftSignInState.Starting
        signIn = scope.launch {
            try {
                val authorization = api.begin()
                mutableState.value = MicrosoftSignInState.Waiting(authorization.userCode, authorization.verificationUri)
                val microsoft = api.awaitTokens(authorization)
                val minecraft = api.minecraft(microsoft.accessToken)
                ensureActive()
                mutex.withLock {
                    val id = "microsoft:${minecraft.identity.uuid}"
                    tokens.write(id, microsoft.refreshToken)
                    accounts.saveMicrosoft(minecraft.identity.username, minecraft.identity.uuid)
                    sessions[id] = minecraft
                }
                mutableState.value = MicrosoftSignInState.Success(minecraft.identity.username)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: AuthenticationException) { mutableState.value = MicrosoftSignInState.Failed(failure.problem) }
            catch (_: Exception) { mutableState.value = MicrosoftSignInState.Failed(AuthProblem.INVALID_RESPONSE) }
        }
    }

    fun cancel() {
        signIn?.cancel()
        signIn = null
        mutableState.value = MicrosoftSignInState.Idle
    }

    suspend fun identity(account: AccountProfile): MinecraftIdentity = mutex.withLock {
        if (account.type != AccountType.MICROSOFT || accounts.accounts.value.none { it.id == account.id }) {
            throw AuthenticationException(AuthProblem.REAUTHENTICATE)
        }
        sessions[account.id]?.takeIf { it.expiresAt > clock() + 120_000 }?.let { return@withLock it.identity }
        val stored = tokens.read(account.id) ?: throw AuthenticationException(AuthProblem.REAUTHENTICATE)
        val refreshed = api.refresh(stored)
        // Save rotations even when the next service is temporarily unavailable.
        tokens.write(account.id, refreshed.refreshToken)
        val verified = api.minecraft(refreshed.accessToken)
        if ("microsoft:${verified.identity.uuid}" != account.id) throw AuthenticationException(AuthProblem.REAUTHENTICATE)
        accounts.saveMicrosoft(verified.identity.username, verified.identity.uuid)
        sessions[account.id] = verified
        verified.identity
    }

    suspend fun delete(id: String) = mutex.withLock {
        if (id.startsWith("microsoft:")) tokens.delete(id)
        sessions.remove(id)
        accounts.delete(id)
    }
}
