package cc.dlabs.pesamind.core.ui

/**
 * Canonical copy source for "Lent & Borrowed" / "Saving Goals" — this app has no live
 * `strings.xml` usage anywhere (confirmed: zero `R.string` references), so every screen
 * hardcodes its own Compose `Text(...)` literals rather than resource lookups. This object is
 * the single exception: it exists specifically to prevent the backend's raw `lent`/`borrowed`
 * enum values from ever leaking into UI copy. Every place that needs to show a direction MUST
 * go through [DebtCredit.directionLabel] rather than deriving text from the raw string itself
 * (e.g. `direction.replaceFirstChar { it.uppercase() }`), which would break silently the moment
 * the backend enum's casing/spelling changes.
 */
object PesaMindStrings {
    object DebtCredit {
        const val FEATURE_NAME = "Lent & Borrowed"
        const val LENT_LABEL = "Lent"
        const val BORROWED_LABEL = "Borrowed"
        const val OWED_TO_YOU_FILTER = "Owed to you"
        const val YOU_OWE_FILTER = "You owe"

        /** Maps the raw `direction` enum value to display copy. Falls back to the raw value
         * only defensively — should never happen in practice. */
        fun directionLabel(direction: String): String =
            when (direction) {
                "lent" -> LENT_LABEL
                "borrowed" -> BORROWED_LABEL
                else -> direction
            }
    }

    object SavingGoal {
        const val FEATURE_NAME = "Saving Goals"
    }
}
