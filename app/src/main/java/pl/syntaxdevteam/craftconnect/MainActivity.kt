package pl.syntaxdevteam.craftconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pl.syntaxdevteam.craftconnect.ui.theme.CraftConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CraftConnectTheme {
                CraftConnectApp()
            }
        }
    }
}
