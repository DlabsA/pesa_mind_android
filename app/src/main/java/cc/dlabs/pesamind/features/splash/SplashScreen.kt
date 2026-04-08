package cc.dlabs.pesamind.features.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.storage.TokenManager.LockState
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(1800)

        val destination = when (TokenManager.getLockState()) {
            LockState.NONE -> if (TokenManager.isLoggedIn()) Routes.LockSetup.route else Routes.Login.route
            LockState.PIN -> Routes.PinUnlock.route
            LockState.PATTERN -> Routes.PatternUnlock.route
        }

        navController.navigate(destination) {
            popUpTo(Routes.Splash.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E2240)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Text("▲▲▲", color = Color(0xFF2ECC71), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(
                "Pesa\nMind",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Empowering Your Prosperity Path",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}