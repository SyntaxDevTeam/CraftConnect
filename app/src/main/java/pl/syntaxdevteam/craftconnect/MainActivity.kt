package pl.syntaxdevteam.craftconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.xbill.DNS.config.AndroidResolverConfigProvider
import pl.syntaxdevteam.craftconnect.data.server.SharedPreferencesServerRepository
import pl.syntaxdevteam.craftconnect.data.session.DefaultSessionManagerFactory
import pl.syntaxdevteam.craftconnect.ui.theme.CraftConnectTheme

class MainActivity : ComponentActivity() {
    private val serverRepository by lazy {
        SharedPreferencesServerRepository(getSharedPreferences("saved_servers", MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidResolverConfigProvider.setContext(applicationContext)
        setContent {
            CraftConnectTheme {
                CraftConnectApp(DefaultSessionManagerFactory(), serverRepository)
            }
        }
    }
}
