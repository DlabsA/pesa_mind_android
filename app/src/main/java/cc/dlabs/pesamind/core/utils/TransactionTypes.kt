package cc.dlabs.pesamind.core.utils

object TransactionTypes {
    const val EXPENSE = "Expense"
    const val INCOME = "Income"
    const val SAVINGS = "Savings"

    val valid = listOf(EXPENSE, INCOME, SAVINGS)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }

    fun displayName(raw: String): String {
        return when (normalizeOrNull(raw)) {
            EXPENSE -> "Expense"
            INCOME -> "Income"
            SAVINGS -> "Savings"
            else -> "Unknown"
        }
    }
}