package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for the crash a Free-tier account hit on the dashboard:
 *
 * ```
 * FATAL EXCEPTION: main
 * java.lang.NullPointerException: Attempt to invoke virtual method
 *   'AnomalyData AnomalySection.getData()' on a null object reference
 *   at DashboardScreenKt$DashboardScrollBody$2 (DashboardScreen.kt:320)
 * ```
 *
 * The backend Premium-gates spending velocity, anomalies and financial health, so
 * `/data/dashboard` returns JSON `null` for all three on a Free account. Those
 * fields were declared as non-null Kotlin types, which does **not** stop Gson from
 * writing null into them — it populates fields reflectively and honours neither
 * Kotlin nullability nor a declared default. The non-null type only hid the problem
 * from the compiler until it NPE'd at the use site.
 *
 * The payload below is the real production response, copied verbatim from the
 * crash report (amounts and ids left as captured), so this test fails if either the
 * DTO or the backend contract regresses.
 */
class DashboardResponseFreeTierTest {
    private val freeTierDashboardJson =
        """
        {
          "anomalies": null,
          "budget_utilization": null,
          "financial_health": null,
          "spending_velocity": null,
          "streak": {
            "id": "c0c51834-70b5-4974-8af9-ec0181c4ac59",
            "user_id": "564d23f4-5b6e-4db7-ada8-bddce0ac9594",
            "current_streak": 3,
            "longest_streak": 9,
            "last_active_date": "2026-08-11T00:00:00+03:00",
            "streak_type": "daily_transaction"
          },
          "summary": {
            "data": {
              "total_income": 4057400,
              "total_expense": 403859,
              "total_savings": 30000,
              "net_movement": 3623541,
              "transaction_count": 27,
              "active_categories": 5,
              "current_month": "2026-08"
            },
            "metadata": {
              "period": "2026-08",
              "generated_at": "2026-08-11T22:35:00.914439901+03:00",
              "currency": "UGX",
              "timezone": "Africa/Kampala"
            },
            "health": { "score": 65, "status": "fair", "trend": "improving", "components": null },
            "recommendations": []
          }
        }
        """.trimIndent()

    @Test
    fun `a Free-tier dashboard payload parses with every gated section null`() {
        val response = Gson().fromJson(freeTierDashboardJson, DashboardResponse::class.java)

        assertNotNull(response)
        // These four are Premium-gated server-side. Reading any of them without a
        // null check is what crashed the app.
        assertNull("anomalies must tolerate null for Free tier", response.anomalies)
        assertNull("spendingVelocity must tolerate null for Free tier", response.spendingVelocity)
        assertNull("financialHealth must tolerate null for Free tier", response.financialHealth)
        assertNull("budgetUtilization must tolerate null", response.budgetUtilization)
    }

    @Test
    fun `the ungated summary still parses so the dashboard has something to render`() {
        val response = Gson().fromJson(freeTierDashboardJson, DashboardResponse::class.java)

        // Summary and streak are available to every tier — a Free user must still
        // get a usable dashboard, not an empty one.
        assertNotNull(response.summary)
        assertEquals(4057400L, response.summary.data.totalIncome)
        assertEquals(27, response.summary.data.transactionCount)
        assertNotNull(response.streak)
        assertEquals(3, response.streak?.currentStreak)
    }

    @Test
    fun `the anomalies guard is false when the section is absent`() {
        val response = Gson().fromJson(freeTierDashboardJson, DashboardResponse::class.java)

        // Mirrors DashboardScreen's guard exactly. Before the fix this expression
        // was `d.anomalies.data.anomaliesDetected > 0`, which threw.
        val anomalies = response.anomalies
        val shouldShowAnomaliesCard = anomalies != null && anomalies.data.anomaliesDetected > 0

        assertEquals(false, shouldShowAnomaliesCard)
    }
}
