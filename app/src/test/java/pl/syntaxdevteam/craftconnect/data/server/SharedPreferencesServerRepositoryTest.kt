package pl.syntaxdevteam.craftconnect.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

class SharedPreferencesServerRepositoryTest {
    @Test
    fun createUpdateReadAndDeleteArePersisted() {
        val storage = MemoryServerStorage(ServerListCodec.encode(emptyList()))
        val repository = SharedPreferencesServerRepository(storage, createId = { "server-1" })

        val created = repository.create("  Survival  ", "  play.example.net  ", favorite = false)

        assertEquals("server-1", created.id)
        assertEquals("Survival", created.name)
        assertEquals("play.example.net", created.address)
        assertEquals(listOf(created), repository.servers.value)

        repository.update(created.copy(name = "Creative", address = "creative.example.net", favorite = true))

        val restored = SharedPreferencesServerRepository(storage).servers.value.single()
        assertEquals("Creative", restored.name)
        assertEquals("creative.example.net", restored.address)
        assertEquals(true, restored.favorite)
        assertFalse(restored.online)
        assertNull(restored.pingMs)

        repository.delete(created.id)

        assertEquals(emptyList<ServerProfile>(), repository.servers.value)
        assertEquals(emptyList<ServerProfile>(), SharedPreferencesServerRepository(storage).servers.value)
    }

    @Test
    fun missingStorageUsesInitialDemoListWithoutPersistingIt() {
        val storage = MemoryServerStorage()

        val repository = SharedPreferencesServerRepository(storage)

        assertEquals(1, repository.servers.value.size)
        assertFalse(storage.contains())
    }

    private class MemoryServerStorage(
        private var value: String? = null,
    ) : ServerStorage {
        override fun contains(): Boolean = value != null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }
}
