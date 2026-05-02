package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.budgetDataStore by preferencesDataStore("pesamind_budgets")

object BudgetManager {
    private val MONTHLY_BUDGETS_KEY = stringPreferencesKey("cached_monthly_budgets")
    private val YEARLY_BUDGETS_KEY = stringPreferencesKey("cached_yearly_budgets")
    private val CURRENT_MONTHLY_BUDGET_KEY = stringPreferencesKey("current_monthly_budget")
    private val CURRENT_YEARLY_BUDGET_KEY = stringPreferencesKey("current_yearly_budget")
    private val MONTHLY_SYNC = longPreferencesKey("monthly_budgets_last_sync")
    private val YEARLY_SYNC = longPreferencesKey("yearly_budgets_last_sync")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Monthly Budgets ──────────────────────────────────────────
    
    /**
     * Save monthly budgets locally for offline access
     */
    suspend fun saveMonthlyBudgets(budgets: List<MonthlyBudgetResponse>) {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            val budgetsJson = Gson().toJson(budgets)
            prefs[MONTHLY_BUDGETS_KEY] = budgetsJson
            prefs[MONTHLY_SYNC] = System.currentTimeMillis()
        }
    }

    /**
     * Get all cached monthly budgets
     */
    suspend fun getMonthlyBudgets(): List<MonthlyBudgetResponse> {
        if (!isInitialized()) return emptyList()
        return try {
            val data = appContext.budgetDataStore.data.first()
            val budgetsJson = data[MONTHLY_BUDGETS_KEY] ?: return emptyList()
            
            Gson().fromJson<List<MonthlyBudgetResponse>>(
                budgetsJson,
                object : TypeToken<List<MonthlyBudgetResponse>>() {}.type
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save current month's budget
     */
    suspend fun saveCurrentMonthlyBudget(budget: MonthlyBudgetResponse) {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            val budgetJson = Gson().toJson(budget)
            prefs[CURRENT_MONTHLY_BUDGET_KEY] = budgetJson
        }
    }

    /**
     * Get current month's budget from cache
     */
    suspend fun getCurrentMonthlyBudget(): MonthlyBudgetResponse? {
        if (!isInitialized()) return null
        return try {
            val data = appContext.budgetDataStore.data.first()
            val budgetJson = data[CURRENT_MONTHLY_BUDGET_KEY] ?: return null
            
            Gson().fromJson(budgetJson, MonthlyBudgetResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get monthly budget by ID from cache
     */
    suspend fun getMonthlyBudgetById(budgetId: String): MonthlyBudgetResponse? {
        return getMonthlyBudgets().find { it.id == budgetId }
    }

    /**
     * Get monthly budget by month and year from cache
     */
    suspend fun getMonthlyBudgetByMonthYear(month: Int, year: Long): MonthlyBudgetResponse? {
        return getMonthlyBudgets().find { it.month == month && it.year == year }
    }

    /**
     * Clear all monthly budgets from cache
     */
    suspend fun clearMonthlyBudgets() {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            prefs.remove(MONTHLY_BUDGETS_KEY)
            prefs.remove(CURRENT_MONTHLY_BUDGET_KEY)
            prefs.remove(MONTHLY_SYNC)
        }
    }

    // ── Yearly Budgets ──────────────────────────────────────────
    
    /**
     * Save yearly budgets locally for offline access
     */
    suspend fun saveYearlyBudgets(budgets: List<YearlyBudgetResponse>) {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            val budgetsJson = Gson().toJson(budgets)
            prefs[YEARLY_BUDGETS_KEY] = budgetsJson
            prefs[YEARLY_SYNC] = System.currentTimeMillis()
        }
    }

    /**
     * Get all cached yearly budgets
     */
    suspend fun getYearlyBudgets(): List<YearlyBudgetResponse> {
        if (!isInitialized()) return emptyList()
        return try {
            val data = appContext.budgetDataStore.data.first()
            val budgetsJson = data[YEARLY_BUDGETS_KEY] ?: return emptyList()
            
            Gson().fromJson<List<YearlyBudgetResponse>>(
                budgetsJson,
                object : TypeToken<List<YearlyBudgetResponse>>() {}.type
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save current year's budget
     */
    suspend fun saveCurrentYearlyBudget(budget: YearlyBudgetResponse) {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            val budgetJson = Gson().toJson(budget)
            prefs[CURRENT_YEARLY_BUDGET_KEY] = budgetJson
        }
    }

    /**
     * Get current year's budget from cache
     */
    suspend fun getCurrentYearlyBudget(): YearlyBudgetResponse? {
        if (!isInitialized()) return null
        return try {
            val data = appContext.budgetDataStore.data.first()
            val budgetJson = data[CURRENT_YEARLY_BUDGET_KEY] ?: return null
            
            Gson().fromJson(budgetJson, YearlyBudgetResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get yearly budget by ID from cache
     */
    suspend fun getYearlyBudgetById(budgetId: String): YearlyBudgetResponse? {
        return getYearlyBudgets().find { it.id == budgetId }
    }

    /**
     * Get yearly budget by year from cache
     */
    suspend fun getYearlyBudgetByYear(year: Long): YearlyBudgetResponse? {
        return getYearlyBudgets().find { it.year == year }
    }

    /**
     * Clear all yearly budgets from cache
     */
    suspend fun clearYearlyBudgets() {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            prefs.remove(YEARLY_BUDGETS_KEY)
            prefs.remove(CURRENT_YEARLY_BUDGET_KEY)
            prefs.remove(YEARLY_SYNC)
        }
    }

    // ── Sync Management ──────────────────────────────────────────

    /**
     * Get last sync time for monthly budgets
     */
    suspend fun getMonthlyBudgetsLastSync(): Long {
        if (!isInitialized()) return 0L
        return appContext.budgetDataStore.data.first()[MONTHLY_SYNC] ?: 0L
    }

    /**
     * Get last sync time for yearly budgets
     */
    suspend fun getYearlyBudgetsLastSync(): Long {
        if (!isInitialized()) return 0L
        return appContext.budgetDataStore.data.first()[YEARLY_SYNC] ?: 0L
    }

    /**
     * Check if cache needs refresh (older than 5 minutes)
     */
    suspend fun isMonthlyBudgetsCacheStale(): Boolean {
        val lastSync = getMonthlyBudgetsLastSync()
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return lastSync < fiveMinutesAgo
    }

    /**
     * Check if yearly budgets cache needs refresh
     */
    suspend fun isYearlyBudgetsCacheStale(): Boolean {
        val lastSync = getYearlyBudgetsLastSync()
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return lastSync < fiveMinutesAgo
    }

    /**
     * Clear all budget data
     */
    suspend fun clearAll() {
        if (!isInitialized()) return
        appContext.budgetDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}


