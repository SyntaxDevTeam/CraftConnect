package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
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
import pl.syntaxdevteam.craftconnect.data.DemoRepository
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
    connectionState: ConnectionState,
    onConnect: (ServerProfile, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var connectionTarget by remember { mutableStateOf<ServerProfile?>(null) }
    var customConnection by remember { mutableStateOf(false) }

    val servers = DemoRepository.servers.filter { server ->
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
            IconButton(onClick = { customConnection = true }) {
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
            items(servers, key = { it.id }) { server ->
                ServerRow(server, onConnect = { connectionTarget = server })
            }
        }
    }

    val target = connectionTarget
    if (target != null || customConnection) {
        OfflineConnectDialog(
            initialAddress = target?.address.orEmpty(),
            connecting = connectionState == ConnectionState.CONNECTING,
            failed = connectionState == ConnectionState.FAILED,
            onDismiss = {
                connectionTarget = null
                customConnection = false
            },
            onConnect = { address, username ->
                val server = target ?: ServerProfile(
                    id = "custom-$address",
                    name = address,
                    address = address,
                    online = false,
                    playersOnline = 0,
                    playersMax = 0,
                    pingMs = null,
                )
                onConnect(server.copy(address = address), username)
            },
        )
    }
}

@Composable
private fun OfflineConnectDialog(
    initialAddress: String,
    connecting: Boolean,
    failed: Boolean,
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
                Text(stringResource(R.string.legacy_protocol_notice), color = TextSecondary, fontSize = 12.sp)
                if (failed) Text(stringResource(R.string.connection_failed), color = Crimson, fontSize = 12.sp)
            }
        },
        confirmButton = {
            GlowButton(
                if (connecting) stringResource(R.string.connecting) else stringResource(R.string.connect),
                onClick = { if (valid && !connecting) onConnect(address.trim(), username) },
            )
        },
        dismissButton = {
            GlowButton(stringResource(R.string.cancel), onClick = onDismiss)
        },
        containerColor = SurfaceRaised,
    )
}

@Composable
private fun ServerRow(server: ServerProfile, onConnect: () -> Unit) {
    NeonCard(highlighted = server.favorite) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerGlyph(server.name, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        if (server.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        tint = if (server.favorite) Crimson else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
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

            GlowButton(stringResource(R.string.connect), onClick = onConnect)
        }
    }
}
