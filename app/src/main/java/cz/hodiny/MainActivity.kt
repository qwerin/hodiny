package cz.hodiny

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.hodiny.ui.navigation.AppNavigation
import cz.hodiny.ui.theme.HodinyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HodinyTheme {
                AppNavigation()
            }
        }
    }
}
