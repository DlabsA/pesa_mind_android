package cc.dlabs.pesamind.features.settings.notifications

import android.content.Context
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.home.TYPE_EXPENSE
import cc.dlabs.pesamind.features.home.TYPE_INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * First real-sample-backed coverage for [SMSMessageProcessor]'s parsers — previously these
 * regexes had zero test coverage and were, per their own doc comments, unverified against any
 * real Airtel/MTN/Stanbic message (ADR-0004 Phase 0). Every fixture below is a verbatim SMS body
 * observed in production data.
 *
 * [context] and [viewModel] are Mockito mocks purely to satisfy [SMSMessageProcessor]'s
 * constructor — the functions under test never touch either. Using a real [TransactionViewModel]
 * here would trigger its `init` block's `viewModelScope.launch { ... }` calls against production
 * singletons (the exact landmine documented in this repo's `.claude/CLAUDE.md` Testing section);
 * a Mockito mock is never constructed via the real constructor, so that `init` block never runs.
 */
class SMSMessageProcessorParsingTest {
    private val context = mock<Context>()
    private val viewModel = mock<TransactionViewModel>()
    private val processor = SMSMessageProcessor(context, viewModel)

    // ── Airtel: expenses ────────────────────────────────────────────────────

    @Test
    fun `airtel PAID message parses as expense with correct amount and TID`() {
        val content = "PAID.TID 153132129509. UGX 550 to BUSINESS Charge UGX 0. Bal UGX 20,604. 03-August-2026 17:54"
        val parsed = processor.parseAirtelMessage(content)
        assertEquals(550.0, parsed?.amount)
        assertEquals(TYPE_EXPENSE, parsed?.type)
        assertEquals("153132129509", parsed?.providerTransactionId)
    }

    @Test
    fun `airtel PAID message ignores charge and balance figures`() {
        val content = "PAID.TID 153569609897. UGX 32,029 to CELLULANT UGANDA LTD Charge UGX 750. Bal UGX 70,834. 09-August-2026 16:12"
        val parsed = processor.parseAirtelMessage(content)
        assertEquals(32029.0, parsed?.amount)
        assertEquals(TYPE_EXPENSE, parsed?.type)
    }

    @Test
    fun `airtel FAILED message does not parse as a transaction`() {
        val content = "FAILED. TID 153245568122 FAILED. Insufficient funds."
        assertNull(processor.parseAirtelMessage(content))
    }

    // ── Airtel: income ──────────────────────────────────────────────────────

    @Test
    fun `airtel RECEIVED message still parses as income`() {
        val content =
            "RECEIVED UGX 1,000 from 256789404730,CONRAD AKANKWASA KAKURU,Hehfhf fhfhdjfjfj. Balance UGX 4,613. TID:153038584469."
        val parsed = processor.parseAirtelMessage(content)
        assertEquals(1000.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
        assertEquals("153038584469", parsed?.providerTransactionId)
    }

    @Test
    fun `airtel cash deposit with Trans ID variant parses as income and captures TID`() {
        val content =
            "Cash deposit of UGX 80,000 from STANBIC. Balance UGX 103,613. Trans ID:153569568145."
        val parsed = processor.parseAirtelMessage(content)
        assertEquals(80000.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
        assertEquals("153569568145", parsed?.providerTransactionId)
    }

    @Test
    fun `airtel quickloan deposit parses as income with amount and TID`() {
        val content =
            "Success! Quickloan UGX 2,900 deposited into your Airtel money account. Please " +
                "dial *185# to complete your intended transaction. TID 153245590793"
        val parsed = processor.parseAirtelMessage(content)
        assertEquals(2900.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
        assertEquals("153245590793", parsed?.providerTransactionId)
    }

    // ── Stanbic ─────────────────────────────────────────────────────────────

    @Test
    fun `stanbic message with positive amount parses as income`() {
        val content =
            "Stanbic Bank Uganda : A transaction of UGX 3,000,000.00, AccNr : XX5285 has been " +
                "completed via  HEAD OFFICE on 04/08/26 08:47:56. IMMEDIATELY CALL 0800150150 " +
                "TO REPORT PHONE THEFT!"
        val parsed = processor.parseStanbicMessage(content)
        assertEquals(3000000.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
    }

    @Test
    fun `stanbic message with negative amount parses as expense with positive stored amount`() {
        val content =
            "Stanbic Bank Uganda : A transaction of UGX -480.00, AccNr : XX5285 has been " +
                "completed via  E-Banking/M-Banking on 05/08/26 17:38:35. IMMEDIATELY CALL " +
                "0800150150 TO REPORT PHONE THEFT!"
        val parsed = processor.parseStanbicMessage(content)
        assertEquals(480.0, parsed?.amount)
        assertEquals(TYPE_EXPENSE, parsed?.type)
    }

    @Test
    fun `stanbic phone theft footer fragment does not parse as a transaction`() {
        assertNull(processor.parseStanbicMessage("0800150150 TO REPORT PHONE THEFT!"))
    }

    @Test
    fun `stanbic CCN login message does not parse as a transaction`() {
        assertNull(processor.parseStanbicMessage("Stanbic Uganda: Your CCN for Login is 53130. Help: 0800150150"))
    }

    // ── Centenary ───────────────────────────────────────────────────────────

    @Test
    fun `centenary message with negative amount parses as expense`() {
        val content =
            "CENTENARY: Dear REBECCA, a trxn of -210,000 on your A/C **663 on 20-04-2026 at " +
                "17:43. Bal:296,349 (ATM WITHDRAWAL VISA /Ebanking). Call 0800200555"
        val parsed = processor.parseCentenaryMessage(content)
        assertEquals(210000.0, parsed?.amount)
        assertEquals(TYPE_EXPENSE, parsed?.type)
    }

    @Test
    fun `centenary message with positive amount parses as income`() {
        val content =
            "CENTENARY: Dear REBECCA, a trxn of 471,500 on your A/C **663 on 24-04-2026 at " +
                "17:41. Bal:727,849 ( APRIL 2026 END OF MONTH PAY/Finance). Call 0800200555"
        val parsed = processor.parseCentenaryMessage(content)
        assertEquals(471500.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
    }

    @Test
    fun `centenary Debit trxn variant without Bal field still parses as expense`() {
        val content =
            "CENTENARY: Dear REBECCA, a Debit trxn of -20,000 on your A/C **663 on 20-05-2026 " +
                "at 08:32.  (ATM WITHDRAWAL VISA /Ebanking). Call 0800200555/0800335344."
        val parsed = processor.parseCentenaryMessage(content)
        assertEquals(20000.0, parsed?.amount)
        assertEquals(TYPE_EXPENSE, parsed?.type)
    }

    @Test
    fun `centenary reversal message parses as income for the reversed amount`() {
        val content =
            "Centenary. Dear MS. NAKINTU REBECCA, your last transaction of 210,000 has been " +
                "reversed successfully. For details call 0800200555"
        val parsed = processor.parseCentenaryMessage(content)
        assertEquals(210000.0, parsed?.amount)
        assertEquals(TYPE_INCOME, parsed?.type)
    }

    @Test
    fun `centenary fraud alert footer does not parse as a transaction`() {
        val content =
            "Dear Customer, be fraud alert. Never share your account details, PIN or OTP with " +
                "anyone. When you lose your phone, report to the Bank immediately. Stay vigilant"
        assertNull(processor.parseCentenaryMessage(content))
    }

    // ── Sender-id normalization regression guard ───────────────────────────

    @Test
    fun `Stanbic sender id normalizes to the canonical MessageSender constant`() {
        assertEquals(MessageSender.STANBIC_BANK, MessageSender.normalizeOrNull("Stanbic"))
    }

    @Test
    fun `Centenary sender id normalizes to the canonical MessageSender constant`() {
        assertEquals(MessageSender.CENTENARY_BANK, MessageSender.normalizeOrNull("Centenary"))
    }
}
