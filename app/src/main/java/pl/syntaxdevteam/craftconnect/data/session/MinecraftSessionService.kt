package pl.syntaxdevteam.craftconnect.data.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.syntaxdevteam.craftconnect.CraftConnectApplication
import pl.syntaxdevteam.craftconnect.MainActivity
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState

/** Keeps the process eligible to answer server keep-alives when the UI is backgrounded. */
class MinecraftSessionService : Service() {
    private val app get() = application as CraftConnectApplication
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.session_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        observer = app.sessionScope.launch {
            app.sessionManager.session.collect { snapshot ->
                if (snapshot.connectionState != ConnectionState.DISCONNECTED && snapshot.connectionState != ConnectionState.FAILED &&
                    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@MinecraftSessionService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                ) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(snapshot.server?.name))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISCONNECT) app.sessionScope.launch { app.sessionManager.disconnect() }
        return START_NOT_STICKY // A killed process cannot restore an authenticated socket.
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(serverName: String?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val disconnect = PendingIntent.getService(this, 1, Intent(this, MinecraftSessionService::class.java).setAction(DISCONNECT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_connection)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(serverName?.let { getString(R.string.session_notification_connected, it) } ?: getString(R.string.session_notification_connecting))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.session_disconnect), disconnect)
            .build()
    }

    companion object {
        private const val CHANNEL = "minecraft_session"
        private const val NOTIFICATION_ID = 1
        private const val DISCONNECT = "pl.syntaxdevteam.craftconnect.DISCONNECT"
    }
}
