package cc.dlabs.pesamind

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import androidx.work.Configuration
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.ProcessedMessageRepository
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.database.migration.PrefsToRoomMigrator
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.di.WorkerFactoryEntryPoint
import cc.dlabs.pesamind.core.navigation.PaymentDeepLink
import cc.dlabs.pesamind.core.navigation.PesaMindNavGraph
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.AuthManager
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.storage.PaymentManager
import cc.dlabs.pesamind.core.storage.SyncMetadataManager
import cc.dlabs.pesamind.core.storage.ThemeManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import cc.dlabs.pesamind.core.theme.PesaMindTheme
import cc.dlabs.pesamind.features.settings.notifications.MessageMonitoringService
import cc.dlabs.pesamind.features.subscription.SubscriptionResumer
import dagger.hilt.EntryPoints
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class PesaMindApp : Application(), Configuration.Provider {
    // Configuration.Provider is a plain interface property, not `@Inject lateinit var`
    // field injection — see WorkerFactoryEntryPoint's doc comment for why that distinction
    // matters on this project's pinned Dagger/Kotlin versions.
    override val workManagerConfiguration: Configuration
        get() {
            val workerFactory = EntryPoints.get(this, WorkerFactoryEntryPoint::class.java).workerFactory()
            return Configuration.Builder().setWorkerFactory(workerFactory).build()
        }

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        AccountManager.init(this)
        AuthManager.init(this)
        ChannelManager.init(this)
        SyncScheduler.init(this)
        NotificationStorage.init(this)
        ThemeManager.init(this)
        SyncMetadataManager.init(this)
        PaymentManager.init(this)
        SubscriptionResumer.init(this)
        // Room-backed repositories (ADR-0004 Slice A1/B) — ChannelRepository/
        // TransactionRepository/BudgetRepository are the source of truth their respective
        // ViewModels read and write through.
        ChannelRepository.init(this)
        TransactionRepository.init(this)
        BudgetRepository.init(this)
        ProcessedMessageRepository.init(this)

        // One-time prefs-blob -> Room import (idempotent, safe to fire on every launch).
        // See docs/decisions/ADR-0004-offline-first.md. Caught, not propagated: this runs
        // unsupervised at process startup, and the in-transaction rollback that keeps a
        // failed attempt retriable on the *next* launch is worthless if an uncaught throw
        // here crashes *this* launch before the app ever renders a frame.
        val database = EntryPoints.get(this, DatabaseEntryPoint::class.java).database()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrefsToRoomMigrator.migrateIfNeeded(this@PesaMindApp, database)
            } catch (e: Exception) {
                Log.e("PesaMindApp", "Prefs -> Room migration failed; will retry next launch", e)
            }
        }

        // Settle a subscription payment the user walked away from. Stands in for a
        // payment webhook: a mobile money PIN prompt can be approved seconds after
        // the app is backgrounded, at which point nothing is polling and the backend
        // can't push us the result. See SubscriptionResumer.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SubscriptionResumer.onAppStart()
            } catch (e: Exception) {
                Log.e("PesaMindApp", "Pending payment check failed; will retry next launch", e)
            }
        }

        // Outbox drain + pull worker (ADR-0004 Slice A2): periodic background cadence, plus
        // an expedited run the moment connectivity comes back (this flow also seeds with the
        // current state on collection, so a cold start that's already online triggers one too).
        SyncScheduler.schedulePeriodic()
        CoroutineScope(Dispatchers.IO).launch {
            NetworkMonitor(this@PesaMindApp).isConnected.collect { connected ->
                if (connected) SyncScheduler.triggerSyncNow()
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
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

        // A payment authorization return may be what launched us (cold start after
        // the customer completed 3DS in a browser tab).
        PaymentDeepLink.handle(intent)

        setContent {
            val isDarkMode = ThemeManager.darkModeFlow.collectAsState().value
            PesaMindTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                PesaMindNavGraph(navController = navController)
            }
        }

        // Start the message monitoring service to ensure SMS monitoring runs in the background
        startMessageMonitoringService()

        requestRequiredPermissions()
    }

    /**
     * With `launchMode="singleTask"`, the payment deep link is delivered here rather
     * than through a fresh [onCreate] — the existing task (and its navigation back
     * stack, including the checkout screen the customer left) is reused.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() in step with what was actually delivered.
        setIntent(intent)
        PaymentDeepLink.handle(intent)
    }

    /**
     * Starts the MessageMonitoringService to ensure continuous background SMS monitoring
     * even when the main app UI is closed.
     */
    private fun startMessageMonitoringService() {
        try {
            val serviceIntent = Intent(this, MessageMonitoringService::class.java)
            startForegroundService(serviceIntent)
            Log.d(TAG, "MessageMonitoringService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MessageMonitoringService: ${e.message}", e)
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest =
            buildRequiredPermissions().filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            return
        }

        // Check if we should show rationale for any of the not-granted permissions
        val needsRationale =
            permissionsToRequest.any { permission ->
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
     * Build the list of permissions your app needs.
     * On API 33+ (Tiramisu), `SubscriptionManager.getPhoneNumber()` — used by
     * `SmsReceiver.getReceivingSimInfo()` to record which MSISDN a mobile money SMS
     * arrived on — requires READ_PHONE_NUMBERS specifically; READ_PHONE_STATE alone
     * throws SecurityException there and the number silently falls back to "Unknown".
     * Both are requested so the pre-33 (`subscriptionInfo.number`) and 33+ paths both work.
     * POST_NOTIFICATIONS is also API 33+-only (a no-op permission before that) — without it
     * being requested here, `SMSMessageProcessor.showLocalNotification` silently skips every
     * transaction notification, same "declared in the manifest but never actually requested"
     * gap as READ_PHONE_NUMBERS had.
     */
    private fun buildRequiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS)
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun handleDeniedPermissions(denied: Set<String>) {
        when {
            Manifest.permission.RECEIVE_SMS in denied -> {
                // Core feature is broken – show a non-dismissible dialog
                Log.e(TAG, "RECEIVE_SMS denied — app cannot monitor transactions")
                showMandatorySettingsDialog()
            }
            Manifest.permission.READ_PHONE_STATE in denied || Manifest.permission.READ_PHONE_NUMBERS in denied -> {
                // SIM/MSISDN identification will fail – optional, just log and continue
                Log.w(TAG, "READ_PHONE_STATE/READ_PHONE_NUMBERS denied — SIM number identification disabled")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                Manifest.permission.POST_NOTIFICATIONS in denied -> {
                // Transaction/alert notifications will fail – optional, just log and continue
                Log.w(TAG, "POST_NOTIFICATIONS denied — transaction notifications disabled")
            }
        }
    }

    /**
     * Non-dismissible dialog that forces the user to go to Settings
     * to grant the permission manually.
     */
    private fun showMandatorySettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "To receive and identify SMS transactions, you must grant the " +
                    "'Receive SMS' permission. Please enable it in the app settings.",
            )
            .setCancelable(false) // Cannot be dismissed by back button
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
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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
                    "the app to function correctly.",
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
