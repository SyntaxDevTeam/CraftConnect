package pl.syntaxdevteam.craftconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogRequest
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun ServerDialogForm(
    dialog: ServerDialogRequest,
    onSubmit: (String, Map<String, String>) -> Unit,
    onCancel: (String) -> Unit,
) {
    var values by remember(dialog) { mutableStateOf(dialog.fields.associate { it.key to "" }) }
    var validationError by remember(dialog) { mutableStateOf<DialogValidationError?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(dialog.title.ifBlank { stringResource(R.string.server_dialog) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                dialog.message?.let { Text(it, color = TextSecondary) }
                dialog.fields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { value ->
                            values = values + (field.key to value.take(field.maxLength))
                            validationError = null
                        },
                        label = { Text(field.label) },
                        singleLine = true,
                        visualTransformation = if (field.secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    )
                }
                when (validationError) {
                    DialogValidationError.REQUIRED -> Text(
                        stringResource(R.string.dialog_fields_required),
                        color = Crimson,
                    )
                    DialogValidationError.MISMATCH -> Text(
                        stringResource(R.string.dialog_passwords_mismatch),
                        color = Crimson,
                    )
                    null -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validationError = validate(dialog, values)
                if (validationError == null) onSubmit(dialog.submitActionId, values)
            }) {
                Text(dialog.submitLabel.ifBlank { stringResource(R.string.confirm) })
            }
        },
        dismissButton = dialog.cancelActionId?.let { cancelActionId ->
            {
                TextButton(onClick = { onCancel(cancelActionId) }) {
                    Text(dialog.cancelLabel ?: stringResource(R.string.cancel))
                }
            }
        },
    )
}

private fun validate(dialog: ServerDialogRequest, values: Map<String, String>): DialogValidationError? {
    if (dialog.fields.any { values[it.key].isNullOrBlank() }) return DialogValidationError.REQUIRED
    val repeated = dialog.fields.firstOrNull {
        it.secret && (it.key.contains("repeat", ignoreCase = true) || it.key.contains("confirm", ignoreCase = true))
    }
    val original = dialog.fields.firstOrNull { it.secret && it.key != repeated?.key }
    if (repeated != null && original != null && values[repeated.key] != values[original.key]) {
        return DialogValidationError.MISMATCH
    }
    return null
}

private enum class DialogValidationError { REQUIRED, MISMATCH }
