package pl.syntaxdevteam.craftconnect.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.data.account.*
import pl.syntaxdevteam.craftconnect.domain.auth.*
import pl.syntaxdevteam.craftconnect.domain.model.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MicrosoftAccountManagerTest {
    private val uuid = "12345678123456781234567812345678"
    private class Storage : AccountStorage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private class Tokens : RefreshTokenStore {
        val values = mutableMapOf<String, String>()
        override fun read(id: String) = values[id]
        override fun write(id: String, token: String) { values[id] = token }
        override fun delete(id: String) { values.remove(id) }
    }
    private inner class Api : MicrosoftAuthApi {
        var problem: AuthProblem? = null
        var refreshes = 0
        var name = "Player"
        var wait: CompletableDeferred<Unit>? = null
        override suspend fun begin() = DeviceAuthorization("secret", "user-code", "https://microsoft.com/devicelogin", 100000, 1)
        override suspend fun awaitTokens(authorization: DeviceAuthorization): MicrosoftTokens {
            wait?.await()
            return MicrosoftTokens("ms-secret", "refresh-secret")
        }
        override suspend fun refresh(refreshToken: String): MicrosoftTokens { refreshes++; return MicrosoftTokens("ms-new", "rotated-secret") }
        override suspend fun minecraft(accessToken: String): VerifiedMinecraftAccount {
            problem?.let { throw AuthenticationException(it) }
            return VerifiedMinecraftAccount(MinecraftIdentity(name, uuid, "mc-secret"), 3_600_000)
        }
        override suspend fun join(identity: MinecraftIdentity, serverHash: String) = Unit
    }

    @Test fun `verified account persists identity only and deduplicates by UUID`() = runTest {
        val storage = Storage(); val accounts = SharedPreferencesAccountRepository(storage); val api = Api(); val tokens = Tokens()
        val manager = MicrosoftAccountManager(accounts, api, tokens, backgroundScope, { 0 })
        manager.start(); runCurrent()
        assertEquals(MicrosoftSignInState.Success("Player"), manager.state.value)
        assertEquals(AccountType.MICROSOFT, accounts.accounts.value.single().type)
        assertEquals("refresh-secret", tokens.values.values.single())
        assertFalse(String(java.util.Base64.getDecoder().decode(storage.value)).contains("secret"))
        api.name = "Renamed"
        manager.start(); runCurrent()
        assertEquals("Renamed", accounts.accounts.value.single().username)
    }

    @Test fun `recreated manager refreshes before connecting and deletion removes credentials`() = runTest {
        val accounts = SharedPreferencesAccountRepository(Storage()); val api = Api(); val tokens = Tokens()
        val profile = accounts.saveMicrosoft("Player", uuid)
        tokens.write(profile.id, "old")
        val manager = MicrosoftAccountManager(accounts, api, tokens, backgroundScope, { 0 })
        assertEquals(uuid, manager.identity(profile).uuid)
        manager.identity(profile)
        assertEquals(1, api.refreshes)
        assertEquals("rotated-secret", tokens.read(profile.id))
        manager.delete(profile.id)
        assertTrue(accounts.accounts.value.isEmpty()); assertTrue(tokens.values.isEmpty())
        try { manager.identity(profile); fail() }
        catch (failure: AuthenticationException) { assertEquals(AuthProblem.REAUTHENTICATE, failure.problem) }
    }

    @Test fun `approval failure never publishes an authenticated profile`() = runTest {
        val accounts = SharedPreferencesAccountRepository(Storage()); val api = Api().apply { problem = AuthProblem.APP_APPROVAL }; val tokens = Tokens()
        val manager = MicrosoftAccountManager(accounts, api, tokens, backgroundScope)
        manager.start(); runCurrent()
        assertEquals(MicrosoftSignInState.Failed(AuthProblem.APP_APPROVAL), manager.state.value)
        assertTrue(accounts.accounts.value.isEmpty()); assertTrue(tokens.values.isEmpty())
    }

    @Test fun `cancelled sign in cannot save an account later`() = runTest {
        val accounts = SharedPreferencesAccountRepository(Storage()); val gate = CompletableDeferred<Unit>(); val api = Api().apply { wait = gate }; val tokens = Tokens()
        val manager = MicrosoftAccountManager(accounts, api, tokens, backgroundScope)
        manager.start(); runCurrent(); manager.cancel(); gate.complete(Unit); runCurrent()
        assertEquals(MicrosoftSignInState.Idle, manager.state.value)
        assertTrue(accounts.accounts.value.isEmpty()); assertTrue(tokens.values.isEmpty())
    }

    @Test fun `offline identity cannot overwrite a verified identity`() {
        val accounts = SharedPreferencesAccountRepository(Storage())
        val premium = accounts.saveMicrosoft("Player", uuid)
        accounts.createOffline("Player")
        try { accounts.update(premium.copy(type = AccountType.OFFLINE, username = "Spoofed")); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals("Player", accounts.accounts.value.first { it.id == premium.id }.username)
    }
}
