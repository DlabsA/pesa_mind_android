package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

@Composable
fun LockSetupScreen(navController: NavHostController) {
    val teal = Color(0xFF1A9E8F)
    val navy = Color(0xFF1E2240)
    val grey = Color(0xFFE5E5E5)


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Set up your lock",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = navy
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Choose either a PIN or a pattern. You’ll use it the next time you open the app.",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { navController.navigate(Routes.PinSetup.route) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = teal)
        ) {
            Text("Set PIN", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate(Routes.PatternSetup.route) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = navy)
        ) {
            Text("Set Pattern", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { navController.navigate(Routes.Dashboard.route) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = grey)
        ) {
            Text("No Lockup set ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "You can change this later from settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

