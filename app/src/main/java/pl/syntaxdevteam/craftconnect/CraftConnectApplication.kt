package pl.syntaxdevteam.craftconnect

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
    val sessionManager by lazy { ForegroundSessionManager(AndroidSessionServiceController(this), sessionScope) }
    val sessionManagerFactory = SessionManagerFactory { sessionManager }
}
