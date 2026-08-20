package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [SimSlotManager]'s pure DataStore read/write paths — no `ApiClient` dependency at all
 * (this manager is local-only by design, never synced), so plain [runBlocking] here is a style
 * choice for consistency with every other manager test in the repo (`.claude/CLAUDE.md`), not a
 * requirement the way it is for anything touching `ApiClient`.
 *
 * Live `SubscriptionManager` behavior (real active-slot enumeration, a real carrier-name change)
 * isn't exercised here — Robolectric's default shadow reports zero active subscriptions, which
 * exactly matches the safe-default path this suite checks: no stored slots, so `getActiveSlots`
 * degrades to empty and `checkForDrift` degrades to `false`, the same "no mapping set yet" no-op
 * behavior `SmsReceiver`'s fallback relies on for every channel until a user opts in.
 */
@RunWith(RobolectricTestRunner::class)
class SimSlotManagerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SimSlotManager.init(context)
        runBlocking { SimSlotManager.clearAll() }
    }

    // SimSlotManager is a process-lifetime singleton (no reset for `isInitialized()`), so its
    // DataStore file persists across tests in this class within the same JVM — mirrors
    // AccountManagerTest's tearDown for the same reason.
    @After
    fun tearDown() {
        runBlocking { SimSlotManager.clearAll() }
    }

    @Test
    fun `getNumberForSlot is null when nothing saved`() =
        runBlocking {
            assertNull(SimSlotManager.getNumberForSlot(0))
        }

    @Test
    fun `saveSlotNumbers then getNumberForSlot round-trips`() =
        runBlocking {
            SimSlotManager.saveSlotNumbers(context, mapOf(0 to "0755175388", 1 to "0770882499"))
            assertEquals("0755175388", SimSlotManager.getNumberForSlot(0))
            assertEquals("0770882499", SimSlotManager.getNumberForSlot(1))
        }

    @Test
    fun `isDriftDetected is false by default`() =
        runBlocking {
            assertFalse(SimSlotManager.isDriftDetected())
        }

    @Test
    fun `checkForDrift is false with no active slots and no stored mapping`() =
        runBlocking {
            // Robolectric reports zero active subscriptions by default — this is the same
            // "nothing to compare against yet" state a real single/no-permission device hits.
            assertFalse(SimSlotManager.checkForDrift(context))
            assertFalse(SimSlotManager.isDriftDetected())
        }

    @Test
    fun `getActiveSlots does not crash with no active subscriptions`() =
        runBlocking {
            assertEquals(emptyList<SimSlotManager.SimSlot>(), SimSlotManager.getActiveSlots(context))
        }
}
