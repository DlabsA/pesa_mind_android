package cc.dlabs.pesamind.core.navigation

sealed class Routes(val route: String) {
    // Auth
    object Login : Routes("login")

    object Register : Routes("register")

    object PinUnlock : Routes("pin_unlock")

    object PatternUnlock : Routes("pattern_unlock")

    object LockSetup : Routes("lock_setup")

    object PinSetup : Routes("pin_setup")

    object PatternSetup : Routes("pattern_setup")

    // Post-signup channel onboarding (one-shot, skippable, batch-creates up to 4 channels)
    object ChannelOnboardingIntro : Routes("onboarding_intro")

    object OnboardingSimSlots : Routes("onboarding_sim_slots")

    object OnboardingCash : Routes("onboarding_cash")

    object OnboardingMoMo : Routes("onboarding_momo")

    object OnboardingAirtel : Routes("onboarding_airtel")

    object OnboardingBank : Routes("onboarding_bank")

    object OnboardingReview : Routes("onboarding_review")

    // Main (bottom nav)
    object Dashboard : Routes("dashboard")

    object Analytics : Routes("analytics")

    object Tools : Routes("tools")

    object Settings : Routes("settings")

    object Home : Routes("home")

    // Sub-screens
    object AddTransaction : Routes(
        "add_transaction?channelId={channelId}&debtCreditId={debtCreditId}&savingGoalId={savingGoalId}",
    ) {
        /** [channelId] pre-selects a channel (e.g. the "Add Transaction" pill on a channel
         * card) — omit for the plain, empty-picker entry point used elsewhere. [debtCreditId]/
         * [savingGoalId] pre-select a Purpose — at most one of these two is ever passed. The
         * "Add payment"/"Add contribution" buttons on the debt/goal detail screens no longer
         * come through here; they use `QuickPaymentDialog` in place, so these two currently
         * have no caller and are kept only as the route's deep-link surface. */
        fun createRoute(
            channelId: String? = null,
            debtCreditId: String? = null,
            savingGoalId: String? = null,
        ): String {
            val params =
                listOfNotNull(
                    channelId?.takeIf { it.isNotBlank() }?.let { "channelId=$it" },
                    debtCreditId?.takeIf { it.isNotBlank() }?.let { "debtCreditId=$it" },
                    savingGoalId?.takeIf { it.isNotBlank() }?.let { "savingGoalId=$it" },
                )
            return if (params.isEmpty()) "add_transaction" else "add_transaction?${params.joinToString("&")}"
        }
    }

    // Lent & Borrowed
    object LentBorrowed : Routes("lent_borrowed")

    object LentBorrowedDetail : Routes("lent_borrowed_detail/{debtCreditId}") {
        fun createRoute(debtCreditId: String) = "lent_borrowed_detail/$debtCreditId"
    }

    // Saving Goals
    object SavingGoals : Routes("saving_goals")

    object SavingGoalDetail : Routes("saving_goal_detail/{savingGoalId}") {
        fun createRoute(savingGoalId: String) = "saving_goal_detail/$savingGoalId"
    }
//    object Accounts : Routes("accounts")
//    object BudgetDetail : Routes("budget_detail")

    // Security in account settings
    object SecuritySettings : Routes("security_settings")

    object SetPin : Routes("set_pin")

    object SetPattern : Routes("set_pattern")

    //
    object AccountSettings : Routes("account_settings")

    object SimSlots : Routes("sim_slots")

    object ChangePassword : Routes("change_password")

    /**
     * The subscription screen — plans, prices, and payment in one place.
     *
     * Still spelled "upgrade": it has nine call sites plus the
     * `pesamind://payment/upgrade` deep link, and the string is part of that
     * contract. Checkout is no longer a separate route; payment is a sheet on
     * this screen.
     */
    object Upgrade : Routes("upgrade")

    object Channels : Routes("channels")

    object ChannelDetail : Routes("channel_detail/{channelId}") {
        fun createRoute(channelId: String) = "channel_detail/$channelId"
    }

    object TransactionList : Routes("transaction_list")

    object SetMonthlyBudget : Routes("set_monthly_budget/{month}/{year}") {
        fun createRoute(
            month: Int,
            year: Int,
        ) = "set_monthly_budget/$month/$year"
    }

    object SetYearlyBudget : Routes("set_yearly_budget")
}
