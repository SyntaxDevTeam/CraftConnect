package pl.syntaxdevteam.craftconnect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.syntaxdevteam.craftconnect.AppDestination
import pl.syntaxdevteam.craftconnect.R
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.ui.theme.AppBackground
import pl.syntaxdevteam.craftconnect.ui.theme.Border
import pl.syntaxdevteam.craftconnect.ui.theme.Crimson
import pl.syntaxdevteam.craftconnect.ui.theme.CrimsonDeep
import pl.syntaxdevteam.craftconnect.ui.theme.Online
import pl.syntaxdevteam.craftconnect.ui.theme.SurfaceBase
import pl.syntaxdevteam.craftconnect.ui.theme.TextPrimary
import pl.syntaxdevteam.craftconnect.ui.theme.TextSecondary

@Composable
fun CraftBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1B080C),
                        AppBackground,
                        AppBackground,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Crimson.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        radius = 760f,
                    ),
                ),
        )
        content()
    }
}

@Composable
fun CraftMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(42.dp)) {
        val width = size.width
        val height = size.height
        val top = Offset(width / 2f, 2f)
        val right = Offset(width - 3f, height * 0.28f)
        val bottom = Offset(width / 2f, height - 2f)
        val left = Offset(3f, height * 0.28f)
        val center = Offset(width / 2f, height * 0.54f)

        val outline = Path().apply {
            moveTo(top.x, top.y)
            lineTo(right.x, right.y)
            lineTo(right.x, height * 0.72f)
            lineTo(bottom.x, bottom.y)
            lineTo(left.x, height * 0.72f)
            lineTo(left.x, left.y)
            close()
        }

        drawPath(outline, color = Crimson, style = Stroke(width = 3.2f))
        drawLine(Crimson, left, center, strokeWidth = 3.2f)
        drawLine(Crimson, right, center, strokeWidth = 3.2f)
        drawLine(Crimson, center, bottom, strokeWidth = 3.2f)
        drawLine(Crimson.copy(alpha = 0.8f), top, center, strokeWidth = 3.2f)
    }
}

@Composable
fun BrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.craftconnect_logo),
            contentDescription = stringResource(R.string.app_logo),
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Craft", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Connect", color = Crimson, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.brand_tagline),
                color = TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        modifier = modifier.fillMaxWidth().then(clickableModifier),
        shape = shape,
        color = if (highlighted) CrimsonDeep.copy(alpha = 0.82f) else SurfaceBase.copy(alpha = 0.96f),
        border = BorderStroke(
            1.dp,
            if (highlighted) Crimson.copy(alpha = 0.78f) else Border.copy(alpha = 0.92f),
        ),
        shadowElevation = if (highlighted) 7.dp else 1.dp,
        content = content,
    )
}

@Composable
fun GlowButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Crimson.copy(alpha = 0.92f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = CrimsonDeep,
            contentColor = TextPrimary,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(if (online) Online else TextSecondary, CircleShape),
    )
}

@Composable
fun ServerGlyph(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(Crimson, CrimsonDeep)),
                RoundedCornerShape(13.dp),
            )
            .border(1.dp, Crimson.copy(alpha = 0.76f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
    }
}

@Composable
fun SessionHeader(server: ServerProfile) {
    NeonCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        highlighted = true,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerGlyph(server.name, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(server.address, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(server.online)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (server.online) stringResource(R.string.connected) else stringResource(R.string.offline),
                        color = if (server.online) Online else TextSecondary,
                        fontSize = 12.sp,
                    )
                    server.pingMs?.let {
                        Text("  ·  " + it + " ms", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CraftBottomBar(destination: AppDestination, onDestination: (AppDestination) -> Unit) {
    NavigationBar(containerColor = Color(0xF20B0C0F), tonalElevation = 0.dp) {
        BottomItem(AppDestination.Servers, stringResource(R.string.nav_servers), Icons.Rounded.Home, destination, onDestination)
        BottomItem(AppDestination.Chat, stringResource(R.string.nav_chat), Icons.AutoMirrored.Rounded.Chat, destination, onDestination)
        BottomItem(AppDestination.Players, stringResource(R.string.nav_players), Icons.Rounded.Person, destination, onDestination)
        BottomItem(AppDestination.Settings, stringResource(R.string.nav_settings), Icons.Rounded.Settings, destination, onDestination)
    }
}

@Composable
private fun RowScope.BottomItem(
    item: AppDestination,
    label: String,
    icon: ImageVector,
    selected: AppDestination,
    onDestination: (AppDestination) -> Unit,
) {
    NavigationBarItem(
        selected = selected == item,
        onClick = { onDestination(item) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 10.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Crimson,
            selectedTextColor = Crimson,
            indicatorColor = CrimsonDeep,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary,
        ),
    )
}
