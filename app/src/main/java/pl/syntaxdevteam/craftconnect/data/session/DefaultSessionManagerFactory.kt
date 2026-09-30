package pl.syntaxdevteam.craftconnect.data.session

import pl.syntaxdevteam.craftconnect.domain.session.SessionManager
import pl.syntaxdevteam.craftconnect.domain.session.SessionManagerFactory
import pl.syntaxdevteam.craftconnect.protocol.legacy.LegacyOfflineMinecraftConnection

class DefaultSessionManagerFactory : SessionManagerFactory {
    override fun create(): SessionManager = DefaultSessionManager(LegacyOfflineMinecraftConnection())
}
