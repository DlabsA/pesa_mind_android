package cc.dlabs.pesamind.core.utils

object TransactionTypes {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val SAVINGS = "saving"

    val valid = listOf(EXPENSE, INCOME, SAVINGS)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }

    fun displayName(raw: String): String {
        return when (normalizeOrNull(raw)) {
            EXPENSE -> "Expense"
            INCOME -> "Income"
            SAVINGS -> "Saving"
            else -> "Unknown"
        }
    }
}