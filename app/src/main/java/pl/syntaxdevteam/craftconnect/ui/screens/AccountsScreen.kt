package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.ui.components.BrandHeader
import pl.syntaxdevteam.craftconnect.ui.components.GlowButton
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceRaised
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun AccountsScreen(
    accounts: List<AccountProfile>,
    onCreate: (String) -> Unit,
    onUpdate: (AccountProfile) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editorTarget by remember { mutableStateOf<AccountProfile?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AccountProfile?>(null) }

    Column(Modifier.fillMaxSize()) {
        BrandHeader()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.accounts), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.accounts_summary), color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = { editorTarget = null; editorVisible = true }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.add_offline_account), tint = Crimson)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { MicrosoftAccountPreview() }
            item {
                Text(
                    stringResource(R.string.offline_accounts),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (accounts.isEmpty()) {
                item { Text(stringResource(R.string.no_offline_accounts), color = TextSecondary) }
            }
            items(accounts, key = { it.id }) { account ->
                OfflineAccountRow(
                    account,
                    onEdit = { editorTarget = account; editorVisible = true },
                    onDelete = { deleteTarget = account },
                )
            }
        }
    }

    if (editorVisible) {
        AccountEditorDialog(
            account = editorTarget,
            unavailableUsernames = accounts.filterNot { it.id == editorTarget?.id }.map { it.username },
            onDismiss = { editorVisible = false },
        ) { username ->
            editorTarget?.let { onUpdate(it.copy(username = username)) } ?: onCreate(username)
            editorVisible = false
        }
    }
    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirmation, account.username)) },
            confirmButton = { GlowButton(stringResource(R.string.delete)) { onDelete(account.id); deleteTarget = null } },
            dismissButton = { GlowButton(stringResource(R.string.cancel)) { deleteTarget = null } },
            containerColor = SurfaceRaised,
        )
    }
}

@Composable
private fun MicrosoftAccountPreview() {
    NeonCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Crimson, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.microsoft_accounts), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.microsoft_accounts_coming_soon), color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OfflineAccountRow(account: AccountProfile, onEdit: () -> Unit, onDelete: () -> Unit) {
    NeonCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Person, null, tint = Crimson, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.username, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.offline_account), color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_account), tint = TextSecondary) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_account), tint = TextSecondary) }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    account: AccountProfile?,
    unavailableUsernames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var username by remember(account) { mutableStateOf(account?.username.orEmpty()) }
    var validationVisible by remember(account) { mutableStateOf(false) }
    val valid = Regex("[A-Za-z0-9_]{3,16}").matches(username.trim())
    val duplicate = unavailableUsernames.any { it.equals(username.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (account == null) R.string.add_offline_account else R.string.edit_account)) },
        text = {
            Column {
                OutlinedTextField(username, { username = it.take(16); validationVisible = false }, label = { Text(stringResource(R.string.offline_username)) }, singleLine = true)
                if (validationVisible && !valid) Text(stringResource(R.string.invalid_offline_username), color = Crimson, fontSize = 12.sp)
                if (validationVisible && duplicate) Text(stringResource(R.string.duplicate_offline_username), color = Crimson, fontSize = 12.sp)
            }
        },
        confirmButton = { GlowButton(stringResource(R.string.save)) { validationVisible = true; if (valid && !duplicate) onSave(username.trim()) } },
        dismissButton = { GlowButton(stringResource(R.string.cancel), onClick = onDismiss) },
        containerColor = SurfaceRaised,
    )
}
