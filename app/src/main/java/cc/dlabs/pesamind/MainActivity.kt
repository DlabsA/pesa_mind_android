package cc.dlabs.pesamind

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import cc.dlabs.pesamind.core.navigation.PesaMindNavGraph
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.storage.ThemeManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.theme.PesaMindTheme
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PesaMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        AccountManager.init(this)
        ChannelManager.init(this)
        NotificationStorage.init(this)
        ThemeManager.init(this)
    }
}
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PESAMIND"
    }
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                handleDeniedPermissions(denied)
            } else {
                Log.d(TAG, "All permissions granted")
                // Permissions granted – continue with SMS monitoring
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode = ThemeManager.darkModeFlow.collectAsState().value
            PesaMindTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                PesaMindNavGraph(navController = navController)
            }
        }
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = buildRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            return
        }

        // Check if we should show rationale for any of the not-granted permissions
        val needsRationale = permissionsToRequest.any { permission ->
            shouldShowRequestPermissionRationale(permission)
        }

        if (needsRationale) {
            // Show a rationale dialog before launching the permission request
            showRationaleDialog {
                // User clicked "Continue" – launch the request
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } else {
            // No rationale needed – request directly
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    /**
     * Handle permanently denied permissions or partial denials.
     */

    /**
     * Build the list of permissions your app needs.
     * READ_PHONE_STATE is optional for SIM identification (API 26+ READ_PHONE_NUMBERS
     * is also acceptable, but we'll stick with READ_PHONE_STATE for broad compatibility).
     * Add READ_PHONE_NUMBERS if targeting newer APIs.
     */
    private fun buildRequiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        return permissions
    }

    private fun handleDeniedPermissions(denied: Set<String>) {
        when {
            Manifest.permission.RECEIVE_SMS in denied -> {
                // Core feature is broken – show a non-dismissible dialog
                Log.e(TAG, "RECEIVE_SMS denied — app cannot monitor transactions")
                showMandatorySettingsDialog(Manifest.permission.RECEIVE_SMS)
            }
            Manifest.permission.READ_PHONE_STATE in denied -> {
                // SIM identification will fail – optional, just log and continue
                Log.w(TAG, "READ_PHONE_STATE denied — SIM slot identification disabled")
            }
        }
    }

    /**
     * Non-dismissible dialog that forces the user to go to Settings
     * to grant the permission manually.
     */
    private fun showMandatorySettingsDialog(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "To receive and identify SMS transactions, you must grant the " +
                        "'Receive SMS' permission. Please enable it in the app settings."
            )
            .setCancelable(false)               // Cannot be dismissed by back button
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            // Optional negative button that does nothing (keeps the dialog up)
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
            .apply {
                setCanceledOnTouchOutside(false) // Cannot be dismissed by tapping outside
                show()
            }
    }

    /**
     * Opens the app's detail settings screen where the user can grant permissions.
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /**
     * Rationale dialog that explains why we need certain permissions.
     * @param onContinue callback invoked when the user agrees to proceed.
     */
    private fun showRationaleDialog(onContinue: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Needed")
            .setMessage(
                "This app needs access to incoming SMS to monitor transactions, " +
                        "and phone state permission to identify the SIM card that " +
                        "received the message. These permissions are essential for " +
                        "the app to function correctly."
            )
            .setCancelable(true)
            .setPositiveButton("Continue") { _, _ ->
                onContinue()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // User cancelled – permission request will not be launched
                Log.d(TAG, "User cancelled the permission request from rationale dialog")
            }
            .show()
    }
}