package cc.dlabs.pesamind

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import androidx.work.Configuration
import cc.dlabs.pesamind.core.billing.PlayBillingManager
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.DebtCreditRepository
import cc.dlabs.pesamind.core.data.ProcessedMessageRepository
import cc.dlabs.pesamind.core.data.SavingGoalRepository
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.database.migration.PrefsToRoomMigrator
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.di.WorkerFactoryEntryPoint
import cc.dlabs.pesamind.core.navigation.PaymentDeepLink
import cc.dlabs.pesamind.core.navigation.PesaMindNavGraph
import cc.dlabs.pesamind.core.navigation.SimSlotDeepLink
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.AuthManager
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.storage.PaymentManager
import cc.dlabs.pesamind.core.storage.SimSlotManager
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
        PlayBillingManager.init(this)
        SubscriptionResumer.init(this)
        SimSlotManager.init(this)
        ChannelRepository.init(this)
        TransactionRepository.init(this)
        BudgetRepository.init(this)
        ProcessedMessageRepository.init(this)
        DebtCreditRepository.init(this)
        SavingGoalRepository.init(this)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A payment authorization return may be what launched us (cold start after
        // the customer completed 3DS in a browser tab).
        PaymentDeepLink.handle(intent)
        SimSlotDeepLink.handle(intent)

        // Independent of any SMS: a live check of whether the SIM physically in each declared
        // slot still matches what the user told us in Settings > SIM Slots. Not run here
        // directly — onResume always fires immediately after onCreate too, and does it there
        // (see override below) so cold-start and every later resume share one code path
        // instead of double-checking on launch.

        setContent {
            val isDarkMode = ThemeManager.darkModeFlow.collectAsState().value
            PesaMindTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                PesaMindNavGraph(navController = navController)
            }
        }

        // Start the message monitoring service to ensure SMS monitoring runs in the background.
        // No permission is requested here — see SmsTracingPermissions for why the SMS/phone
        // prompts live in the screens that explain them instead.
        startMessageMonitoringService()
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
        SimSlotDeepLink.handle(intent)
    }

    /**
     * Every return to the foreground — not just the [onCreate] cold-start path — re-runs the
     * SIM-slot drift check, since a swap made while this app was backgrounded is otherwise
     * never caught until the process happens to be killed and relaunched. `PesaMindNavGraph`
     * observes [SimSlotManager.driftBlocking] and blocks the app behind a non-dismissible form
     * whenever this finds (or still has an unresolved) drift.
     */
    override fun onResume() {
        super.onResume()
        runSimSlotDriftCheck()
    }

    private fun runSimSlotDriftCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SimSlotManager.refreshDriftBlockingState(this@MainActivity)
            } catch (e: Exception) {
                Log.w(TAG, "SIM slot drift check failed: ${e.message}", e)
            }
        }
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

}
