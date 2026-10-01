package pl.syntaxdevteam.craftconnect.data.account

import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.syntaxdevteam.craftconnect.domain.account.AccountRepository
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.domain.model.AccountType

class SharedPreferencesAccountRepository internal constructor(
    private val storage: AccountStorage,
    private val createId: () -> String = { UUID.randomUUID().toString() },
) : AccountRepository {
    constructor(preferences: SharedPreferences) : this(SharedPreferencesAccountStorage(preferences))

    private val mutableAccounts = MutableStateFlow(AccountListCodec.decode(storage.read().orEmpty()))
    override val accounts: StateFlow<List<AccountProfile>> = mutableAccounts.asStateFlow()

    @Synchronized
    override fun createOffline(username: String): AccountProfile {
        val normalized = validateUsername(username)
        require(mutableAccounts.value.none { it.username.equals(normalized, ignoreCase = true) }) {
            "Account username already exists"
        }
        return AccountProfile(createId(), normalized, AccountType.OFFLINE).also {
            publish(mutableAccounts.value + it)
        }
    }

    @Synchronized
    override fun update(account: AccountProfile) {
        require(account.type == AccountType.OFFLINE) { "Microsoft accounts cannot be edited yet" }
        val normalized = account.copy(username = validateUsername(account.username))
        require(mutableAccounts.value.none {
            it.id != normalized.id && it.username.equals(normalized.username, ignoreCase = true)
        }) { "Account username already exists" }
        if (mutableAccounts.value.none { it.id == normalized.id }) return
        publish(mutableAccounts.value.map { if (it.id == normalized.id) normalized else it })
    }

    @Synchronized
    override fun delete(id: String) {
        val updated = mutableAccounts.value.filterNot { it.id == id }
        if (updated.size != mutableAccounts.value.size) publish(updated)
    }

    private fun publish(accounts: List<AccountProfile>) {
        storage.write(AccountListCodec.encode(accounts))
        mutableAccounts.value = accounts
    }

    private fun validateUsername(username: String): String = username.trim().also {
        require(USERNAME.matches(it)) { "Username must contain 3-16 letters, digits or underscores" }
    }

    private companion object {
        val USERNAME = Regex("[A-Za-z0-9_]{3,16}")
    }
}

internal interface AccountStorage {
    fun read(): String?
    fun write(value: String)
}

private class SharedPreferencesAccountStorage(private val preferences: SharedPreferences) : AccountStorage {
    override fun read(): String? = preferences.getString(KEY, null)
    override fun write(value: String) { preferences.edit().putString(KEY, value).apply() }

    private companion object { const val KEY = "accounts" }
}
