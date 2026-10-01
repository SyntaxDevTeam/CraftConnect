package pl.syntaxdevteam.craftconnect.domain.account

import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile

interface AccountRepository {
    val accounts: StateFlow<List<AccountProfile>>

    fun createOffline(username: String): AccountProfile
    fun saveMicrosoft(username: String, uuid: String): AccountProfile
    fun update(account: AccountProfile)
    fun delete(id: String)
}
