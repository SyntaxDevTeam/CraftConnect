package pl.syntaxdevteam.craftconnect.data.session

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AndroidSessionServiceController(private val context: Context) : SessionServiceController {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, MinecraftSessionService::class.java))
    }
    override fun stop() {
        context.stopService(Intent(context, MinecraftSessionService::class.java))
    }
}
