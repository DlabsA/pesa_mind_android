package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Live `SubscriptionManager` behavior (real active-slot enumeration) isn't exercised here —
 * Robolectric's default shadow reports zero active subscriptions, which exactly matches the
 * safe-default path this suite checks: no stored slots, so `getActiveSlots` degrades to empty and
 * `checkForDrift` degrades to `false`, the same "no mapping set yet" no-op behavior
 * `SmsReceiver`'s fallback relies on for every channel until a user opts in.
 *
 * The drift comparison itself is covered against
 * [SimSlotManager.detectDrift] instead, which takes the live slots as a parameter — so the swap
 * cases (notably two same-carrier SIMs trading trays) are testable without a shadowed
 * `SubscriptionManager` at all.
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

    // --- detectDrift: the pure comparison, exercised without a live SubscriptionManager ---

    private fun slot(
        index: Int,
        carrier: String,
        subscriptionId: Int?,
    ) = SimSlotManager.SimSlot(slotIndex = index, carrierName = carrier, subscriptionId = subscriptionId)

    private fun baseline(
        carrier: String,
        subscriptionId: Int?,
    ) = SimSlotManager.SlotBaseline(carrierName = carrier, subscriptionId = subscriptionId)

    @Test
    fun `two same-carrier SIMs swapping trays is drift even though both carrier names still match`() {
        // The case carrier-name comparison alone cannot see, and the one that matters most: an
        // unprovisioned-MSISDN carrier is unprovisioned for both of the user's lines, so this is
        // exactly when SmsReceiver leans on the stored mapping.
        val baselines =
            mapOf(
                0 to baseline("Safaricom", subscriptionId = 1),
                1 to baseline("Safaricom", subscriptionId = 2),
            )
        val live =
            mapOf(
                0 to slot(0, "Safaricom", subscriptionId = 2),
                1 to slot(1, "Safaricom", subscriptionId = 1),
            )

        assertTrue(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `untouched slots are not drift`() {
        val baselines =
            mapOf(
                0 to baseline("Safaricom", subscriptionId = 1),
                1 to baseline("Airtel", subscriptionId = 2),
            )
        val live =
            mapOf(
                0 to slot(0, "Safaricom", subscriptionId = 1),
                1 to slot(1, "Airtel", subscriptionId = 2),
            )

        assertFalse(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `a changed carrier in a tracked slot is still drift`() {
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = 1))
        val live = mapOf(0 to slot(0, "Airtel", subscriptionId = 2))

        assertTrue(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `a SIM removed from a tracked slot entirely is drift`() {
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = 1))

        assertTrue(SimSlotManager.detectDrift(baselines, emptyMap()))
    }

    @Test
    fun `a legacy baseline with no stored subscription id does not flag drift while the carrier matches`() {
        // Installs that saved a mapping before identity checking existed must not all be blocked
        // behind the drift overlay on their first launch after upgrading.
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = null))
        val live = mapOf(0 to slot(0, "Safaricom", subscriptionId = 7))

        assertFalse(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `a legacy baseline still flags drift on a carrier change`() {
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = null))
        val live = mapOf(0 to slot(0, "Airtel", subscriptionId = 7))

        assertTrue(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `an unreadable live subscription id degrades to carrier comparison rather than false drift`() {
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = 1))
        val live = mapOf(0 to slot(0, "Safaricom", subscriptionId = null))

        assertFalse(SimSlotManager.detectDrift(baselines, live))
    }

    @Test
    fun `a live slot the user never mapped is ignored`() {
        val baselines = mapOf(0 to baseline("Safaricom", subscriptionId = 1))
        val live =
            mapOf(
                0 to slot(0, "Safaricom", subscriptionId = 1),
                1 to slot(1, "Airtel", subscriptionId = 9),
            )

        assertFalse(SimSlotManager.detectDrift(baselines, live))
    }
}
