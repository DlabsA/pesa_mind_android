package cc.dlabs.pesamind.core.network.analytics

import  com.google.gson.annotations.SerializedName

data class AnomaliesResponse(
    val data: AnomalyData,
    val metadata: Metadata,
    val recommendations: List<Recommendation>
)

data class AnomalyData(
    @SerializedName("anomalies_detected")
    val anomaliesDetected: Int,
    val items: List<AnomalyItem>,
    @SerializedName("critical_count")
    val criticalCount: Int,
    @SerializedName("warning_count")
    val warningCount: Int
)

data class AnomalyItem(
    @SerializedName("transaction_id")
    val transactionId: String,
    val type: String,
    val category: String,
    val amount: Long,
    @SerializedName("normal_min")
    val normalMin: Long,
    @SerializedName("normal_max")
    val normalMax: Long,
    val severity: String,
    @SerializedName("sigma_multiple")
    val sigmaMultiple: Double,
    @SerializedName("detected_at")
    val detectedAt: String
)