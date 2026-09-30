package pl.syntaxdevteam.craftconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.ui.components.BrandHeader
import pl.syntaxdevteam.craftconnect.ui.components.NeonCard
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.CrimsonDeep
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun SettingsScreen() {
    var notifications by remember { mutableStateOf(true) }
    var reconnect by remember { mutableStateOf(true) }
    var compression by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BrandHeader()

        SectionTitle(R.string.settings_app_preferences)
        NeonCard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ToggleRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.notifications),
                    stringResource(R.string.notifications_summary),
                    notifications,
                ) { notifications = it }
                Divider()
                ToggleRow(
                    Icons.Rounded.Refresh,
                    stringResource(R.string.auto_reconnect),
                    stringResource(R.string.auto_reconnect_summary),
                    reconnect,
                ) { reconnect = it }
                Divider()
                ValueRow(Icons.Rounded.Settings, stringResource(R.string.theme), stringResource(R.string.theme_value))
                Divider()
                ValueRow(Icons.Rounded.Language, stringResource(R.string.language), stringResource(R.string.language_value))
            }
        }

        SectionTitle(R.string.settings_connection)
        NeonCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            highlighted = true,
        ) {
            Column {
                ValueRow(Icons.Rounded.Settings, stringResource(R.string.protocol_version), stringResource(R.string.protocol_version_value))
                Divider()
                ValueRow(Icons.Rounded.Settings, stringResource(R.string.connection_timeout), stringResource(R.string.connection_timeout_value))
                Divider()
                ValueRow(Icons.Rounded.Refresh, stringResource(R.string.keep_alive), stringResource(R.string.keep_alive_value))
                Divider()
                ToggleRow(
                    Icons.Rounded.Lock,
                    stringResource(R.string.compression),
                    stringResource(R.string.compression_summary),
                    compression,
                ) { compression = it }
            }
        }

        SectionTitle(R.string.settings_account_session)
        NeonCard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                ValueRow(Icons.Rounded.Settings, stringResource(R.string.manage_account), stringResource(R.string.manage_account_summary))
                Divider()
                ValueRow(Icons.Rounded.Lock, stringResource(R.string.saved_servers), stringResource(R.string.saved_servers_summary))
            }
        }

        Text(
            text = stringResource(R.string.prototype_footer),
            color = TextSecondary,
            modifier = Modifier.padding(20.dp),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SectionTitle(id: Int) {
    Text(
        text = stringResource(id),
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Crimson)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = Crimson,
                uncheckedTrackColor = CrimsonDeep,
                uncheckedBorderColor = Border,
            ),
        )
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Crimson)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(value, color = TextSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = Border.copy(alpha = 0.72f))
}
