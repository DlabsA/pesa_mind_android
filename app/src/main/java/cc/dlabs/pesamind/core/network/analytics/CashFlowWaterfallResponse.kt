package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class CashFlowWaterfallResponse(
    val data: WaterfallData,
    val metadata: Metadata,
    val recommendations: List<Recommendation> = emptyList(),
)

data class WaterfallData(
    @SerializedName("opening_balance")
    val openingBalance: Double,
    val income: IncomeBreakdown,
    val expenses: ExpenseBreakdown,
    @SerializedName("savings_transfers")
    val savingsTransfers: Double,
    @SerializedName("closing_balance")
    val closingBalance: Double,
)

data class IncomeBreakdown(
    val total: Double,
    val sources: List<IncomeSource> = emptyList(),
)

data class IncomeSource(
    val channel: String,
    val amount: Double,
    val percent: Double,
    @SerializedName("transaction_count")
    val transactionCount: Int,
)

data class ExpenseBreakdown(
    val total: Double,
    val categories: List<ExpenseCategory> = emptyList(),
)

data class ExpenseCategory(
    val channel: String,
    val amount: Double,
    val percent: Double,
    @SerializedName("transaction_count")
    val transactionCount: Int,
)
