package cc.dlabs.pesamind.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.Account
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(rootNav: NavHostController) {
    val teal = Color(0xFF1A9E8F)
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    var account by remember { mutableStateOf<Account?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val storedAccount = AccountManager.getAccount()
            if (storedAccount.username.isBlank() && storedAccount.email.isBlank()) {
                account = null
                accountError = "Account details not found. Please sign in again."
            } else {
                account = storedAccount
                accountError = null
            }
        } catch (_: Exception) {
            account = null
            accountError = "Failed to load account details"
        }
    }

    val displayName = account?.username.orEmpty()
    val displayEmail = account?.email.orEmpty()
    val initial = displayName.firstOrNull()?.uppercase() ?: "U"

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            TokenManager.clearTokens()
                            TokenManager.clearLock()
                            AccountManager.clearAccount()
                            rootNav.navigate(Routes.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                ) {
                    Text("Log Out", color = Color(0xFFE74C3C), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        // ── Header ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar circle with initial
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = teal.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initial,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = teal
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (accountError == null) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = displayEmail,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = accountError ?: "",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── Section: Account ─────────────────────────────────
        SettingsSectionHeader(title = "Account")

        SettingsRow(
            icon = Icons.Filled.Person,
            iconTint = teal,
            title = "Account",
            subtitle = "Username, email",
            onClick = { rootNav.navigate(Routes.AccountSettings.route) }
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.Security,
            iconTint = teal,
            title = "Security",
            subtitle = "PIN, Pattern lock",
            onClick = { rootNav.navigate(Routes.SecuritySettings.route) }
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.Notifications,
            iconTint = teal,
            title = "Notifications",
            subtitle = "Manage alerts and reminders",
            onClick = { /* TODO: navController.navigate(Routes.NotificationSettings.route) */ }
        )

        Spacer(Modifier.height(8.dp))

        // ── Section: Preferences ─────────────────────────────
        SettingsSectionHeader(title = "Preferences")

        SettingsRow(
            icon = Icons.Filled.Palette,
            iconTint = teal,
            title = "Appearance",
            subtitle = "Dark mode, theme",
            onClick = { /* TODO */ }
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.Language,
            iconTint = teal,
            title = "Language",
            subtitle = "English",
            onClick = { /* TODO */ }
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.AttachMoney,
            iconTint = teal,
            title = "Currency",
            subtitle = "UGX — Ugandan Shilling",
            onClick = { /* TODO */ }
        )

        Spacer(Modifier.height(8.dp))

        // ── Section: About ───────────────────────────────────
        SettingsSectionHeader(title = "About")

        SettingsRow(
            icon = Icons.Filled.Info,
            iconTint = Color.Gray,
            title = "About PesaMind",
            subtitle = "Version 1.0.0",
            onClick = { /* TODO */ },
            showChevron = false
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.Policy,
            iconTint = Color.Gray,
            title = "Privacy Policy",
            subtitle = null,
            onClick = { /* TODO */ }
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Filled.HelpOutline,
            iconTint = Color.Gray,
            title = "Help & Support",
            subtitle = null,
            onClick = { /* TODO */ }
        )

        Spacer(Modifier.height(16.dp))

        // ── Log out ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogoutDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Logout,
                contentDescription = "Log Out",
                tint = Color(0xFFE74C3C),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Log Out",
                color = Color(0xFFE74C3C),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Reusable components ──────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Gray,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(
            start = 20.dp,
            top = 16.dp,
            bottom = 4.dp
        )
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon box
        Surface(
            modifier = Modifier.size(38.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = iconTint.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Chevron
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = Color.LightGray.copy(alpha = 0.5f)
    )
}