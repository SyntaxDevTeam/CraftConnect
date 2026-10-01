package pl.syntaxdevteam.craftconnect.protocol

import java.util.Locale

/** Explicit success messages, not the act of sending /login or closing a dialog. */
internal object ServerLoginConfirmation {
    fun isSuccessful(message: String): Boolean {
        val normalized = message.replace(Regex("§[0-9A-FK-ORa-fk-or]"), "")
            .trim().replace(Regex("^\\[(?:AuthMe|AuthGatewayX)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .lowercase(Locale.ROOT).trimEnd('.', '!')
        return normalized in successMessages
    }

    private val successMessages = setOf(
        "pomyślnie zalogowano na serwerze",
        "logowanie konta premium przebiegło pomyślnie",
        "pomyślnie zalogowano",
        "zalogowano pomyślnie",
        "zostałeś pomyślnie zalogowany",
        "successfully logged in",
        "you have successfully logged in",
        "login successful",
    )
}
