package pl.syntaxdevteam.craftconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.auth.*
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun MicrosoftSignInCard(state: MicrosoftSignInState, onStart: () -> Unit, onCancel: () -> Unit) {
    val browser = LocalUriHandler.current
    NeonCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.microsoft_accounts))
            Text(stringResource(R.string.microsoft_sign_in_description), color = TextSecondary)
            when (state) {
                MicrosoftSignInState.Idle -> GlowButton(stringResource(R.string.microsoft_sign_in), onClick = onStart)
                MicrosoftSignInState.Starting -> Text(stringResource(R.string.microsoft_starting))
                is MicrosoftSignInState.Waiting -> {
                    Text(stringResource(R.string.microsoft_device_instructions))
                    SelectionContainer { Text(state.userCode) }
                    GlowButton(stringResource(R.string.microsoft_open_browser)) { browser.openUri(state.verificationUri) }
                }
                is MicrosoftSignInState.Success -> {
                    Text(stringResource(R.string.microsoft_added, state.username))
                    GlowButton(stringResource(R.string.microsoft_add_another), onClick = onStart)
                }
                is MicrosoftSignInState.Failed -> {
                    Text(stringResource(authProblemText(state.problem)))
                    GlowButton(stringResource(R.string.microsoft_retry), onClick = onStart)
                }
            }
            if (state is MicrosoftSignInState.Waiting || state is MicrosoftSignInState.Starting) {
                GlowButton(stringResource(R.string.cancel), onClick = onCancel)
            }
        }
    }
}

fun authProblemText(problem: AuthProblem): Int = when (problem) {
    AuthProblem.NETWORK -> R.string.microsoft_error_network
    AuthProblem.EXPIRED -> R.string.microsoft_error_expired
    AuthProblem.DECLINED -> R.string.microsoft_error_declined
    AuthProblem.CONFIGURATION -> R.string.microsoft_error_configuration
    AuthProblem.APP_APPROVAL -> R.string.microsoft_error_approval
    AuthProblem.NO_LICENSE -> R.string.microsoft_error_license
    AuthProblem.NO_PROFILE -> R.string.microsoft_error_profile
    AuthProblem.XBOX_ACCOUNT -> R.string.microsoft_error_xbox
    AuthProblem.REAUTHENTICATE -> R.string.microsoft_error_reauthenticate
    AuthProblem.STORAGE -> R.string.microsoft_error_storage
    AuthProblem.INVALID_RESPONSE -> R.string.microsoft_error_response
}
