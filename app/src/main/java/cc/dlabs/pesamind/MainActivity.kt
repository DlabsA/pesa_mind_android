package cc.dlabs.pesamind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import cc.dlabs.pesamind.core.navigation.PesaMindNavGraph
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.theme.PesaMindTheme
import android.app.Application
import cc.dlabs.pesamind.core.storage.AccountManager

class PesaMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        AccountManager.init(this)
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PesaMindTheme {
                val navController = rememberNavController()
                PesaMindNavGraph(navController = navController)
            }
        }
    }
}