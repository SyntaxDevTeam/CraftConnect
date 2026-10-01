package pl.syntaxdevteam.craftconnect.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.domain.session.SessionManager

/** Service promotion starts while the user's Connect action is in the foreground. */
class ForegroundSessionManager(
    private val service: SessionServiceController,
    scope: CoroutineScope,
    private val delegate: SessionManager = DefaultSessionManagerFactory().create(),
) : SessionManager by delegate {
    init {
        scope.launch {
            delegate.session.collect { snapshot ->
                if (snapshot.connectionState == ConnectionState.FAILED || snapshot.connectionState == ConnectionState.DISCONNECTED) {
                    service.stop()
                }
            }
        }
    }

    override suspend fun connect(server: ServerProfile, username: String) {
        service.start()
        delegate.connect(server, username)
    }

    override suspend fun disconnect() {
        try { delegate.disconnect() }
        finally { service.stop() }
    }
}

interface SessionServiceController {
    fun start()
    fun stop()
}
