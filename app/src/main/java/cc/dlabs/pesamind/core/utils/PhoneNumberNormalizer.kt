package cc.dlabs.pesamind.core.utils

/**
 * Canonical-form phone number key used wherever a receiving/account number needs to be
 * compared across sources that disagree on prefix formatting (`+256770...`, `256770...`,
 * `0770...` all refer to the same line). Used by [cc.dlabs.pesamind.core.storage.ChannelManager]
 * (SMS receiving-number channel matching), [cc.dlabs.pesamind.core.data.ChannelRepository]
 * (channel create/lookup), and channel onboarding/settings screens (user-entered account
 * numbers) — one shared definition so all three can never drift apart on what "the same number"
 * means.
 */
object PhoneNumberNormalizer {
    private const val LOCAL_DIGIT_COUNT = 9

    /** Last [LOCAL_DIGIT_COUNT] digits of [raw], which is prefix-agnostic (country code vs.
     * leading `0` vs. neither). Null/blank/non-digit input returns null — callers must supply
     * their own sentinel for "unresolved" rather than treating that as a real key. */
    fun normalize(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() }?.ifBlank { null } ?: return null
        return digits.takeLast(LOCAL_DIGIT_COUNT)
    }
}
