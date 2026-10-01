package pl.syntaxdevteam.craftconnect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.xbill.DNS.config.AndroidResolverConfigProvider
import pl.syntaxdevteam.craftconnect.data.server.SharedPreferencesServerRepository
import pl.syntaxdevteam.craftconnect.data.account.SharedPreferencesAccountRepository
import pl.syntaxdevteam.craftconnect.ui.theme.CraftConnectTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestConnectionNotification() {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val preferences = getSharedPreferences("notification_permission", MODE_PRIVATE)
        if (!preferences.getBoolean("requested", false)) {
            preferences.edit().putBoolean("requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val serverRepository by lazy {
        SharedPreferencesServerRepository(getSharedPreferences("saved_servers", MODE_PRIVATE))
    }
    private val accountRepository by lazy {
        SharedPreferencesAccountRepository(getSharedPreferences("saved_accounts", MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidResolverConfigProvider.setContext(applicationContext)
        setContent {
            CraftConnectTheme {
                CraftConnectApp(
                    (application as CraftConnectApplication).sessionManagerFactory, serverRepository, accountRepository,
                    (application as CraftConnectApplication).sessionScope,
                    ::requestConnectionNotification,
                )
            }
        }
    }
}

