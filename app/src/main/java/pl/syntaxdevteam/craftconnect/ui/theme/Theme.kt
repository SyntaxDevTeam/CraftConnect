package pl.syntaxdevteam.craftconnect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Crimson = Color(0xFFFF3546)
val CrimsonSoft = Color(0xFFFF6B76)
val CrimsonDeep = Color(0xFF32070C)

val AppBackground = Color(0xFF08090B)
val SurfaceBase = Color(0xFF101114)
val SurfaceRaised = Color(0xFF17191D)
val Border = Color(0xFF30333A)

val TextPrimary = Color(0xFFF5F5F6)
val TextSecondary = Color(0xFF969AA4)
val Online = Color(0xFF39DF78)
val Warning = Color(0xFFFFC857)

private val CraftConnectColors = darkColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = CrimsonDeep,
    onPrimaryContainer = TextPrimary,
    secondary = CrimsonSoft,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = Border,
)

@Composable
fun CraftConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CraftConnectColors,
        typography = Typography(),
        content = content,
    )
}
