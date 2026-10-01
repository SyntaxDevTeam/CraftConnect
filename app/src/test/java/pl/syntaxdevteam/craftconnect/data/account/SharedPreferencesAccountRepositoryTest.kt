package pl.syntaxdevteam.craftconnect.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesAccountRepositoryTest {
    @Test
    fun `creates updates deletes and persists offline accounts`() {
        val storage = MemoryStorage()
        val repository = SharedPreferencesAccountRepository(storage) { "account-id" }

        val created = repository.createOffline("  Steve_123  ")
        assertEquals("Steve_123", created.username)
        repository.update(created.copy(username = "Alex"))
        assertEquals("Alex", repository.accounts.value.single().username)
        assertEquals("Alex", SharedPreferencesAccountRepository(storage).accounts.value.single().username)

        repository.delete(created.id)
        assertTrue(repository.accounts.value.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid offline username`() {
        SharedPreferencesAccountRepository(MemoryStorage()).createOffline("a!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate usernames ignoring case`() {
        val repository = SharedPreferencesAccountRepository(MemoryStorage())
        repository.createOffline("Steve")
        repository.createOffline("steve")
    }

    private class MemoryStorage : AccountStorage {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
    }
}
