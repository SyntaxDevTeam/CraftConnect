package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import pl.syntaxdevteam.craftconnect.domain.model.ServerPlayer
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.components.SessionHeader
import pl.syntaxdevteam.craftconnect.ui.components.StatusDot
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceRaised
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun PlayersScreen(server: ServerProfile, serverPlayers: List<ServerPlayer>, connected: Boolean) {
    var query by remember { mutableStateOf("") }
    val players = serverPlayers.filter {
        it.name.contains(query, ignoreCase = true) || it.displayName?.contains(query, ignoreCase = true) == true
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        SessionHeader(server)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text(stringResource(R.string.search_players), color = TextSecondary) },
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

        Text(
            text = stringResource(R.string.online_players) + " (" + serverPlayers.size + ")",
            modifier = Modifier.padding(horizontal = 18.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )

        NeonCard(modifier = Modifier.weight(1f).padding(16.dp)) {
            LazyColumn {
                if (players.isEmpty()) item {
                    Text(
                        stringResource(if (!connected) R.string.players_disconnected else if (query.isNotBlank()) R.string.players_no_matches else R.string.players_empty),
                        modifier = Modifier.padding(14.dp), color = TextSecondary,
                    )
                }
                items(players, key = { it.uuid }) { player ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeonAvatar(player.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(player.displayName ?: player.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                            if (player.displayName != null && player.displayName != player.name) {
                                Text(player.name, color = TextSecondary, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(true)
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.online), color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Text(player.pingMs?.let { stringResource(R.string.player_ping, it) } ?: stringResource(R.string.player_ping_unknown), color = TextSecondary, fontSize = 12.sp)

                    }
                }
            }
        }

    }
}

@Composable
private fun NeonAvatar(name: String) {
    NeonCard(
        modifier = Modifier.size(42.dp),
        highlighted = true,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(2).uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

