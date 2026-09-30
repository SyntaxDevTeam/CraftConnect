package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
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
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.PlayerRole
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.ui.components.GlowButton
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.components.SessionHeader
import pl.syntaxdevteam.craftconnect.ui.components.StatusDot
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.Online
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceRaised
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary
import pl.syntaxdevteam.craftconnect.ui.theme.Warning

@Composable
fun PlayersScreen(server: ServerProfile) {
    var query by remember { mutableStateOf("") }
    val players = DemoRepository.players.filter { it.name.contains(query, ignoreCase = true) }

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
            text = stringResource(R.string.online_players) + " (" + players.size + ")",
            modifier = Modifier.padding(horizontal = 18.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )

        NeonCard(modifier = Modifier.weight(1f).padding(16.dp)) {
            LazyColumn {
                items(players, key = { it.name }) { player ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeonAvatar(player.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(player.name, color = roleColor(player.role), fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(true)
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.online), color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Text(player.pingMs.toString() + " ms", color = TextSecondary, fontSize = 12.sp)
                        IconButton(onClick = { }) {
                            Icon(Icons.Rounded.MoreVert, null, tint = TextSecondary)
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.quick_actions),
            modifier = Modifier.padding(start = 18.dp, bottom = 8.dp),
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                stringResource(R.string.action_message),
                stringResource(R.string.action_teleport),
                stringResource(R.string.action_kick),
                stringResource(R.string.action_ban),
            ).forEach { label ->
                GlowButton(label, Modifier.weight(1f), onClick = { })
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

private fun roleColor(role: PlayerRole) = when (role) {
    PlayerRole.ADMIN -> Crimson
    PlayerRole.MVP -> Warning
    PlayerRole.VIP -> Online
    PlayerRole.PLAYER -> TextPrimary
}
