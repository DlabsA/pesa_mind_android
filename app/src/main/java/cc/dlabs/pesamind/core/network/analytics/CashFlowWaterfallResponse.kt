package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class CashFlowWaterfallResponse(
    val data: WaterfallData,
    val metadata: Metadata,
    val recommendations: List<Recommendation>
)

data class WaterfallData(
    @SerializedName("opening_balance")
    val openingBalance: Long,
    val income: IncomeBreakdown,
    val expenses: ExpenseBreakdown,
    @SerializedName("savings_transfers")
    val savingsTransfers: Long,
    @SerializedName("closing_balance")
    val closingBalance: Long
)

data class IncomeBreakdown(
    val total: Long,
    val sources: List<IncomeSource>
)

data class IncomeSource(
    val channel: String,
    val amount: Long,
    val percent: Double,
    @SerializedName("transaction_count")
    val transactionCount: Int
)

data class ExpenseBreakdown(
    val total: Long,
    val categories: List<ExpenseCategory>
)

data class ExpenseCategory(
    val channel: String,
    val amount: Long,
    val percent: Double,
    @SerializedName("transaction_count")
    val transactionCount: Int
)