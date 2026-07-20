package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class MonthlyTrendsResponse(
    val data: TrendsData,
    val metadata: Metadata,
    val health: Health,
    val recommendations: List<Recommendation> = emptyList(),
)

data class TrendsData(
    val months: List<MonthlyData> = emptyList(),
    val summary: TrendSummary,
) {
    var notEmpty: Boolean = months.isNotEmpty()
}

data class MonthlyData(
    val date: String,
    val income: Long,
    val expense: Long,
    val savings: Long,
    val net: Long,
    @SerializedName("transaction_count")
    val transactionCount: Int,
)

data class TrendSummary(
    @SerializedName("avg_income")
    val avgIncome: Double,
    @SerializedName("avg_expense")
    val avgExpense: Double,
    @SerializedName("avg_savings")
    val avgSavings: Double,
    @SerializedName("income_trend")
    val incomeTrend: String,
    @SerializedName("expense_trend")
    val expenseTrend: String,
    @SerializedName("savings_trend")
    val savingsTrend: String,
    @SerializedName("highest_income_month")
    val highestIncomeMonth: String,
    @SerializedName("highest_expense_month")
    val highestExpenseMonth: String,
)
