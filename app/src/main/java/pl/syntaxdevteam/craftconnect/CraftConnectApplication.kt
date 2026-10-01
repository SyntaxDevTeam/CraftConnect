package pl.syntaxdevteam.craftconnect

import pl.syntaxdevteam.craftconnect.data.account.SharedPreferencesAccountRepository
import pl.syntaxdevteam.craftconnect.data.auth.KeystoreRefreshTokenStore
import pl.syntaxdevteam.craftconnect.data.auth.MicrosoftAccountManager
import pl.syntaxdevteam.craftconnect.data.auth.MicrosoftMinecraftAuthApi
import pl.syntaxdevteam.craftconnect.data.session.DefaultSessionManager
import pl.syntaxdevteam.craftconnect.protocol.modern.ModernOfflineMinecraftConnection

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.syntaxdevteam.craftconnect.data.session.AndroidSessionServiceController
import pl.syntaxdevteam.craftconnect.data.session.ForegroundSessionManager
import pl.syntaxdevteam.craftconnect.domain.session.SessionManagerFactory

/** One protocol session per process, independent of Activity/Compose recreation. */
class CraftConnectApplication : Application() {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val accountRepository by lazy {
        SharedPreferencesAccountRepository(getSharedPreferences("saved_accounts", MODE_PRIVATE))
    }
    private val microsoftApi by lazy {
        MicrosoftMinecraftAuthApi("931c77bd-a884-457d-9dbd-da85c2fca433")
    }
    val microsoftAccounts by lazy {
        MicrosoftAccountManager(accountRepository, microsoftApi,
            KeystoreRefreshTokenStore(this), sessionScope)
    }
    val sessionManager by lazy {
        ForegroundSessionManager(AndroidSessionServiceController(this), sessionScope,
            DefaultSessionManager(
                ModernOfflineMinecraftConnection(joinSession = microsoftApi::join),
                premiumIdentity = microsoftAccounts::identity))
    }
    val sessionManagerFactory = SessionManagerFactory { sessionManager }
}
