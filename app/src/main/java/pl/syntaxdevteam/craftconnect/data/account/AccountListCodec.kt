package pl.syntaxdevteam.craftconnect.data.account

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.domain.model.AccountType

internal object AccountListCodec {
    fun encode(accounts: List<AccountProfile>): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeInt(accounts.size)
                accounts.forEach { account ->
                    output.writeUTF(account.id)
                    output.writeUTF(account.username)
                    output.writeUTF(account.type.name)
                }
            }
            buffer.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(encoded: String): List<AccountProfile> = runCatching {
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            require(input.readInt() == FORMAT_VERSION)
            val count = input.readInt()
            require(count in 0..MAX_ACCOUNTS)
            List(count) {
                AccountProfile(input.readUTF(), input.readUTF(), AccountType.valueOf(input.readUTF()))
            }
        }
    }.getOrDefault(emptyList())

    private const val FORMAT_VERSION = 1
    private const val MAX_ACCOUNTS = 100
}
