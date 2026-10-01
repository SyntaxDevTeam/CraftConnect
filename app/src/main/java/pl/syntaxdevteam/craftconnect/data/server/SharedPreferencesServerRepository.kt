package pl.syntaxdevteam.craftconnect.data.server

import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.server.ServerRepository

class SharedPreferencesServerRepository internal constructor(
    private val storage: ServerStorage,
    private val createId: () -> String = { UUID.randomUUID().toString() },
) : ServerRepository {
    constructor(
        preferences: SharedPreferences,
        createId: () -> String = { UUID.randomUUID().toString() },
    ) : this(SharedPreferencesServerStorage(preferences), createId)

    private val mutableServers = MutableStateFlow(loadInitialServers())
    override val servers: StateFlow<List<ServerProfile>> = mutableServers.asStateFlow()

    @Synchronized
    override fun create(name: String, address: String, favorite: Boolean, minecraftVersion: MinecraftVersion): ServerProfile {
        val server = ServerProfile(
            id = createId(),
            name = name.trim().also { require(it.isNotEmpty()) { "Server name is required" } },
            address = address.trim().also { require(it.isNotEmpty()) { "Server address is required" } },
            online = false,
            playersOnline = 0,
            playersMax = 0,
            pingMs = null,
            favorite = favorite,
            minecraftVersion = minecraftVersion,
        )
        publish(mutableServers.value + server)
        return server
    }

    @Synchronized
    override fun update(server: ServerProfile) {
        val normalized = server.copy(
            name = server.name.trim().also { require(it.isNotEmpty()) { "Server name is required" } },
            address = server.address.trim().also { require(it.isNotEmpty()) { "Server address is required" } },
        )
        val current = mutableServers.value
        if (current.none { it.id == normalized.id }) return
        publish(current.map { if (it.id == normalized.id) normalized else it })
    }

    @Synchronized
    override fun delete(id: String) {
        val updated = mutableServers.value.filterNot { it.id == id }
        if (updated.size != mutableServers.value.size) publish(updated)
    }

    private fun loadInitialServers(): List<ServerProfile> {
        if (!storage.contains()) return DemoRepository.servers
        return ServerListCodec.decode(storage.read().orEmpty())
    }

    private fun publish(servers: List<ServerProfile>) {
        storage.write(ServerListCodec.encode(servers))
        mutableServers.value = servers
    }
}

internal interface ServerStorage {
    fun contains(): Boolean
    fun read(): String?
    fun write(value: String)
}

private class SharedPreferencesServerStorage(
    private val preferences: SharedPreferences,
) : ServerStorage {
    override fun contains(): Boolean = preferences.contains(KEY_SERVERS)
    override fun read(): String? = preferences.getString(KEY_SERVERS, null)
    override fun write(value: String) {
        preferences.edit().putString(KEY_SERVERS, value).apply()
    }

    private companion object {
        const val KEY_SERVERS = "servers"
    }
}

