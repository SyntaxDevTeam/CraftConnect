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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.ui.components.BrandHeader
import pl.syntaxdevteam.craftconnect.ui.components.GlowButton
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.components.ServerGlyph
import pl.syntaxdevteam.craftconnect.ui.components.StatusDot
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.Online
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceRaised
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun ServersScreen(
    servers: List<ServerProfile>,
    connectionState: ConnectionState,
    connectionError: String?,
    onCreate: (name: String, address: String, favorite: Boolean) -> Unit,
    onUpdate: (ServerProfile) -> Unit,
    onDelete: (String) -> Unit,
    onConnect: (ServerProfile, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var connectionTarget by remember { mutableStateOf<ServerProfile?>(null) }
    var editorTarget by remember { mutableStateOf<ServerProfile?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ServerProfile?>(null) }

    val filteredServers = servers.filter { server ->
        (!favoritesOnly || server.favorite) &&
            (query.isBlank() ||
                server.name.contains(query, ignoreCase = true) ||
                server.address.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize()) {
        BrandHeader()

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(stringResource(R.string.search_servers), color = TextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Crimson,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = SurfaceRaised,
                    unfocusedContainerColor = SurfaceRaised,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Crimson,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                editorTarget = null
                editorVisible = true
            }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_server),
                    tint = Crimson,
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlowButton(stringResource(R.string.all), onClick = { favoritesOnly = false })
            GlowButton(stringResource(R.string.favorites), onClick = { favoritesOnly = true })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredServers.isEmpty()) {
                item {
                    Text(
                        text = if (servers.isEmpty()) {
                            stringResource(R.string.no_saved_servers)
                        } else {
                            stringResource(R.string.no_matching_servers)
                        },
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(filteredServers, key = { it.id }) { server ->
                ServerRow(
                    server = server,
                    onConnect = { connectionTarget = server },
                    onToggleFavorite = { onUpdate(server.copy(favorite = !server.favorite)) },
                    onEdit = {
                        editorTarget = server
                        editorVisible = true
                    },
                    onDelete = { deleteTarget = server },
                )
            }
        }
    }

    connectionTarget?.let { target ->
        OfflineConnectDialog(
            initialAddress = target.address,
            connecting = connectionState == ConnectionState.CONNECTING,
            failed = connectionState == ConnectionState.FAILED,
            connectionError = connectionError,
            onDismiss = { connectionTarget = null },
            onConnect = { address, username -> onConnect(target.copy(address = address), username) },
        )
    }

    if (editorVisible) {
        ServerEditorDialog(
            server = editorTarget,
            onDismiss = { editorVisible = false },
            onSave = { name, address, favorite ->
                val target = editorTarget
                if (target == null) {
                    onCreate(name, address, favorite)
                } else {
                    onUpdate(target.copy(name = name, address = address, favorite = favorite))
                }
                editorVisible = false
            },
        )
    }

    deleteTarget?.let { target ->
        DeleteServerDialog(
            server = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDelete(target.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun ServerEditorDialog(
    server: ServerProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var name by remember(server) { mutableStateOf(server?.name.orEmpty()) }
    var address by remember(server) { mutableStateOf(server?.address.orEmpty()) }
    var favorite by remember(server) { mutableStateOf(server?.favorite ?: false) }
    var validationVisible by remember(server) { mutableStateOf(false) }
    val valid = name.isNotBlank() && address.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (server == null) R.string.add_server else R.string.edit_server))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(64); validationVisible = false },
                    label = { Text(stringResource(R.string.server_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(255); validationVisible = false },
                    label = { Text(stringResource(R.string.server_address)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = favorite, onCheckedChange = { favorite = it })
                    Text(stringResource(R.string.favorite_server))
                }
                if (validationVisible && !valid) {
                    Text(stringResource(R.string.server_fields_required), color = Crimson, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            GlowButton(stringResource(R.string.save), onClick = {
                validationVisible = true
                if (valid) onSave(name.trim(), address.trim(), favorite)
            })
        },
        dismissButton = { GlowButton(stringResource(R.string.cancel), onClick = onDismiss) },
        containerColor = SurfaceRaised,
    )
}

@Composable
private fun DeleteServerDialog(
    server: ServerProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_server)) },
        text = { Text(stringResource(R.string.delete_server_confirmation, server.name)) },
        confirmButton = { GlowButton(stringResource(R.string.delete), onClick = onConfirm) },
        dismissButton = { GlowButton(stringResource(R.string.cancel), onClick = onDismiss) },
        containerColor = SurfaceRaised,
    )
}

@Composable
private fun OfflineConnectDialog(
    initialAddress: String,
    connecting: Boolean,
    failed: Boolean,
    connectionError: String?,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
) {
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    var username by remember { mutableStateOf("") }
    val valid = address.isNotBlank() && Regex("[A-Za-z0-9_]{3,16}").matches(username)

    AlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        title = { Text(stringResource(R.string.quick_connect)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    singleLine = true,
                    enabled = !connecting,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.offline_username)) },
                    singleLine = true,
                    enabled = !connecting,
                )
                Text(stringResource(R.string.modern_protocol_notice), color = TextSecondary, fontSize = 12.sp)
                if (failed) {
                    Text(
                        connectionError ?: stringResource(R.string.connection_failed),
                        color = Crimson,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            GlowButton(
                if (connecting) stringResource(R.string.connecting) else stringResource(R.string.connect),
                onClick = { if (valid && !connecting) onConnect(address.trim(), username) },
            )
        },
        dismissButton = { GlowButton(stringResource(R.string.cancel), onClick = onDismiss) },
        containerColor = SurfaceRaised,
    )
}

@Composable
private fun ServerRow(
    server: ServerProfile,
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    NeonCard(highlighted = server.favorite) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerGlyph(server.name, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (server.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(R.string.toggle_favorite),
                            tint = if (server.favorite) Crimson else TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit_server),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete_server),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(server.address, color = TextSecondary, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(server.online)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (server.online) stringResource(R.string.online) else stringResource(R.string.offline),
                        color = if (server.online) Online else TextSecondary,
                        fontSize = 11.sp,
                    )
                    Text("  " + server.playersOnline + " / " + server.playersMax, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.width(8.dp))
            GlowButton(stringResource(R.string.connect), onClick = onConnect)
        }
    }
}
