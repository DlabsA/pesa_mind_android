package cc.dlabs.pesamind

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import cc.dlabs.pesamind.core.navigation.PesaMindNavGraph
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.theme.PesaMindTheme
import android.app.Application
import android.os.Build
import androidx.core.app.ActivityCompat
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage


private val MainActivity.PERMISSION_REQUEST_CODE: Int
    get() = 1001

class PesaMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        AccountManager.init(this)
        ChannelManager.init(this)
        NotificationStorage.init(this)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ),
            PERMISSION_REQUEST_CODE
        )
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