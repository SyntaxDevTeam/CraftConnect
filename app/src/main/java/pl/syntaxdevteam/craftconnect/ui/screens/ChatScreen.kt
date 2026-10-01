package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.model.ReceivedChatMessage
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.ui.components.GlowButton
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.components.SessionHeader
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceRaised
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun ChatScreen(server: ServerProfile, messages: List<ReceivedChatMessage>, connected: Boolean, onSend: (String) -> Unit = {}) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && (listState.layoutInfo.totalItemsCount == 0 || (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= messages.size - 3)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        SessionHeader(server)

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlowButton(stringResource(R.string.tab_chat), onClick = { })
            GlowButton(stringResource(R.string.tab_commands), onClick = { })
            GlowButton(stringResource(R.string.tab_console), onClick = { })
        }

        NeonCard(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (messages.isEmpty()) item { Text(stringResource(R.string.chat_empty), color = TextSecondary) }
                items(messages, key = { it.id }) { ChatLine(it) }
            }
        }

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                enabled = connected,
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(stringResource(R.string.chat_placeholder), color = TextSecondary) },
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
            IconButton(
                enabled = connected && input.isNotBlank(),
                onClick = {
                    val value = input.trim()
                    if (value.isNotEmpty()) {
                        onSend(value)
                        input = ""
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = Crimson)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("/lobby", "/msg", "/help").forEach { command ->
                GlowButton(command, Modifier.weight(1f), onClick = { input = command })
            }
        }
    }
}

@Composable
private fun ChatLine(message: ReceivedChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(message.content, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(
            android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(message.receivedAtEpochMillis)),
            color = TextSecondary, fontSize = 10.sp,
        )
    }
}


